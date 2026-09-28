package com.interchange.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台业务配置，对应 application.yml 的 app.* 节点。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProps {

    /** 接收接口的全局令牌，可被用户的 api_token 单独覆盖 */
    private String receiveToken;

    /** 是否允许使用 H2 内存库启动（默认 false，检测到内存库会直接启动失败） */
    private boolean allowMemoryDb = false;

    private Push push = new Push();

    /** 接口日志（logs/interfaces 下每个接口一个文件）的输出样式 */
    private IfaceLog ifaceLog = new IfaceLog();

    /** 日志表（task_log / receive_log）的保留策略 */
    private Log log = new Log();

    /** SQL 取数可用的额外数据源 */
    private List<ExtraDataSource> extraDatasources = new ArrayList<>();

    /** 登录账号的来源：平台不建用户表，账号主数据来自业务系统 */
    private User user = new User();

    /** 主数据接收（MDM）接口的落库配置 */
    private Mdm mdm = new Mdm();

    /**
     * 接口日志输出样式。
     *
     * <p>都可以通过启动参数覆盖，例如
     * {@code --app.iface-log.pretty=true --app.iface-log.sql-pretty=true}。
     *
     * <p>注意：**过程日志（阶段明细）固定打印，不提供开关**。
     * 它是排查线上故障的唯一依据，做成开关只会出现"出事时发现没开"，
     * 因此代码里没有对应的开关分支，yml 里也不再有 verbose 这个键。
     */
    @Data
    public static class IfaceLog {
        /**
         * 是否缩进美化。
         * false（默认）= 一行一条 JSON（JSON Lines），便于 ELK / 脚本按行采集；
         * true = 多行缩进，便于肉眼查看（代价是一条记录占多行，不能按行采集）。
         */
        private boolean pretty = false;

        /** 日志里的 SQL 是否格式化（关键字换行对齐） */
        private boolean sqlPretty = false;

        /** 过程日志里是否带上报文正文（报文较大时可关掉，只留状态与耗时） */
        private boolean verbosePayload = true;

        /**
         * 过程日志（START / FETCHED / SEND_START / SEND_END / END）的输出样式。
         * text（默认）= 一行纯文本，肉眼直接看，形如：
         *   2026-09-21 22:05:00.123 | PUSH | OaUserTask | START | traceId=xxx | 开始运行定时任务：OaUserTask（用户数据推送）
         * json = 与汇总记录一致的一行 JSON，便于脚本/采集器解析。
         */
        private String stageFormat = "text";
    }

    /**
     * 日志表（task_log / receive_log）的保留策略。
     *
     * <p>日志表存的是完整报文（request_body / response_body 上限 100K 字符），
     * 高频任务（例如 30 秒一次）一天就能落近 3000 条，几个月就会把库撑大。
     * 默认改为「只留最新一条」：新的执行结果覆盖旧的，行数恒等于任务数 / 接口数。
     * 需要追溯历史明细时看接口日志文件 {@code logs/interfaces/{接口}/{接口}.log}，
     * 那里按次记录的全过程都在（跨天 gzip 保留 30 天）。
     */
    @Data
    public static class Log {
        /**
         * true（默认）= 每个任务 / 每个接口只保留最后一次执行结果（成功失败都覆盖）；
         * false = 每次执行都新增一条，保留完整历史（库会随时间增长）。
         */
        private boolean keepLatestOnly = true;
    }

    @Data
    public static class Push {
        /** 推送超时（毫秒） */
        private int timeoutMs = 15000;
        /** SQL 取数最大行数，防止一次拉爆内存 */
        private int maxRows = 10000;
        /** 单条日志报文落库上限（字符） */
        private int logBodyLimit = 100000;
    }

    @Data
    public static class ExtraDataSource {
        private String key;
        private String name;
        private String url;
        private String username;
        private String password;
        private String driver;
    }

    /**
     * 主数据接收（MDM）接口配置。
     *
     * <p>这套接口落的是资金/ERP 系统的基表（SYS_CORP、SYS_EXTERNAL_CORP、BT_BANK_ACC 等），
     * 产品不建自己的表，所以库必须是已经存在业务基表的那个库，各项默认值也必须与该库现有数据一致。
     */
    @Data
    public static class Mdm {
        /** 是否启用 MDM 接收接口（false 时 5 个接口不注册，URL 返回「接口未定义」） */
        private boolean enabled = false;

        /**
         * 推送方向的第三方地址前缀，对应 dyg-erp 的 REST 根 {@code http://host:port/{ctx}/rest/mdm}。
         * 留空则只在平台侧登记接收接口，不建推送示范任务。
         */
        private String pushBaseUrl = "";

        /**
         * 落库数据源 key：取值可以是 {@code main}（平台自身库）
         * 或 {@code app.extra-datasources} 里配的业务库 key。
         */
        private String datasource = "t6";

        /** 落库时的创建人/更新人 */
        private String createBy = "admin";

        /** 新建单位时写入的 net_id（资金系统基线数据的默认 nets/founder 记录） */
        private String netId = "402880425b8abcfb015b8ac16e980000";

        /** 新建银行账户时的默认账户类型 */
        private String bankAccType = "01";

        /** 新建银行账户时的默认账户属性 ID */
        private String bankAccAttribute = "4028802f5c0fe799015c10248b1a0029";

        /** 新建客商时写入的 supplier_id（主数据平台方） */
        private String supplierId = "58F9F65861CF2889E063D900A8C05056";

        /** 是否只接收境内银行网点（categoryCode=INSIDE），与 dyg-erp 原逻辑一致 */
        private boolean insideBranchOnly = true;

        /**
         * 基表表名用小写。Oracle 下表名不区分大小写，配成什么都一样；
         * Linux 上的 MySQL 在 lower_case_table_names=0 时表名<b>区分大小写</b>，
         * 如果这些表当初建成小写，这里要打开。
         */
        private boolean lowercaseTables = false;
    }

    /**
     * 登录账号的来源。
     *
     * <p>平台与业务系统共用一份账号：主数据（登录名/姓名/口令/启停）在业务库，
     * 平台只在自己的 sys_user_ext 里挂角色、令牌、平台口令。
     */
    @Data
    public static class User {
        /** 账号主数据所在的数据源 key，对应 app.extra-datasources 里配置的业务库 */
        private String datasource = "t6";

        /** 业务系统用户表名 */
        private String table = "sys_user";

        /** 业务系统账号首次登录平台时，是否自动建档（角色取 default-role） */
        private boolean autoProvision = true;

        /** 自动建档时赋予的角色 */
        private String defaultRole = "OPERATOR";

        /**
         * 是否允许直接用业务系统的口令登录平台。
         * 关掉后，只有在本平台改过密码（sys_user_ext.platform_password 有值）的账号能登录。
         */
        private boolean allowBizPassword = true;
    }
}
