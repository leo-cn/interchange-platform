package com.interchange.platform.dyg.service;

import com.interchange.platform.dyg.constant.MdmConstant;
import com.interchange.platform.dyg.dao.AccNatureDao;
import com.interchange.platform.dyg.dao.BankAccDao;
import com.interchange.platform.dyg.dao.BankInputDao;
import com.interchange.platform.dyg.dao.BankTypeDao;
import com.interchange.platform.dyg.dao.CorpDao;
import com.interchange.platform.dyg.dao.CurrencyDao;
import com.interchange.platform.dyg.dao.RegionDao;
import com.interchange.platform.dyg.entity.BtAccNature;
import com.interchange.platform.dyg.entity.BtBankAcc;
import com.interchange.platform.dyg.entity.BtBankType;
import com.interchange.platform.dyg.entity.BtCurrency;
import com.interchange.platform.dyg.entity.BtInputBankInfo;
import com.interchange.platform.dyg.entity.SysCorp;
import com.interchange.platform.dyg.entity.SysRegion;
import com.interchange.platform.dyg.vo.BankAccCurVO;
import com.interchange.platform.dyg.vo.BankAccVO;
import com.interchange.platform.standard.anotation.ReceiveApi;
import com.interchange.platform.standard.sys.service.receiveService.ReceiveService;
import com.interchange.platform.standard.utils.DateUtil;
import com.interchange.platform.standard.utils.StringUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 银行账户接收：{@code POST /api/receive/mdm-bank-acc-receive}。
 * 报文为 JSON 数组，字段见 {@link BankAccVO}。依赖银行类别 / 网点 / 组织 / 币种。
 */
@Component
@ReceiveApi(code = MdmConstant.API_BANK_ACC, desc = "MDM 银行账户接收")
public class BankAccReceiveService extends MdmReceiveSupport<BankAccVO> implements ReceiveService {

    @Resource
    private BankAccDao bankAccDao;
    @Resource
    private AccNatureDao accNatureDao;
    @Resource
    private BankTypeDao bankTypeDao;
    @Resource
    private BankInputDao bankInputDao;
    @Resource
    private CorpDao corpDao;
    @Resource
    private CurrencyDao currencyDao;
    @Resource
    private RegionDao regionDao;

    /** 整批预加载：银行类别 / 网点 / 组织 / 币种 / 已存在的账户 */
    private Map<String, BtBankType> typeMap = Map.of();
    private Map<String, BtInputBankInfo> branchMap = Map.of();
    private Map<String, SysCorp> corpMap = Map.of();
    private Map<String, BtCurrency> currencyMap = Map.of();
    private Map<String, BtBankAcc> existMap = Map.of();

    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        return receive(body, BankAccVO.class,
                "银行账户报文为空（请以 JSON 数组 POST）", "解析银行账户报文出错：");
    }

    @Override
    protected void prepare(List<BankAccVO> list) {
        typeMap = bankTypeDao.mapByMdId(collect(list, BankAccVO::getPkBanktype));
        branchMap = bankInputDao.mapByMdId(collect(list, BankAccVO::getPkBankdoc));
        corpMap = corpDao.mapByMdId(collect(list, BankAccVO::getFinanceorg));
        existMap = bankAccDao.mapByMdId(collect(list, BankAccVO::getMdId));
        currencyMap = currencyDao.mapByCode(list.stream()
                .filter(b -> b.getBdBankaccsub() != null)
                .flatMap(b -> b.getBdBankaccsub().stream())
                .map(BankAccCurVO::getPkCurrtypeCodeShow)
                .toList());
    }

    @Override
    protected String mdIdOf(BankAccVO bean) {
        return bean.getMdId();
    }

    @Override
    protected String mdCodeOf(BankAccVO bean) {
        return bean.getMdCode();
    }

    @Override
    protected String mdDescriptionOf(BankAccVO bean) {
        return bean.getMdDescription();
    }

    @Override
    protected String validate(BankAccVO b) {
        StringBuilder sb = new StringBuilder();
        if (StringUtil.isBlank(b.getMdId())) {
            sb.append("主数据ID不能为空;");
        }
        if (StringUtil.isBlank(b.getAccNum())) {
            sb.append("银行账号不能为空;");
        }
        if (StringUtil.isBlank(b.getPkBanktype())) {
            sb.append("银行类别不能为空;");
        } else if (!typeMap.containsKey(b.getPkBanktype())) {
            sb.append("银行类别未能接收，暂不接收账户信息;");
        }
        if (StringUtil.isBlank(b.getPkBankdoc())) {
            sb.append("银行网点不能为空;");
        } else if (!branchMap.containsKey(b.getPkBankdoc())) {
            sb.append("银行网点未能接收，暂不接收账户信息;");
        }
        if (StringUtil.isBlank(b.getFinanceorg())) {
            sb.append("组织机构不能为空;");
        } else if (!corpMap.containsKey(b.getFinanceorg())) {
            sb.append("组织机构未能接收，暂不接收账户信息;");
        }
        if (StringUtil.isBlank(b.getMdStatusCode())) {
            sb.append("主数据状态不能为空;");
        }
        return sb.toString();
    }

    @Override
    protected void save(BankAccVO bean) {
        BtBankType type = typeMap.get(bean.getPkBanktype());
        BtInputBankInfo branch = branchMap.get(bean.getPkBankdoc());
        SysCorp corp = corpMap.get(bean.getFinanceorg());
        BtBankAcc exist = existMap.get(bean.getMdId());

        // 有效标志与原系统一致是「反」的：ACTIVE 写 0，非 ACTIVE 写 1
        String validSign = MdmConstant.ACTIVE.equals(bean.getMdStatusCode())
                ? MdmConstant.N : MdmConstant.Y;
        String isOnline = "0".equals(bean.getNetqueryflag()) ? MdmConstant.N : MdmConstant.Y;
        Timestamp openDate = DateUtil.parseToTimestamp(bean.getAccopendate());

        String typeId = type == null ? null : type.getId();
        String bifCode = typeId == null ? null : bankTypeDao.findBifIdByBankTypeId(typeId);

        String prov = null;
        String city = null;
        String cityCode = branch == null ? null : branch.getBankCityCode();
        if (!StringUtil.isBlank(cityCode)) {
            SysRegion region = regionDao.findByBankInputCity(cityCode).orElse(null);
            if (region != null) {
                prov = region.getParentId();
                city = region.getId();
            }
        }
        String natureId = ensureNature(bean.getAccattribute(), bean.getAccattributeDesc());

        boolean isNew = exist == null;
        BtBankAcc entity = isNew ? new BtBankAcc() : exist;
        if (isNew) {
            entity.setId(StringUtil.uuid());
            entity.setMdId(bean.getMdId());
            entity.setAccType(MdmConstant.BANK_ACC_TYPE);
            entity.setAttributeId(MdmConstant.BANK_ACC_ATTRIBUTE);
            // 与原系统一致的初始值
            entity.setElectricBill(MdmConstant.N);
            entity.setRatesFloat(MdmConstant.N);
            entity.setInterestCycle(-1);
            entity.setStatus(Integer.valueOf(MdmConstant.STATUS_APPROVED));
            entity.setIsCapitalPool(MdmConstant.N);
            entity.setIsDomesticBank(MdmConstant.Y);
            entity.setIsLimitQuota(MdmConstant.N);
            entity.setIsOnlineHandle(MdmConstant.N);
            entity.setIsOffshoreAccount(MdmConstant.N);
            entity.setIsRpaEscrow(MdmConstant.N);
            entity.setBasicAccountSign(MdmConstant.N);
            entity.setCreateDate(DateUtil.now());
            entity.setCreateBy(MdmConstant.CREATE_BY);
        } else {
            entity.setUpdateDate(DateUtil.now());
            entity.setUpdateBy(MdmConstant.CREATE_BY);
        }
        entity.setName(bean.getName());
        entity.setBankAcc(bean.getAccNum());
        entity.setAccName(bean.getAccName());
        entity.setCorpId(corp == null ? null : corp.getId());
        entity.setNatureId(natureId);
        entity.setBankTypeId(typeId);
        entity.setBankCode(branch == null ? null : branch.getSysBankCode());
        entity.setBankName(branch == null ? null : branch.getBankName());
        entity.setProv(prov);
        entity.setCity(city);
        entity.setBifCode(bifCode);
        entity.setRegDate(openDate);
        entity.setIsOnline(isOnline);
        entity.setValidSign(validSign);
        bankAccDao.save(entity);

        replaceAccCur(entity.getId(), bean.getBdBankaccsub());
    }

    /** 账户性质：不存在则按报文自动新增 */
    private String ensureNature(String code, String name) {
        if (StringUtil.isBlank(code)) {
            return null;
        }
        BtAccNature exist = accNatureDao.findByCode(code).orElse(null);
        if (exist != null) {
            return exist.getId();
        }
        BtAccNature entity = new BtAccNature();
        entity.setId(StringUtil.uuid());
        entity.setNatureCode(code);
        entity.setNatureName(StringUtil.isBlank(name) ? code : name);
        entity.setValidSign(MdmConstant.Y);
        entity.setCreateDate(DateUtil.now());
        entity.setCreateBy(MdmConstant.CREATE_BY);
        accNatureDao.save(entity);
        return entity.getId();
    }

    /** 账户币种：先删后插 */
    private void replaceAccCur(String accId, List<BankAccCurVO> list) {
        bankAccDao.deleteAccCur(accId);
        if (list == null) {
            return;
        }
        for (BankAccCurVO cur : list) {
            BtCurrency row = currencyMap.get(cur.getPkCurrtypeCodeShow());
            if (row == null) {
                log.warn("账户币种在 BT_CURRENCY 中不存在，跳过: {}", cur.getPkCurrtypeCodeShow());
                continue;
            }
            bankAccDao.insertAccCur(StringUtil.uuid(), accId, row.getId());
        }
    }
}
