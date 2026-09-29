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
import com.interchange.platform.dyg.vo.PartnerBankAccVO;
import com.interchange.platform.dyg.vo.PartnerVO;
import com.interchange.platform.standard.anotation.ReceiveApi;
import com.interchange.platform.standard.exception.BusinessException;
import com.interchange.platform.standard.sys.service.receiveService.ReceiveService;
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
 * 客商接收：{@code POST /api/receive/mdm-partner-receive}。
 * 报文为 JSON 数组，字段见 {@link PartnerVO}。依赖银行网点 / 银行类别 / 币种。
 *
 * <p>处理方式与 dyg-erp 的接收服务一致：<b>逐条校验、每条一个独立事务、
 * 单条失败不影响其余、最后统一回执</b>。已有客商、以及要落库的客商对象，
 * 都在 {@link #validate} 里查完、组装好；{@link #save} 只负责写库。
 * 账号明细里的网点 / 类别 / 币种因为不参与校验，仍在落库时按账号逐条查。
 *
 * <p>单条事务走自注入代理 + {@code @Transactional}，见 {@link #self}。
 */
@Component
@ReceiveApi(code = MdmConstant.API_PARTNER,
        desc = "MDM 客商接收：写入 SYS_EXTERNAL_CORP + 客商账号子表")
public class PartnerReceiveService implements ReceiveService {

    private static final Logger log = LoggerFactory.getLogger(PartnerReceiveService.class);

    /**
     * 自注入代理。{@link #save} 上的 {@code @Transactional} 只有经过代理调用才生效，
     * 在本类里直接 {@code this.save(...)} 属于自调用，事务会被静默跳过。
     * {@code @Lazy} 不能省：Spring Boot 2.6+ 默认禁止循环引用，而自注入本身就是一种循环引用。
     */
    @Lazy
    @Autowired
    private PartnerReceiveService self;

    @Resource
    private ExternalCorpDao externalCorpDao;
    @Resource
    private BankInputDao bankInputDao;
    @Resource
    private BankTypeDao bankTypeDao;
    @Resource
    private CurrencyDao currencyDao;

    /** 逐条处理整批报文并生成回执 */
    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        // 报文一律是 JSON 数组
        if (StringUtil.isBlank(body)) {
            throw new BusinessException(400, "客商信息报文为空（请以 JSON 数组 POST）");
        }
        List<PartnerVO> list;
        try {
            list = JsonUtil.jsonToObjArray(PartnerVO.class, body);
        } catch (Exception e) {
            throw new BusinessException(400, "解析客商信息报文出错：" + e.getMessage());
        }
        List<Map<String, Object>> items = new ArrayList<>(list.size());
        for (PartnerVO bean : list) {
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
    private Map<String, Object> handleOne(PartnerVO bean) {
        Prepared prepared = validate(bean);
        String status = MdmConstant.E;
        String message = prepared.error();
        if (message.isEmpty()) {
            try {
                self.save(bean, prepared.entity());
                status = MdmConstant.S;
                message = MdmConstant.MSG_OK;
            } catch (Exception e) {
                log.error("MDM 落库失败: type=PartnerVO, mdId={}", bean.getMdId(), e);
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
     * 单条校验：校验字段、查已有客商，并把要落库的客商对象组装好
     * （校验不通过时 {@code error} 非空，{@code entity} 不可用）。
     */
    private Prepared validate(PartnerVO b) {
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

        // 已有客商：按有无决定新增还是更新
        SysExternalCorp exist = StringUtil.isBlank(b.getMdId())
                ? null : externalCorpDao.findByMdId(b.getMdId()).orElse(null);
        boolean isNew = exist == null;

        SysExternalCorp entity = isNew ? new SysExternalCorp() : exist;
        if (isNew) {
            entity.setId(StringUtil.uuid());
            entity.setMdId(b.getMdId());
            // 与原系统一致的初始值
            entity.setStatus(1);
            entity.setAuditStatus(MdmConstant.STATUS_APPROVED);
            entity.setIsNative(1);
            entity.setSourceSystem("BFS");
            entity.setSupplierId(MdmConstant.SUPPLIER_ID);
            entity.setBwType("00");
        }
        entity.setCode(b.getCode());
        entity.setName(b.getName());
        entity.setNameEn(b.getEname());
        entity.setAbbreviate(b.getShortname());
        entity.setSocCode(b.getUnifiedSocialCode());
        entity.setExternalType(convertBptype(b.getBptype()));
        return new Prepared(entity, sb.toString());
    }

    /** 单条落库，在独立事务内执行（由 {@link #handleOne} 经代理调用） */
    @Transactional
    public void save(PartnerVO bean, SysExternalCorp entity) {
        externalCorpDao.save(entity);
        replaceAcc(entity.getId(), bean.getBdBankaccbas());
    }

    /** 客商账号：先删后插；网点 / 类别 / 币种在库里查不到就留空 */
    private void replaceAcc(String corpId, List<PartnerBankAccVO> list) {
        externalCorpDao.deleteBankAcc(corpId);
        if (list == null) {
            return;
        }
        for (PartnerBankAccVO acc : list) {
            BtInputBankInfo branch = bankInputDao.findByMdId(acc.getPkBankdoc()).orElse(null);
            BtBankType type = bankTypeDao.findByMdId(acc.getPkBankdocBanktypeShow()).orElse(null);
            // 币种只取第一条（与原系统一致）
            String curId = null;
            if (acc.getBankaccsub() != null && !acc.getBankaccsub().isEmpty()) {
                String curCode = acc.getBankaccsub().get(0).getPkCurrtypeCodeShow();
                BtCurrency row = StringUtil.isBlank(curCode)
                        ? null : currencyDao.findByCode(curCode).orElse(null);
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

    /**
     * 单条校验结果。
     *
     * @param entity 校验通过后可直接落库的客商对象（值已设好）
     * @param error  错误说明，空串表示校验通过
     */
    private record Prepared(SysExternalCorp entity, String error) {
    }
}
