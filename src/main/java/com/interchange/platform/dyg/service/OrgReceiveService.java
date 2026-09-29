package com.interchange.platform.dyg.service;

import com.interchange.platform.dyg.constant.MdmConstant;
import com.interchange.platform.dyg.dao.CorpDao;
import com.interchange.platform.dyg.dao.CurrencyDao;
import com.interchange.platform.dyg.entity.BtCurrency;
import com.interchange.platform.dyg.entity.SysCorp;
import com.interchange.platform.dyg.vo.OrgVO;
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
 * 组织机构接收：{@code POST /api/receive/mdm-org-receive}。
 * 报文为 JSON 数组，字段见 {@link OrgVO}。只新增，不更新。
 *
 * <p>处理方式与 dyg-erp 的接收服务一致：<b>逐条校验、每条一个独立事务、
 * 单条失败不影响其余、最后统一回执</b>。本位币、是否已接收，以及落库对象，
 * 都在 {@link #validate} 里一次查完、组装好；{@link #save} 只负责写库。
 *
 * <p>单条事务走自注入代理 + {@code @Transactional}，见 {@link #self}。
 */
@Component
@ReceiveApi(code = MdmConstant.API_ORG, desc = "MDM 组织机构接收：写入 SYS_CORP，仅新增不更新")
public class OrgReceiveService implements ReceiveService {

    private static final Logger log = LoggerFactory.getLogger(OrgReceiveService.class);

    /**
     * 自注入代理。{@link #save} 上的 {@code @Transactional} 只有经过代理调用才生效，
     * 在本类里直接 {@code this.save(...)} 属于自调用，事务会被静默跳过。
     * {@code @Lazy} 不能省：Spring Boot 2.6+ 默认禁止循环引用，而自注入本身就是一种循环引用。
     */
    @Lazy
    @Autowired
    private OrgReceiveService self;

    @Resource
    private CorpDao corpDao;
    @Resource
    private CurrencyDao currencyDao;

    /** 逐条处理整批报文并生成回执 */
    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        // 报文一律是 JSON 数组
        if (StringUtil.isBlank(body)) {
            throw new BusinessException(400, "组织机构报文为空（请以 JSON 数组 POST）");
        }
        List<OrgVO> list;
        try {
            list = JsonUtil.jsonToObjArray(OrgVO.class, body);
        } catch (Exception e) {
            throw new BusinessException(400, "解析组织机构报文出错：" + e.getMessage());
        }
        List<Map<String, Object>> items = new ArrayList<>(list.size());
        for (OrgVO bean : list) {
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
    private Map<String, Object> handleOne(OrgVO bean) {
        Prepared prepared = validate(bean);
        String status = MdmConstant.E;
        String message = prepared.error();
        if (message.isEmpty()) {
            try {
                self.save(prepared.entity());
                status = MdmConstant.S;
                message = MdmConstant.MSG_OK;
            } catch (Exception e) {
                log.error("MDM 落库失败: type=OrgVO, mdId={}", bean.getMdId(), e);
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
     * 单条校验：校验字段、查本位币与是否已接收，并把落库对象组装好
     * （校验不通过时 {@code error} 非空，{@code entity} 不可用）。
     */
    private Prepared validate(OrgVO b) {
        StringBuilder sb = new StringBuilder();
        if (StringUtil.isBlank(b.getMdId())) {
            sb.append("主数据ID不能为空;");
        }
        if (StringUtil.isBlank(b.getOrgCode())) {
            sb.append("组织编码不能为空;");
        }
        if (StringUtil.isBlank(b.getOrgName())) {
            sb.append("组织名称不能为空;");
        }
        if (StringUtil.isBlank(b.getPkCurrtype())) {
            sb.append("本位币不能为空;");
        }

        // 本位币必须已接收
        BtCurrency currency = null;
        if (StringUtil.isNotBlank(b.getPkCurrtype())) {
            currency = currencyDao.findByCode(b.getPkCurrtype()).orElse(null);
            if (currency == null) {
                sb.append("本位币在币种表中不存在;");
            }
        }
        // 组织机构只新增不更新，已存在视为重复
        if (StringUtil.isNotBlank(b.getMdId()) && corpDao.findByMdId(b.getMdId()).isPresent()) {
            sb.append("重复接收;");
        }

        SysCorp entity = new SysCorp();
        entity.setId(StringUtil.uuid());
        entity.setMdId(b.getMdId());
        entity.setCode(b.getOrgCode());
        entity.setName(b.getOrgName());
        entity.setShortName(b.getShortname());
        entity.setNameEn(b.getDef5());
        entity.setParentMdId(StringUtil.isBlank(b.getLegalParentidIdShow())
                ? null : b.getLegalParentidIdShow());
        entity.setCurId(currency == null ? null : currency.getId());
        entity.setSocCode(b.getTaxpayercode());
        entity.setUnitAttribute(StringUtil.isTrue(b.getOrgtype2()) ? "01" : "02");
        entity.setStatus(StringUtil.isTrue(b.getEnable()) ? 1 : 0);
        entity.setGroupCode(b.getPkGroup());
        entity.setNetId(MdmConstant.NET_ID);
        // 与原系统一致：type=1、listed_company=0、rat_group=1、is_limit_quota=0
        entity.setType(1);
        entity.setListedCompany(0);
        entity.setRatGroup(1);
        entity.setIsLimitQuota(MdmConstant.N);
        entity.setCreateTime(DateUtil.now());
        entity.setCreateBy(MdmConstant.CREATE_BY);
        return new Prepared(entity, sb.toString());
    }

    /** 单条落库，在独立事务内执行（由 {@link #handleOne} 经代理调用） */
    @Transactional
    public void save(SysCorp entity) {
        corpDao.save(entity);
    }

    /**
     * 单条校验结果。
     *
     * @param entity 校验通过后可直接落库的单位对象（值已设好）
     * @param error  错误说明，空串表示校验通过
     */
    private record Prepared(SysCorp entity, String error) {
    }
}
