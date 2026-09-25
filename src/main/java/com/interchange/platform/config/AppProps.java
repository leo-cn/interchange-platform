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
