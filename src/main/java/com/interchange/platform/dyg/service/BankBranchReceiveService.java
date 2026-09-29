package com.interchange.platform.dyg.service;

import com.interchange.platform.dyg.constant.MdmConstant;
import com.interchange.platform.dyg.dao.BankInputDao;
import com.interchange.platform.dyg.dao.BankTypeDao;
import com.interchange.platform.dyg.entity.BtBankType;
import com.interchange.platform.dyg.entity.BtInputBankInfo;
import com.interchange.platform.dyg.vo.BankBranchVO;
import com.interchange.platform.standard.anotation.ReceiveApi;
import com.interchange.platform.standard.exception.BusinessException;
import com.interchange.platform.standard.sys.service.receiveService.ReceiveService;
import com.interchange.platform.standard.utils.DateUtil;
import com.interchange.platform.standard.utils.JsonUtil;
import com.interchange.platform.standard.utils.StringUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 银行网点接收：{@code POST /api/receive/mdm-bank-branch-receive}。
 * 报文为 JSON 数组，字段见 {@link BankBranchVO}。依赖已接收的银行类别。
 *
 * <p>处理方式与 dyg-erp 的接收服务一致：<b>逐条校验、每条一个独立事务、
 * 单条失败不影响其余、最后统一回执</b>。
 *
 * <p>关联主数据（银行类别、已有网点）、派生值（有效标志、城市码、人行前缀）
 * 以及落库对象，都在 {@link #validate} 里一次查完、算完、设好；
 * {@link #save} 只负责写库（含回写银行类别前缀）。
 *
 * <p>单条事务走自注入代理 + {@code @Transactional}，见 {@link #self}。
 */
@Component
@ReceiveApi(code = MdmConstant.API_BANK_BRANCH,
        desc = "MDM 银行网点接收：写入 BT_INPUT_BANK_INFO，只接收境内银行")
public class BankBranchReceiveService implements ReceiveService {

    private static final Logger log = LoggerFactory.getLogger(BankBranchReceiveService.class);

    /**
     * 自注入代理。{@link #save} 上的 {@code @Transactional} 只有经过代理调用才生效，
     * 在本类里直接 {@code this.save(...)} 属于自调用，事务会被静默跳过。
     * {@code @Lazy} 不能省：Spring Boot 2.6+ 默认禁止循环引用，而自注入本身就是一种循环引用。
     */
    @Lazy
    @Autowired
    private BankBranchReceiveService self;

    @Resource
    private BankInputDao bankInputDao;
    @Resource
    private BankTypeDao bankTypeDao;

    /** 逐条处理整批报文并生成回执 */
    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        // 报文一律是 JSON 数组
        if (StringUtil.isBlank(body)) {
            throw new BusinessException(400, "银行网点报文为空（请以 JSON 数组 POST）");
        }
        List<BankBranchVO> list;
        try {
            list = JsonUtil.jsonToObjArray(BankBranchVO.class, body);
        } catch (Exception e) {
            throw new BusinessException(400, "解析银行网点报文出错：" + e.getMessage());
        }
        // 本次成功落库的联行号（局部变量），整批处理完再清理同号的历史脏数据
        List<String> savedCodes = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>(list.size());
        for (BankBranchVO bean : list) {
            items.add(handleOne(bean, savedCodes));
        }
        cleanupDirty(savedCodes);
        // 统一回执：外层 status 恒为 S，单条成败看 responseData 每条的 status
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", MdmConstant.S);
        res.put("message", MdmConstant.MSG_RECEIVE_OK);
        res.put("responseData", items);
        return res;
    }

    /** 单条：先校验并组装好落库对象，再在独立事务里落库，最后生成回执条目 */
    private Map<String, Object> handleOne(BankBranchVO bean, List<String> savedCodes) {
        Prepared prepared = validate(bean);
        String status = MdmConstant.E;
        String message = prepared.error();
        if (message.isEmpty()) {
            try {
                self.save(prepared.entity(), prepared.type(), prepared.writeBackPrefix(), savedCodes);
                status = MdmConstant.S;
                message = MdmConstant.MSG_OK;
            } catch (Exception e) {
                log.error("MDM 落库失败: type=BankBranchVO, mdId={}", bean.getMdId(), e);
                message = MdmConstant.MSG_SAVE_FAILED + StringUtil.reason(e);
            }
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("mdId", bean.getMdId());
        item.put("mdCode", bean.getMdCode());
        item.put("mdDescription", bean.getMdDescription());
        item.put("status", status);
        item.put("message", message);
        return item;
    }

    /**
     * 单条校验：校验字段、查关联主数据，并把落库要用的值全部算好、设到 {@code entity} 上
     * （校验不通过时 {@code error} 非空，{@code entity} 不可用）。
     */
    private Prepared validate(BankBranchVO b) {
        StringBuilder sb = new StringBuilder();
        if (StringUtil.isBlank(b.getMdId())) {
            sb.append("主数据ID不能为空;");
        }
        if (StringUtil.isBlank(b.getBanktype())) {
            sb.append("银行类型不能为空;");
        }
        if (StringUtil.isBlank(b.getCombinenum())) {
            sb.append("联行号不能为空;");
        }
        if (StringUtil.isBlank(b.getName())) {
            sb.append("开户行名称不能为空;");
        }
        if (StringUtil.isBlank(b.getCategoryCode())) {
            sb.append("主数据分类不能为空;");
        } else if (!MdmConstant.CATEGORY_INSIDE.equals(b.getCategoryCode())) {
            sb.append("非境内银行数据，暂不接收;");
        }
        if (StringUtil.isBlank(b.getMdStatusCode())) {
            sb.append("主数据状态不能为空;");
        }
        if (StringUtil.isBlank(b.getShortname())) {
            sb.append("网点简称不能为空;");
        }

        // 银行类型必须已接收：人行前缀、以及回写前缀都要用它
        BtBankType type = null;
        if (StringUtil.isNotBlank(b.getBanktype())) {
            type = bankTypeDao.findByMdId(b.getBanktype()).orElse(null);
            if (type == null) {
                sb.append("银行类型未能接收，暂不接收网点信息;");
            }
        }
        // 已有网点：按有无决定新增还是更新
        BtInputBankInfo exist = StringUtil.isBlank(b.getMdId())
                ? null : bankInputDao.findByMdId(b.getMdId()).orElse(null);

        // 落库要用的值：城市码取联行号第 4~7 位；前缀优先取银行类别上的人行前缀，
        // 没有才截联行号前三位，并把截出来的值回写到银行类别上
        String code = b.getCombinenum();
        String cityCode = code != null && code.length() >= 7 ? code.substring(3, 7) : null;
        String prefix = type == null ? null : type.getBankPrefix();
        boolean writeBackPrefix = false;
        if (type != null && StringUtil.isBlank(prefix) && code != null && code.length() >= 3) {
            prefix = code.substring(0, 3);
            writeBackPrefix = true;
            type.setBankPrefix(prefix);
        }

        boolean isNew = exist == null;
        BtInputBankInfo entity = isNew ? new BtInputBankInfo() : exist;
        if (isNew) {
            entity.setId(StringUtil.uuid());
            entity.setMdId(b.getMdId());
            entity.setCreateDate(DateUtil.now());
            entity.setCreateBy(MdmConstant.CREATE_BY);
        } else {
            entity.setUpdateDate(DateUtil.now());
            entity.setUpdateBy(MdmConstant.CREATE_BY);
        }
        entity.setSysBankCode(code);
        entity.setBankName(b.getName());
        entity.setShortName(b.getShortname());
        entity.setBankPrefix(prefix);
        entity.setBankCityCode(cityCode);
        entity.setValidSign(MdmConstant.ACTIVE.equals(b.getMdStatusCode())
                ? MdmConstant.Y : MdmConstant.N);
        return new Prepared(entity, type, writeBackPrefix, sb.toString());
    }

    /** 单条落库，在独立事务内执行（由 {@link #handleOne} 经代理调用） */
    @Transactional
    public void save(BtInputBankInfo entity, BtBankType type, boolean writeBackPrefix, List<String> savedCodes) {
        bankInputDao.save(entity);
        if (writeBackPrefix) {
            // 把截出来的前缀回写到银行类别上
            bankTypeDao.save(type);
        }
        if (entity.getSysBankCode() != null) {
            savedCodes.add(entity.getSysBankCode());
        }
    }

    /** 删除同联行号、md_id 为空的历史脏数据；失败不影响本次接收结果 */
    private void cleanupDirty(List<String> savedCodes) {
        List<String> codes = savedCodes.stream().filter(StringUtil::isNotBlank).distinct().toList();
        if (codes.isEmpty()) {
            return;
        }
        try {
            bankInputDao.deleteDirtyByBankCodes(codes);
        } catch (Exception e) {
            log.warn("银行网点历史脏数据清理失败: {}", StringUtil.reason(e));
        }
    }

    /**
     * 单条校验结果。
     *
     * @param entity          校验通过后可直接落库的网点对象（值已设好）
     * @param type            银行类别，回写前缀时用（校验不通过时为 null）
     * @param writeBackPrefix 是否要把新截出来的前缀回写到银行类别
     * @param error           错误说明，空串表示校验通过
     */
    private record Prepared(BtInputBankInfo entity, BtBankType type, boolean writeBackPrefix, String error) {
    }
}
