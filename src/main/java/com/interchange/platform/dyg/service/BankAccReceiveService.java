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
 * 银行账户接收：{@code POST /api/receive/mdm-bank-acc-receive}。
 * 报文为 JSON 数组，字段见 {@link BankAccVO}。依赖银行类别 / 网点 / 组织 / 币种。
 *
 * <p>处理方式与 dyg-erp 的接收服务一致：<b>逐条校验、每条一个独立事务、
 * 单条失败不影响其余、最后统一回执</b>。
 *
 * <p>关联主数据（银行类别 / 网点 / 组织 / 已有账户）、派生值（有效标志、联网标志、
 * 开户日期、BIF 编码、省市区）以及落库对象，都在 {@link #validate} 里一次查完、算完、设好；
 * {@link #save} 只负责写库（含账户性质和账户币种，因为它们都要写）。
 *
 * <p>单条事务走自注入代理 + {@code @Transactional}，见 {@link #self}。
 *
 * <p>注意：{@link #save} 里的主表 + 账户性质 + 账户币种是多条写，
 * 必须整体在同一个事务里，所以事务边界包的是整个 save。
 */
@Component
@ReceiveApi(code = MdmConstant.API_BANK_ACC, desc = "MDM 银行账户接收")
public class BankAccReceiveService implements ReceiveService {

    private static final Logger log = LoggerFactory.getLogger(BankAccReceiveService.class);

    /**
     * 自注入代理。{@link #save} 上的 {@code @Transactional} 只有经过代理调用才生效，
     * 在本类里直接 {@code this.save(...)} 属于自调用，事务会被静默跳过。
     * {@code @Lazy} 不能省：Spring Boot 2.6+ 默认禁止循环引用，而自注入本身就是一种循环引用。
     */
    @Lazy
    @Autowired
    private BankAccReceiveService self;

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

    /** 逐条处理整批报文并生成回执 */
    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        // 报文一律是 JSON 数组
        if (StringUtil.isBlank(body)) {
            throw new BusinessException(400, "银行账户报文为空（请以 JSON 数组 POST）");
        }
        List<BankAccVO> list;
        try {
            list = JsonUtil.jsonToObjArray(BankAccVO.class, body);
        } catch (Exception e) {
            throw new BusinessException(400, "解析银行账户报文出错：" + e.getMessage());
        }
        List<Map<String, Object>> items = new ArrayList<>(list.size());
        for (BankAccVO bean : list) {
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
    private Map<String, Object> handleOne(BankAccVO bean) {
        Prepared prepared = validate(bean);
        String status = MdmConstant.E;
        String message = prepared.error();
        if (message.isEmpty()) {
            try {
                self.save(bean, prepared.entity());
                status = MdmConstant.S;
                message = MdmConstant.MSG_OK;
            } catch (Exception e) {
                log.error("MDM 落库失败: type=BankAccVO, mdId={}", bean.getMdId(), e);
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
     *
     * <p>账户性质不在这一步查，因为它可能需要新增，属于写操作，留在 {@link #save} 的事务内。
     */
    private Prepared validate(BankAccVO b) {
        StringBuilder sb = new StringBuilder();
        if (StringUtil.isBlank(b.getMdId())) {
            sb.append("主数据ID不能为空;");
        }
        if (StringUtil.isBlank(b.getAccNum())) {
            sb.append("银行账号不能为空;");
        }
        if (StringUtil.isBlank(b.getPkBanktype())) {
            sb.append("银行类别不能为空;");
        }
        if (StringUtil.isBlank(b.getPkBankdoc())) {
            sb.append("银行网点不能为空;");
        }
        if (StringUtil.isBlank(b.getFinanceorg())) {
            sb.append("组织机构不能为空;");
        }
        if (StringUtil.isBlank(b.getMdStatusCode())) {
            sb.append("主数据状态不能为空;");
        }

        // 关联主数据必须已接收，缺一个就不落这条账户
        BtBankType type = null;
        if (StringUtil.isNotBlank(b.getPkBanktype())) {
            type = bankTypeDao.findByMdId(b.getPkBanktype()).orElse(null);
            if (type == null) {
                sb.append("银行类别未能接收，暂不接收账户信息;");
            }
        }
        BtInputBankInfo branch = null;
        if (StringUtil.isNotBlank(b.getPkBankdoc())) {
            branch = bankInputDao.findByMdId(b.getPkBankdoc()).orElse(null);
            if (branch == null) {
                sb.append("银行网点未能接收，暂不接收账户信息;");
            }
        }
        SysCorp corp = null;
        if (StringUtil.isNotBlank(b.getFinanceorg())) {
            corp = corpDao.findByMdId(b.getFinanceorg()).orElse(null);
            if (corp == null) {
                sb.append("组织机构未能接收，暂不接收账户信息;");
            }
        }
        // 已有账户：按有无决定新增还是更新
        BtBankAcc exist = StringUtil.isBlank(b.getMdId())
                ? null : bankAccDao.findByMdId(b.getMdId()).orElse(null);

        // 落库要用的值：BIF 编码、省市区
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

        boolean isNew = exist == null;
        BtBankAcc entity = isNew ? new BtBankAcc() : exist;
        if (isNew) {
            entity.setId(StringUtil.uuid());
            entity.setMdId(b.getMdId());
            entity.setAccType(MdmConstant.BANK_ACC_TYPE);
            entity.setAttributeId(MdmConstant.BANK_ACC_ATTRIBUTE);
            // 与原系统一致的初始值
            entity.setElectricBill(MdmConstant.N);
            entity.setRatesFloat(MdmConstant.N);
            entity.setInterestCycle(-1);
            entity.setStatus(MdmConstant.STATUS_APPROVED);
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
        entity.setName(b.getName());
        entity.setBankAcc(b.getAccNum());
        entity.setAccName(b.getAccName());
        entity.setCorpId(corp == null ? null : corp.getId());
        entity.setBankTypeId(typeId);
        entity.setBankCode(branch == null ? null : branch.getSysBankCode());
        entity.setBankName(branch == null ? null : branch.getBankName());
        entity.setProv(prov);
        entity.setCity(city);
        entity.setBifCode(bifCode);
        entity.setRegDate(DateUtil.parseToTimestamp(b.getAccopendate()));
        entity.setIsOnline("0".equals(b.getNetqueryflag()) ? MdmConstant.N : MdmConstant.Y);
        // 有效标志与原系统一致是「反」的：ACTIVE 写 0，非 ACTIVE 写 1
        entity.setValidSign(MdmConstant.ACTIVE.equals(b.getMdStatusCode())
                ? MdmConstant.N : MdmConstant.Y);
        return new Prepared(entity, sb.toString());
    }

    /** 单条落库，在独立事务内执行（由 {@link #handleOne} 经代理调用） */
    @Transactional
    public void save(BankAccVO bean, BtBankAcc entity) {
        // 账户性质不存在要新增，是写操作，所以留在事务内
        entity.setNatureId(ensureNature(bean.getAccattribute(), bean.getAccattributeDesc()));
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

    /** 账户币种：先删后插，币种按编码逐条查 */
    private void replaceAccCur(String accId, List<BankAccCurVO> list) {
        bankAccDao.deleteAccCur(accId);
        if (list == null) {
            return;
        }
        for (BankAccCurVO cur : list) {
            String code = cur.getPkCurrtypeCodeShow();
            BtCurrency row = StringUtil.isBlank(code) ? null : currencyDao.findByCode(code).orElse(null);
            if (row == null) {
                log.warn("账户币种在 BT_CURRENCY 中不存在，跳过: {}", code);
                continue;
            }
            bankAccDao.insertAccCur(StringUtil.uuid(), accId, row.getId());
        }
    }

    /**
     * 单条校验结果。
     *
     * @param entity 校验通过后可直接落库的账户对象（值已设好）
     * @param error  错误说明，空串表示校验通过
     */
    private record Prepared(BtBankAcc entity, String error) {
    }
}
