package com.interchange.platform.dyg.service;

import com.interchange.platform.dyg.constant.MdmConstant;
import com.interchange.platform.dyg.dao.BankInputDao;
import com.interchange.platform.dyg.dao.BankTypeDao;
import com.interchange.platform.dyg.dao.CurrencyDao;
import com.interchange.platform.dyg.dao.ExternalCorpDao;
import com.interchange.platform.dyg.entity.BtBankType;
import com.interchange.platform.dyg.entity.BtCurrency;
import com.interchange.platform.dyg.entity.BtInputBankInfo;
import com.interchange.platform.dyg.entity.SysExternalCorp;
import com.interchange.platform.dyg.entity.SysExternalCorpBankacc;
import com.interchange.platform.dyg.vo.PartnerBankAccCurVO;
import com.interchange.platform.dyg.vo.PartnerBankAccVO;
import com.interchange.platform.dyg.vo.PartnerVO;
import com.interchange.platform.standard.anotation.ReceiveApi;
import com.interchange.platform.standard.sys.service.receiveService.ReceiveService;
import com.interchange.platform.standard.utils.StringUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 客商接收：{@code POST /api/receive/mdm-partner-receive}。
 * 报文为 JSON 数组，字段见 {@link PartnerVO}。依赖银行网点 / 银行类别 / 币种。
 */
@Component
@ReceiveApi(code = MdmConstant.API_PARTNER,
        desc = "MDM 客商接收：写入 SYS_EXTERNAL_CORP + 客商账号子表")
public class PartnerReceiveService extends MdmReceiveSupport<PartnerVO> implements ReceiveService {

    @Resource
    private ExternalCorpDao externalCorpDao;
    @Resource
    private BankInputDao bankInputDao;
    @Resource
    private BankTypeDao bankTypeDao;
    @Resource
    private CurrencyDao currencyDao;

    /** 整批预加载：网点 / 银行类别 / 币种 / 已存在的客商 */
    private Map<String, BtInputBankInfo> branchMap = Map.of();
    private Map<String, BtBankType> typeMap = Map.of();
    private Map<String, BtCurrency> currencyMap = Map.of();
    private Map<String, SysExternalCorp> existMap = Map.of();

    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        return receive(body, PartnerVO.class,
                "客商信息报文为空（请以 JSON 数组 POST）", "解析客商信息报文出错：");
    }

    @Override
    protected void prepare(List<PartnerVO> list) {
        List<String> branchIds = new ArrayList<>();
        List<String> typeIds = new ArrayList<>();
        List<String> curCodes = new ArrayList<>();
        for (PartnerVO p : list) {
            if (p.getBdBankaccbas() == null) {
                continue;
            }
            for (PartnerBankAccVO acc : p.getBdBankaccbas()) {
                branchIds.add(acc.getPkBankdoc());
                typeIds.add(acc.getPkBankdocBanktypeShow());
                if (acc.getBankaccsub() != null) {
                    acc.getBankaccsub().stream()
                            .map(PartnerBankAccCurVO::getPkCurrtypeCodeShow)
                            .forEach(curCodes::add);
                }
            }
        }
        branchMap = bankInputDao.mapByMdId(collect(branchIds, id -> id));
        typeMap = bankTypeDao.mapByMdId(collect(typeIds, id -> id));
        currencyMap = currencyDao.mapByCode(collect(curCodes, code -> code));
        existMap = externalCorpDao.mapByMdId(collect(list, PartnerVO::getMdId));
    }

    @Override
    protected String mdIdOf(PartnerVO bean) {
        return bean.getMdId();
    }

    @Override
    protected String mdCodeOf(PartnerVO bean) {
        return bean.getMdCode();
    }

    @Override
    protected String mdDescriptionOf(PartnerVO bean) {
        return bean.getMdDescription();
    }

    @Override
    protected String validate(PartnerVO b) {
        StringBuilder sb = new StringBuilder();
        if (StringUtil.isBlank(b.getMdId())) {
            sb.append("主数据ID不能为空;");
        }
        if (StringUtil.isBlank(b.getCode())) {
            sb.append("客商编号不能为空;");
        }
        if (StringUtil.isBlank(b.getName())) {
            sb.append("客商名称不能为空;");
        }
        return sb.toString();
    }

    @Override
    protected void save(PartnerVO bean) {
        SysExternalCorp exist = existMap.get(bean.getMdId());
        boolean isNew = exist == null;
        SysExternalCorp entity = isNew ? new SysExternalCorp() : exist;
        if (isNew) {
            entity.setId(StringUtil.uuid());
            entity.setMdId(bean.getMdId());
            // 与原系统一致的初始值
            entity.setStatus(1);
            entity.setAuditStatus(Integer.valueOf(MdmConstant.STATUS_APPROVED));
            entity.setIsNative(1);
            entity.setSourceSystem("BFS");
            entity.setSupplierId(MdmConstant.SUPPLIER_ID);
            entity.setBwType("00");
        }
        entity.setCode(bean.getCode());
        entity.setName(bean.getName());
        entity.setNameEn(bean.getEname());
        entity.setAbbreviate(bean.getShortname());
        entity.setSocCode(bean.getUnifiedSocialCode());
        entity.setExternalType(convertBptype(bean.getBptype()));
        externalCorpDao.save(entity);

        replaceAcc(entity.getId(), bean.getBdBankaccbas());
    }

    /** 客商账号：先删后插 */
    private void replaceAcc(String corpId, List<PartnerBankAccVO> list) {
        externalCorpDao.deleteBankAcc(corpId);
        if (list == null) {
            return;
        }
        for (PartnerBankAccVO acc : list) {
            BtInputBankInfo branch = branchMap.get(acc.getPkBankdoc());
            BtBankType type = typeMap.get(acc.getPkBankdocBanktypeShow());
            // 币种只取第一条（与原系统一致）
            String curId = null;
            if (acc.getBankaccsub() != null && !acc.getBankaccsub().isEmpty()) {
                BtCurrency row = currencyMap.get(acc.getBankaccsub().get(0).getPkCurrtypeCodeShow());
                curId = row == null ? null : row.getId();
            }
            SysExternalCorpBankacc entity = new SysExternalCorpBankacc();
            entity.setId(StringUtil.uuid());
            entity.setExternalCorpId(corpId);
            entity.setExternalAcc(acc.getAccnum01());
            entity.setExternalAccName(acc.getAccname());
            entity.setBank(branch == null ? null : branch.getBankName());
            entity.setBankType(type == null ? null : type.getBankType());
            entity.setBankSourcecode(branch == null ? null : branch.getSysBankCode());
            entity.setCurId(curId);
            externalCorpDao.saveBankAcc(entity);
        }
    }

    /** 客商类型转换：SUP供应商-1 CUS客户-0 BP客商-2 */
    private static String convertBptype(String bptype) {
        if (MdmConstant.BPTYPE_SUPPLIER.equals(bptype)) {
            return "1";
        }
        if (MdmConstant.BPTYPE_CUSTOMER.equals(bptype)) {
            return "0";
        }
        if (MdmConstant.BPTYPE_BOTH.equals(bptype)) {
            return "2";
        }
        return bptype;
    }
}
