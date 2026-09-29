package com.interchange.platform.standard.init;

import jakarta.annotation.Resource;
import com.interchange.platform.standard.utils.Crypto;
import com.interchange.platform.standard.utils.Utils;
import com.interchange.platform.standard.sys.entity.InterfaceTask;
import com.interchange.platform.standard.sys.entity.Partner;
import com.interchange.platform.standard.sys.entity.SysUserExt;
import com.interchange.platform.standard.sys.dao.InterfaceTaskDao;
import com.interchange.platform.standard.sys.dao.PartnerDao;
import com.interchange.platform.standard.sys.dao.ReceiveLogDao;
import com.interchange.platform.standard.sys.dao.SysUserExtDao;
import com.interchange.platform.standard.sys.service.BizUserService;
import com.interchange.platform.standard.sys.service.log.LogStore;
import com.interchange.platform.standard.sys.service.receiveService.ReceiveApiService;
import com.interchange.platform.standard.sys.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 首次启动初始化：
 * <ol>
 *   <li>把业务系统的 admin 账号映射为平台管理员（平台不建用户表，只补授权记录）；</li>
 *   <li>创建“模拟第三方”对接方与两个演示任务，方便开箱联调；</li>
 *   <li>把所有启用中的任务重新注册到 Quartz。</li>
 * </ol>
 * 已有数据时不会重复初始化。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /** 匹配演示对接方里写死的本机地址端口，便于按实际启动端口自动纠正 */
    private static final Pattern LOCAL_PORT = Pattern.compile("^(https?://)(127\\.0\\.0\\.1|localhost):(\\d+)");

    @Resource
    private SysUserExtDao userExtDao;
    @Resource
    private BizUserService directory;
    @Resource
    private PartnerDao partnerDao;
    @Resource
    private InterfaceTaskDao taskDao;
    @Resource
    private TaskService taskService;
    @Resource
    private LogStore logStore;
    @Resource
    private ReceiveApiService receiveApiService;
    @Resource
    private ReceiveLogDao receiveLogDao;
    /** 本次实际监听端口：演示对接方要回调平台自身的 /api/mock/thirdparty，端口必须与之一致 */
    @Value("${server.port:18080}")
    private int serverPort;

    @Override
    public void run(ApplicationArguments args) {
        // 日志表收敛：每个任务/接口只留最新一条。历史攒了很多或刚从全量模式切过来时，
        // 这一步会把多余的行清掉；失败也不影响启动。
        logStore.pruneToLatest();
        initReceiveApis();
        initPlatformAdmin();
        Partner demoPartner = initDemoPartner();
        initDemoTasks(demoPartner);
        taskService.reloadAllSchedules();
    }

    /**
     * 接收接口清单的初始化：
     */
    private void initReceiveApis() {
        try {
            receiveApiService.registerIfAbsent("ping", "心跳探测", "内置样例接口：返回 pong 与服务端时间");
            receiveApiService.registerIfAbsent("order-receive", "订单接收（样例）",
                    "内置样例接口：回执 ack 并回显 bizNo，供联调验证使用");

            int imported = 0;
            for (String code : receiveLogDao.findDistinctApiCodes()) {
                if (receiveApiService.registerIfAbsent(code, code,
                        "由历史调用记录自动补登记，请补充接口名称与说明", true)) {
                    imported++;
                }
            }
            if (imported > 0) {
                log.warn("已从历史接收日志补登记 {} 个接口到「接收接口」清单，"
                        + "建议到页面核对名称后再对外提供服务", imported);
            }
        } catch (Exception e) {
            log.warn("初始化接收接口清单失败，可登录后在「接收接口」页手工登记: {}",
                    e.getClass().getSimpleName());
        }
    }

    /**
     * 把业务系统的 admin 账号映射成平台管理员。
     */
    private void initPlatformAdmin() {
        try {
            if (directory.findByLoginName("admin").isEmpty()) {
                log.warn("业务系统用户表里没有 admin 账号，跳过平台管理员初始化；"
                        + "请检查 app.user.datasource / app.user.table 配置，或在业务系统里创建 admin 后重启平台");
                return;
            }
            if (userExtDao.findByExtId(adminExtId()).isPresent()) {
                return;
            }
            SysUserExt ext = new SysUserExt();
            ext.setExtId(adminExtId());
            ext.setRole("ADMIN");
            ext.setStatus(1);
            ext.setPlatformPassword(Crypto.hashPassword("Admin@123"));
            ext.setApiToken("PO-ADMIN-DEMO-TOKEN");
            ext.setCreateTime(LocalDateTime.now());
            userExtDao.save(ext);
            log.info("已把业务系统账号 admin 映射为平台管理员，登录口令：Admin@123（请登录后立即修改）");
        } catch (Exception e) {
            log.warn("初始化平台管理员失败，可稍后在业务系统可用时重启平台重试: {}",
                    e.getClass().getSimpleName());
        }
    }

    /** 业务系统 admin 的主键，查一次缓存住，避免对业务库多打一次 */
    private String adminExtId() {
        return directory.findByLoginName("admin")
                .map(BizUserService.BizUser::id)
                .orElseThrow(() -> new IllegalStateException("业务系统缺少 admin 账号"));
    }

    private Partner initDemoPartner() {
        Partner exists = partnerDao.findByPartnerCode("DEMO_THIRD").orElse(null);
        if (exists != null) {
            healDemoPartnerUrl(exists);
            return exists;
        }
        Partner partner = new Partner();
        partner.setPartnerCode("DEMO_THIRD");
        partner.setPartnerName("模拟第三方系统（内置联调）");
        partner.setBaseUrl(demoBaseUrl());
        partner.setAuthType("HEADER");
        partner.setAuthUser("X-Partner-Token");
        partner.setAuthSecret(Crypto.encrypt("demo-secret"));
        partner.setHeadersJson("{\"X-Client\":\"interchange-platform\"}");
        partner.setTimeoutMs(15000);
        partner.setStatus(1);
        partner.setRemark("内置模拟对接方，指向 /api/mock/thirdparty/**，可直接联调推送链路；生产环境请删除或停用");
        Partner saved = partnerDao.save(partner);
        log.info("已创建演示对接方：{} -> {}", saved.getPartnerName(), saved.getBaseUrl());
        return saved;
    }

    /** 演示对接方指向的是平台自己的模拟接口，端口换了要跟着换，否则演示推送会连不上 */
    private String demoBaseUrl() {
        return "http://127.0.0.1:" + serverPort + "/api/mock/thirdparty";
    }

    private void healDemoPartnerUrl(Partner partner) {
        String url = partner.getBaseUrl();
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        Matcher m = LOCAL_PORT.matcher(url.trim());
        if (m.find() && !String.valueOf(serverPort).equals(m.group(3))) {
            String fixed = m.replaceFirst("$1$2:" + serverPort);
            partner.setBaseUrl(fixed);
            partnerDao.save(partner);
            log.info("演示对接方地址端口与当前启动端口不一致，已自动纠正：{} -> {}", url, fixed);
        }
    }

    private void initDemoTasks(Partner partner) {
        if (taskDao.findByTaskCode("DEMO-USER-PUSH").isEmpty()) {
            InterfaceTask task = new InterfaceTask();
            task.setTaskCode("DEMO-USER-PUSH");
            task.setTaskName("演示：用户数据定时推送");
            task.setPartnerId(partner.getId());
            task.setSourceType("SQL");
            task.setDatasourceKey("main");
            task.setSqlText("select LOGIN_NAME as userCode, USERNAME as userName, CREATE_TIME as createTime "
                    + "from sys_user");
            task.setTargetPath("/order/push");
            task.setHttpMethod("POST");
            task.setContentType("application/json;charset=UTF-8");
            task.setPushMode("BATCH");
            task.setCronExpression("0 0/5 * * * ?");
            task.setTimeoutMs(15000);
            task.setEnabled(true);
            task.setRemark("每 5 分钟把 sys_user 数据整批推送到模拟第三方，用于验证推送链路");
            taskDao.save(task);
            log.info("已创建演示任务：DEMO-USER-PUSH（每 5 分钟执行一次）");
        }

        if (taskDao.findByTaskCode("DEMO-HEARTBEAT").isEmpty()) {
            InterfaceTask task = new InterfaceTask();
            task.setTaskCode("DEMO-HEARTBEAT");
            task.setTaskName("演示：接口心跳探测");
            task.setPartnerId(partner.getId());
            task.setSourceType("FIXED");
            task.setFixedPayload("{\"type\":\"heartbeat\",\"source\":\"interchange-platform\","
                    + "\"time\":\"" + Utils.format(LocalDateTime.now()) + "\"}");
            task.setTargetPath("/ping");
            task.setHttpMethod("POST");
            task.setPushMode("BATCH");
            task.setCronExpression("0 0/1 * * * ?");
            task.setTimeoutMs(10000);
            task.setEnabled(false);
            task.setRemark("固定报文心跳，默认停用；可手工“立即执行”验证");
            taskDao.save(task);
            log.info("已创建演示任务：DEMO-HEARTBEAT（默认停用）");
        }
    }
}
