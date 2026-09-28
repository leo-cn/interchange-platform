package com.interchange.platform.mdm;

import com.interchange.platform.mdm.task.MdmPushHandler;

import com.interchange.platform.mdm.constant.MdmConstant;

import com.interchange.platform.config.AppProps;
import com.interchange.platform.entity.InterfaceTask;
import com.interchange.platform.entity.Partner;
import com.interchange.platform.repository.InterfaceTaskRepository;
import com.interchange.platform.repository.PartnerRepository;
import com.interchange.platform.service.ReceiveApiService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MDM 接口的启动登记：注册接收接口 + 建推送示范任务。
 *
 * <p>两件事都<b>只做一次</b>（按编码查重），用户在页面上改过的内容不会被覆盖。
 *
 * <p>推送任务默认是<b>停用</b>的：主数据从哪儿来（哪个库、哪些字段）每个项目都不一样，
 * 自动生成的任务里 SQL 是空的，直接跑必然报错。请在「定时任务」页把 SQL 补齐后再启用。
 *
 * <p>启用开关 {@code app.mdm.enabled}：整套 MDM 是可选能力，关掉时不注册任何东西，
 * 不影响平台原有功能。
 */
@Component
public class MdmInitializer {

    private static final Logger log = LoggerFactory.getLogger(MdmInitializer.class);

    /** 对接方编码：dyg-erp */
    private static final String PARTNER_CODE = "DYG_ERP";

    private final AppProps appProps;
    private final ReceiveApiService receiveApiService;
    private final PartnerRepository partnerRepository;
    private final InterfaceTaskRepository taskRepository;
    private final MdmSchemaChecker schemaChecker;

    public MdmInitializer(AppProps appProps, ReceiveApiService receiveApiService,
                          PartnerRepository partnerRepository, InterfaceTaskRepository taskRepository,
                          MdmSchemaChecker schemaChecker) {
        this.appProps = appProps;
        this.receiveApiService = receiveApiService;
        this.partnerRepository = partnerRepository;
        this.taskRepository = taskRepository;
        this.schemaChecker = schemaChecker;
    }

    @PostConstruct
    public void init() {
        if (!appProps.getMdm().isEnabled()) {
            log.info("MDM 主数据接口未启用（app.mdm.enabled=false），跳过登记");
            return;
        }
        checkSchema();
        registerReceiveApis();
        Partner partner = ensurePartner();
        if (partner != null) {
            createPushTasks(partner);
        }
    }

    /**
     * 启动自检：把「缺表/缺列」提前到启动时暴露。
     *
     * <p>这里<b>不让启动失败</b>：平台只是租户，表不是它的，缺列时直接拒绝启动会把
     * 整个其它功能一起拖下水；打印成 ERROR 列表，由运维补齐即可。
     */
    private void checkSchema() {
        try {
            MdmSchemaChecker.CheckResult result = schemaChecker.check();
            if (result.skipped()) {
                log.info("MDM 落库自检已跳过：目标库不是 MySQL，请人工核对基表字段");
                return;
            }
            List<String> problems = result.problems();
            if (problems.isEmpty()) {
                log.info("MDM 落库自检通过：目标数据源 {} 的基表与字段齐全", appProps.getMdm().getDatasource());
                return;
            }
            log.error("======== MDM 落库自检未通过，{} 项缺失 ========", problems.size());
            problems.forEach(p -> log.error("  - {}", p));
            log.error("请先在 {} 库上补齐上述表/字段再使用 MDM 接口，"
                            + "否则对应接口调用时会出现列名不存在的 SQL 错误",
                    appProps.getMdm().getDatasource());
        } catch (Exception e) {
            // 自检只是体检，失败不该阻断启动
            log.warn("MDM 落库自检未能执行：{}", e.getMessage());
        }
    }

    /** 登记 5 个接收接口；默认要求令牌，与平台对外接口的默认策略一致 */
    private void registerReceiveApis() {
        receiveApiService.registerIfAbsent(MdmConstant.API_BANK_TYPE,
                "MDM 银行类别接收", "落 BT_BANK_TYPE + BIS_BIF_INIT，应最先接收", true);
        receiveApiService.registerIfAbsent(MdmConstant.API_BANK_BRANCH,
                "MDM 银行网点接收", "落 BT_INPUT_BANK_INFO，依赖银行类别，默认只收境内银行", true);
        receiveApiService.registerIfAbsent(MdmConstant.API_ORG,
                "MDM 组织机构接收", "落 SYS_CORP，仅新增不更新", true);
        receiveApiService.registerIfAbsent(MdmConstant.API_BANK_ACC,
                "MDM 银行账户接收", "落 BT_BANK_ACC + 币种子表，依赖类别/网点/组织/币种", true);
        receiveApiService.registerIfAbsent(MdmConstant.API_PARTNER,
                "MDM 客商接收", "落 SYS_EXTERNAL_CORP + 客商账号子表", true);
    }

    /**
     * 建 dyg-erp 对接方。原系统这 5 个接口是无鉴权的，
     * 平台侧默认仍按认证方式 NONE 建（与该行为一致）；如需校验，
     * 在「第三方系统」页改成 HEADER/BEARER 并填令牌即可，不用改代码。
     */
    private Partner ensurePartner() {
        String baseUrl = appProps.getMdm().getPushBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.info("未配置 app.mdm.push-base-url，跳过 MDM 推送示范任务的创建");
            return null;
        }
        Partner exists = partnerRepository.findByPartnerCode(PARTNER_CODE).orElse(null);
        if (exists != null) {
            return exists;
        }
        Partner p = new Partner();
        p.setPartnerCode(PARTNER_CODE);
        p.setPartnerName("dyg-erp 资金系统（主数据）");
        p.setBaseUrl(baseUrl.trim());
        p.setAuthType("NONE");
        p.setTimeoutMs(30000);
        p.setStatus(1);
        p.setRemark("MDM 主数据推送目标，指向 dyg-erp 的 /rest/mdm/*Receive");
        Partner saved = partnerRepository.save(p);
        log.info("已创建 MDM 推送对接方：{} -> {}", saved.getPartnerName(), saved.getBaseUrl());
        return saved;
    }

    /**
     * 建 5 个推送示范任务（默认停用，SQL 留空待补）。
     *
     * <p>目标路径与 dyg-erp 的 Jersey 路径一一对应：
     * partnerReceive / orgReceive / bankAccReceive / bankBranchReceive / bankTypeReceive。
     */
    private void createPushTasks(Partner partner) {
        create("MDM-PARTNER-PUSH", "推送：客商主数据", "/partnerReceive", partner);
        create("MDM-ORG-PUSH", "推送：组织机构主数据", "/orgReceive", partner);
        create("MDM-BANK-ACC-PUSH", "推送：银行账户主数据", "/bankAccReceive", partner);
        create("MDM-BANK-BRANCH-PUSH", "推送：银行网点主数据", "/bankBranchReceive", partner);
        create("MDM-BANK-TYPE-PUSH", "推送：银行类别主数据", "/bankTypeReceive", partner);
    }

    private void create(String taskCode, String taskName, String targetPath, Partner partner) {
        if (taskRepository.findByTaskCode(taskCode).isPresent()) {
            return;
        }
        InterfaceTask task = new InterfaceTask();
        task.setTaskCode(taskCode);
        task.setTaskName(taskName);
        task.setPartnerId(partner.getId());
        task.setSourceType("SQL");
        task.setDatasourceKey(appProps.getMdm().getDatasource());
        task.setTargetPath(targetPath);
        task.setHttpMethod("POST");
        task.setContentType("application/json;charset=UTF-8");
        task.setPushMode("BATCH");
        task.setHandlerBean(MdmPushHandler.CODE);
        task.setCronExpression("0 0/30 * * * ?");
        task.setTimeoutMs(30000);
        task.setEnabled(false);
        task.setRemark("MDM 主数据推送（默认停用）：先在「数据源」里把 SQL 补齐，"
                + "字段别名须用小驼峰对齐 MDM 报文，确认无误后再启用");
        taskRepository.save(task);
        log.info("已创建 MDM 推送任务：{}（默认停用，需补全取数 SQL）-> {}", taskCode, targetPath);
    }
}
