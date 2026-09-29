package com.interchange.platform.dyg.service;

import com.interchange.platform.dyg.constant.MdmConstant;
import com.interchange.platform.dyg.dao.BankInputDao;
import com.interchange.platform.dyg.dao.BankTypeDao;
import com.interchange.platform.dyg.entity.BtBankType;
import com.interchange.platform.dyg.entity.BtInputBankInfo;
import com.interchange.platform.dyg.vo.BankBranchVO;
import com.interchange.platform.standard.anotation.ReceiveApi;
import com.interchange.platform.standard.sys.service.receiveService.ReceiveService;
import com.interchange.platform.standard.utils.DateUtil;
import com.interchange.platform.standard.utils.StringUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 银行网点接收：{@code POST /api/receive/mdm-bank-branch-receive}。
 * 报文为 JSON 数组，字段见 {@link BankBranchVO}。依赖已接收的银行类别。
 */
@Component
@ReceiveApi(code = MdmConstant.API_BANK_BRANCH,
        desc = "MDM 银行网点接收：写入 BT_INPUT_BANK_INFO，只接收境内银行")
public class BankBranchReceiveService extends MdmReceiveSupport<BankBranchVO>
        implements ReceiveService {

    @Resource
    private BankInputDao bankInputDao;
    @Resource
    private BankTypeDao bankTypeDao;

    /** 整批预加载：银行类别 + 已存在的网点 */
    private Map<String, BtBankType> typeMap = Map.of();
    private Map<String, BtInputBankInfo> existMap = Map.of();
    /** 本次成功落库的联行号，收尾时清理同号的历史脏数据 */
    private final List<String> savedCodes = new ArrayList<>();

    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        savedCodes.clear();
        Map<String, Object> result = receive(body, BankBranchVO.class,
                "银行网点报文为空（请以 JSON 数组 POST）", "解析银行网点报文出错：");
        cleanupDirty();
        return result;
    }

    @Override
    protected void prepare(List<BankBranchVO> list) {
        typeMap = bankTypeDao.mapByMdId(collect(list, BankBranchVO::getBanktype));
        existMap = bankInputDao.mapByMdId(collect(list, BankBranchVO::getMdId));
    }

    @Override
    protected String mdIdOf(BankBranchVO bean) {
        return bean.getMdId();
    }

    @Override
    protected String mdCodeOf(BankBranchVO bean) {
        return bean.getMdCode();
    }

    @Override
    protected String mdDescriptionOf(BankBranchVO bean) {
        return bean.getMdDescription();
    }

    @Override
    protected String validate(BankBranchVO b) {
        StringBuilder sb = new StringBuilder();
        if (StringUtil.isBlank(b.getMdId())) {
            sb.append("主数据ID不能为空;");
        }
        if (StringUtil.isBlank(b.getBanktype())) {
            sb.append("银行类型不能为空;");
        } else if (!typeMap.containsKey(b.getBanktype())) {
            sb.append("银行类型未能接收，暂不接收网点信息;");
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
        return sb.toString();
    }

    @Override
    protected void save(BankBranchVO bean) {
        BtBankType type = typeMap.get(bean.getBanktype());
        BtInputBankInfo exist = existMap.get(bean.getMdId());
        String code = bean.getCombinenum();
        String validSign = MdmConstant.ACTIVE.equals(bean.getMdStatusCode())
                ? MdmConstant.Y : MdmConstant.N;
        // 城市码取联行号第 4~7 位，不足 7 位则不写
        String cityCode = code != null && code.length() >= 7 ? code.substring(3, 7) : null;
        // 前缀优先取银行类别上的人行前缀，没有才截联行号前三位，并回写银行类别
        String prefix = type == null ? null : type.getBankPrefix();
        boolean fromCode = false;
        if (StringUtil.isBlank(prefix) && code != null && code.length() >= 3) {
            prefix = code.substring(0, 3);
            fromCode = true;
        }
        boolean isNew = exist == null;
        BtInputBankInfo entity = isNew ? new BtInputBankInfo() : exist;
        if (isNew) {
            entity.setId(StringUtil.uuid());
            entity.setMdId(bean.getMdId());
            entity.setCreateDate(DateUtil.now());
            entity.setCreateBy(MdmConstant.CREATE_BY);
        } else {
            entity.setUpdateDate(DateUtil.now());
            entity.setUpdateBy(MdmConstant.CREATE_BY);
        }
        entity.setSysBankCode(code);
        entity.setBankName(bean.getName());
        entity.setShortName(bean.getShortname());
        entity.setBankPrefix(prefix);
        entity.setBankCityCode(cityCode);
        entity.setValidSign(validSign);
        bankInputDao.save(entity);

        if (fromCode && type != null) {
            type.setBankPrefix(prefix);
            bankTypeDao.save(type);
        }
        if (code != null) {
            savedCodes.add(code);
        }
    }

    /** 删除同联行号、md_id 为空的历史脏数据；失败不影响本次接收结果 */
    private void cleanupDirty() {
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
}
