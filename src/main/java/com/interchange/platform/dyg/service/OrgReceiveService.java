package com.interchange.platform.dyg.service;

import com.interchange.platform.dyg.constant.MdmConstant;
import com.interchange.platform.dyg.dao.CorpDao;
import com.interchange.platform.dyg.dao.CurrencyDao;
import com.interchange.platform.dyg.entity.BtCurrency;
import com.interchange.platform.dyg.entity.SysCorp;
import com.interchange.platform.dyg.vo.OrgVO;
import com.interchange.platform.standard.anotation.ReceiveApi;
import com.interchange.platform.standard.sys.service.receiveService.ReceiveService;
import com.interchange.platform.standard.utils.DateUtil;
import com.interchange.platform.standard.utils.StringUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 组织机构接收：{@code POST /api/receive/mdm-org-receive}。
 * 报文为 JSON 数组，字段见 {@link OrgVO}。只新增，不更新。
 */
@Component
@ReceiveApi(code = MdmConstant.API_ORG, desc = "MDM 组织机构接收：写入 SYS_CORP，仅新增不更新")
public class OrgReceiveService extends MdmReceiveSupport<OrgVO> implements ReceiveService {

    @Resource
    private CorpDao corpDao;
    @Resource
    private CurrencyDao currencyDao;

    /** 整批预加载：本位币映射 + 已存在的 mdId（用于判重） */
    private Map<String, BtCurrency> currencyMap = Map.of();
    private Map<String, SysCorp> existMap = Map.of();

    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        return receive(body, OrgVO.class,
                "组织机构报文为空（请以 JSON 数组 POST）", "解析组织机构报文出错：");
    }

    @Override
    protected void prepare(List<OrgVO> list) {
        currencyMap = currencyDao.mapByCode(collect(list, OrgVO::getPkCurrtype));
        existMap = corpDao.mapByMdId(collect(list, OrgVO::getMdId));
    }

    @Override
    protected String mdIdOf(OrgVO bean) {
        return bean.getMdId();
    }

    @Override
    protected String mdCodeOf(OrgVO bean) {
        return bean.getMdCode();
    }

    @Override
    protected String mdDescriptionOf(OrgVO bean) {
        return bean.getMdDescription();
    }

    @Override
    protected String validate(OrgVO b) {
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
        } else if (!currencyMap.containsKey(b.getPkCurrtype())) {
            sb.append("本位币在币种表中不存在;");
        }
        // 组织机构只新增不更新，已存在视为重复
        if (existMap.containsKey(b.getMdId())) {
            sb.append("重复接收;");
        }
        return sb.toString();
    }

    @Override
    protected void save(OrgVO bean) {
        BtCurrency currency = currencyMap.get(bean.getPkCurrtype());
        SysCorp entity = new SysCorp();
        entity.setId(StringUtil.uuid());
        entity.setMdId(bean.getMdId());
        entity.setCode(bean.getOrgCode());
        entity.setName(bean.getOrgName());
        entity.setShortName(bean.getShortname());
        entity.setNameEn(bean.getDef5());
        entity.setParentMdId(StringUtil.isBlank(bean.getLegalParentidIdShow())
                ? null : bean.getLegalParentidIdShow());
        entity.setCurId(currency == null ? null : currency.getId());
        entity.setSocCode(bean.getTaxpayercode());
        entity.setUnitAttribute(StringUtil.isTrue(bean.getOrgtype2()) ? "01" : "02");
        entity.setStatus(StringUtil.isTrue(bean.getEnable()) ? 1 : 0);
        entity.setGroupCode(bean.getPkGroup());
        entity.setNetId(MdmConstant.NET_ID);
        // 与原系统一致：type=1、listed_company=0、rat_group=1、is_limit_quota=0
        entity.setType(1);
        entity.setListedCompany(0);
        entity.setRatGroup(1);
        entity.setIsLimitQuota(MdmConstant.N);
        entity.setCreateTime(DateUtil.now());
        entity.setCreateBy(MdmConstant.CREATE_BY);
        corpDao.save(entity);
    }
}
