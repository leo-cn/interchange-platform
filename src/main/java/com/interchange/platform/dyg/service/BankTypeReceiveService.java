package com.interchange.platform.dyg.service;

import com.interchange.platform.dyg.constant.MdmConstant;
import com.interchange.platform.dyg.dao.BankTypeDao;
import com.interchange.platform.dyg.entity.BisBifInit;
import com.interchange.platform.dyg.entity.BtBankType;
import com.interchange.platform.dyg.vo.BankTypeVO;
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
 * 银行类别接收：{@code POST /api/receive/mdm-bank-type-receive}。
 * 报文为 JSON 数组，字段见 {@link BankTypeVO}。必须最先接收，网点按 mdId 关联它取人行前缀。
 *
 * <p>处理方式与 dyg-erp 的接收服务一致：<b>逐条校验、每条一个独立事务、
 * 单条失败不影响其余、最后统一回执</b>。已有类别、以及要落库的类别/接口初始化对象，
 * 都在 {@link #validate} 里查完、组装好；{@link #save} 只负责写库。
 *
 * <p>单条事务走自注入代理 + {@code @Transactional}，见 {@link #self}。
 */
@Component
@ReceiveApi(code = MdmConstant.API_BANK_TYPE,
        desc = "MDM 银行类别接收：写入 BT_BANK_TYPE，新增时级联 BIS_BIF_INIT")
public class BankTypeReceiveService implements ReceiveService {

    private static final Logger log = LoggerFactory.getLogger(BankTypeReceiveService.class);

    /**
     * 自注入代理。{@link #save} 上的 {@code @Transactional} 只有经过代理调用才生效，
     * 在本类里直接 {@code this.save(...)} 属于自调用，事务会被静默跳过。
     * {@code @Lazy} 不能省：Spring Boot 2.6+ 默认禁止循环引用，而自注入本身就是一种循环引用。
     */
    @Lazy
    @Autowired
    private BankTypeReceiveService self;

    @Resource
    private BankTypeDao bankTypeDao;

    /** 逐条处理整批报文并生成回执 */
    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        // 报文一律是 JSON 数组
        if (StringUtil.isBlank(body)) {
            throw new BusinessException(400, "银行类别报文为空（请以 JSON 数组 POST）");
        }
        List<BankTypeVO> list;
        try {
            list = JsonUtil.jsonToObjArray(BankTypeVO.class, body);
        } catch (Exception e) {
            throw new BusinessException(400, "解析银行类别报文出错：" + e.getMessage());
        }
        List<Map<String, Object>> items = new ArrayList<>(list.size());
        for (BankTypeVO bean : list) {
            items.add(handleOne(bean));
        }
        // 统一回执：外层 status 恒为 S，单条成败看 responseData 每条的 status
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", MdmConstant.S);
        res.put("message", MdmConstant.MSG_RECEIVE_OK);
        res.put("responseData", items);
        return res;
    }

    /** 单条：先校验并组装好落库对象，再在独立事务里落库，最后生成回执条目 */
    private Map<String, Object> handleOne(BankTypeVO bean) {
        Prepared prepared = validate(bean);
        String status = MdmConstant.E;
        String message = prepared.error();
        if (message.isEmpty()) {
            try {
                self.save(prepared.entity(), prepared.bifInit());
                status = MdmConstant.S;
                message = MdmConstant.MSG_OK;
            } catch (Exception e) {
                log.error("MDM 落库失败: type=BankTypeVO, mdId={}", bean.getMdId(), e);
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
     * 单条校验：校验字段、查已有类别，并把要落库的类别对象（新增时含级联的接口初始化对象）
     * 组装好（校验不通过时 {@code error} 非空，{@code entity} 不可用）。
     */
    private Prepared validate(BankTypeVO b) {
        StringBuilder sb = new StringBuilder();
        if (StringUtil.isBlank(b.getMdId())) {
            sb.append("主数据ID不能为空;");
        }
        if (StringUtil.isBlank(b.getCode())) {
            sb.append("银行类别编码不能为空;");
        }
        if (StringUtil.isBlank(b.getName())) {
            sb.append("银行类别名称不能为空;");
        }
        if (StringUtil.isBlank(b.getMdStatusCode())) {
            sb.append("主数据状态不能为空;");
        }

        // 已有类别：按有无决定新增还是更新
        BtBankType exist = StringUtil.isBlank(b.getMdId())
                ? null : bankTypeDao.findByMdId(b.getMdId()).orElse(null);
        boolean isNew = exist == null;

        BtBankType entity = isNew ? new BtBankType() : exist;
        if (isNew) {
            entity.setId(StringUtil.uuid());
            entity.setMdId(b.getMdId());
            entity.setIsSystem(MdmConstant.N);
            entity.setCreateDate(DateUtil.now());
            entity.setCreateBy(MdmConstant.CREATE_BY);
        } else {
            entity.setUpdateDate(DateUtil.now());
            entity.setUpdateBy(MdmConstant.CREATE_BY);
        }
        entity.setBankType(b.getCode());
        entity.setTypeName(b.getName());
        entity.setBankPrefix(b.getCombinecode());
        entity.setValidSign(MdmConstant.ACTIVE.equals(b.getMdStatusCode())
                ? MdmConstant.Y : MdmConstant.N);

        // 新增银行类别时级联插一条接口初始化记录，这里一并建好
        BisBifInit bif = null;
        if (isNew) {
            bif = new BisBifInit();
            bif.setId(StringUtil.uuid());
            bif.setBifCode(b.getCode());
            bif.setName(b.getName());
            bif.setBankTypeId(entity.getId());
            bif.setValidSign(MdmConstant.Y);
            bif.setIsSystem(MdmConstant.N);
            bif.setCreateDate(DateUtil.now());
            bif.setCreateBy(MdmConstant.CREATE_BY);
        }
        return new Prepared(entity, bif, sb.toString());
    }

    /** 单条落库，在独立事务内执行（由 {@link #handleOne} 经代理调用） */
    @Transactional
    public void save(BtBankType entity, BisBifInit bifInit) {
        bankTypeDao.save(entity);
        if (bifInit != null) {
            bankTypeDao.saveBifInit(bifInit);
        }
    }

    /**
     * 单条校验结果。
     *
     * @param entity  校验通过后可直接落库的银行类别对象（值已设好）
     * @param bifInit 新增时级联要写的接口初始化对象，更新时为 null
     * @param error   错误说明，空串表示校验通过
     */
    private record Prepared(BtBankType entity, BisBifInit bifInit, String error) {
    }
}
