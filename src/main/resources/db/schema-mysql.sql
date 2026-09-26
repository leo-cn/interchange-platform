-- ============================================================================
--  接口交换平台 · MySQL 8.x 建表脚本
--  使用方式：
--    1) CREATE DATABASE t6 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
--    2) mysql -uroot -p t6 < schema-mysql.sql
--    3) 启动参数：--spring.profiles.active=mysql
--  说明：应用默认 ddl-auto=update 也会自动建表；脚本用于生产环境手工初始化/审阅。
-- ============================================================================

-- 平台侧用户扩展属性。
-- 平台不建自己的用户表：账号主数据（登录名 / 姓名 / 口令 / 启停）在业务系统用户表
-- （默认 t6.sys_user，见 app.user.datasource / app.user.table），本表按 ext_id 挂在
-- 业务用户主键上，只存平台专有的角色 / 令牌 / 平台口令。
CREATE TABLE IF NOT EXISTS sys_user_ext
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ext_id            VARCHAR(64)  NOT NULL COMMENT '业务系统用户主键（t6.sys_user.ID）',
    role              VARCHAR(32) COMMENT 'ADMIN/OPERATOR/VIEWER',
    status            INT DEFAULT 1 COMMENT '平台侧 1 启用 0 停用（不回写业务系统）',
    api_token         VARCHAR(128) COMMENT '接收接口调用令牌',
    platform_password VARCHAR(128) COMMENT '平台口令（BCrypt）；为空表示沿用业务系统口令',
    last_login_time   DATETIME COMMENT '最近登录时间',
    create_time       DATETIME COMMENT '创建时间',
    update_time       DATETIME COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_ext_ext_id (ext_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '平台用户扩展属性';

-- 接入方凭证：作为服务方时，给每个请求方发 appKey / appSecret 用来换票。
-- 换出来的 access_token 只在服务端缓存里（带到期时间），不落库。
CREATE TABLE IF NOT EXISTS api_token
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(128) NOT NULL COMMENT '请求方名称',
    app_key         VARCHAR(64)  NOT NULL COMMENT '客户端标识（换票用）',
    app_secret      VARCHAR(128) NOT NULL COMMENT '客户端密钥（换票用）',
    status          INT DEFAULT 1 COMMENT '1 启用 0 停用',
    allow_api_codes VARCHAR(512) COMMENT '允许调用的接口编码，逗号分隔；空=不限',
    ttl_seconds     INT DEFAULT 7200 COMMENT '签发的令牌有效期（秒）',
    remark          VARCHAR(255) COMMENT '备注',
    last_used_time  DATETIME COMMENT '最近一次换票时间',
    create_time     DATETIME COMMENT '创建时间',
    update_time     DATETIME COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_token_app_key (app_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '接入方凭证';

-- 历史库升级：平台自建的 sys_user 已于 2026-09-25 下线（备份表 sys_user_bak_20260925 保留）。
-- 新库不会建这张表；若老库还残留，可自行备份后删除：
--   CREATE TABLE sys_user_bak AS SELECT * FROM sys_user;
--   DROP TABLE sys_user;

-- 接收接口登记表（接口清单）。
-- 平台的接收端点只有一条 POST /api/receive/{apiCode}；这张表决定哪些 apiCode 是"存在"的。
-- 未登记或已停用的接口会被直接拒绝，不会被兜底回执成成功。
-- 本表不含任何令牌信息（令牌在 api_token 凭证 + 服务端签发缓存里）。
CREATE TABLE IF NOT EXISTS receive_api
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    api_code      VARCHAR(64)  NOT NULL COMMENT '接口编码，对应 URL 上的 /api/receive/{apiCode}',
    name          VARCHAR(128) NOT NULL COMMENT '接口名称',
    status        INT DEFAULT 1 COMMENT '1 启用 0 停用（停用时第三方调用被拒）',
    auth_required INT DEFAULT 1 COMMENT '1 需要令牌鉴权 0 免鉴权',
    remark        VARCHAR(255) COMMENT '备注',
    create_time   DATETIME COMMENT '创建时间',
    update_time   DATETIME COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_receive_api_code (api_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '接收接口登记';

-- 第三方系统（对接方）
CREATE TABLE IF NOT EXISTS partner
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    partner_code VARCHAR(64)  NOT NULL COMMENT '系统编码',
    partner_name VARCHAR(128) NOT NULL COMMENT '系统名称',
    base_url     VARCHAR(512) COMMENT '服务地址',
    auth_type    VARCHAR(32) COMMENT 'NONE/BASIC/BEARER/API_KEY/HEADER',
    auth_user    VARCHAR(128) COMMENT '认证用户名或自定义 Header 名',
    auth_secret  VARCHAR(1024) COMMENT '密钥（AES-GCM 加密存储）',
    headers_json VARCHAR(4000) COMMENT '公共请求头 JSON',
    timeout_ms   INT DEFAULT 15000 COMMENT '超时毫秒',
    status       INT DEFAULT 1 COMMENT '1 启用 0 停用',
    remark       VARCHAR(512),
    create_time  DATETIME,
    update_time  DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_partner_code (partner_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '第三方系统';

-- 定时推送任务
CREATE TABLE IF NOT EXISTS interface_task
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    task_code          VARCHAR(64)  NOT NULL COMMENT '任务编码',
    task_name          VARCHAR(128) NOT NULL COMMENT '任务名称',
    partner_id         BIGINT COMMENT '目标第三方系统',
    source_type        VARCHAR(16)  NOT NULL COMMENT 'SQL/HTTP_PULL/FIXED',
    datasource_key     VARCHAR(64) DEFAULT 'main' COMMENT '取数数据源',
    sql_text           LONGTEXT COMMENT '取数 SQL（仅 SELECT）',
    pull_url           VARCHAR(1024) COMMENT 'HTTP 拉取地址',
    pull_method        VARCHAR(8) DEFAULT 'GET',
    pull_headers       VARCHAR(4000) COMMENT '拉取请求头 JSON',
    fixed_payload      LONGTEXT COMMENT '固定报文',
    target_path        VARCHAR(512) COMMENT '目标路径',
    http_method        VARCHAR(8) DEFAULT 'POST',
    content_type       VARCHAR(128) DEFAULT 'application/json;charset=UTF-8',
    push_mode          VARCHAR(16) DEFAULT 'BATCH' COMMENT 'BATCH 整批 / PER_ROW 逐条',
    headers_json       VARCHAR(4000) COMMENT '附加请求头 JSON',
    handler_bean       VARCHAR(64) COMMENT '自定义处理器 TaskHandler.code，留空走默认推送逻辑',
    cron_expression    VARCHAR(64) COMMENT 'Cron 表达式',
    timeout_ms         INT DEFAULT 15000,
    enabled            TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark             VARCHAR(512),
    last_exec_time     DATETIME COMMENT '最近执行时间',
    last_result        VARCHAR(32) COMMENT '最近执行结果',
    last_cost_ms       BIGINT COMMENT '最近耗时毫秒',
    create_time        DATETIME,
    update_time        DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_code (task_code),
    KEY idx_task_enabled (enabled),
    KEY idx_task_partner (partner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '定时推送任务';

-- 任务执行日志
CREATE TABLE IF NOT EXISTS task_log
(
    id            BIGINT NOT NULL AUTO_INCREMENT,
    task_id       BIGINT,
    task_code     VARCHAR(64),
    task_name     VARCHAR(128),
    partner_name  VARCHAR(128),
    trace_id      VARCHAR(64),
    trigger_type  VARCHAR(16) COMMENT 'CRON/MANUAL',
    start_time    DATETIME,
    end_time      DATETIME,
    cost_ms       BIGINT,
    status        VARCHAR(16) COMMENT 'SUCCESS/FAIL/PARTIAL',
    total_count   INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count    INT DEFAULT 0,
    target_url    VARCHAR(1024),
    request_body  LONGTEXT,
    response_body LONGTEXT,
    error_msg     LONGTEXT,
    create_time   DATETIME,
    PRIMARY KEY (id),
    KEY idx_log_task_code (task_code),
    KEY idx_log_start_time (start_time),
    KEY idx_log_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '任务执行日志';

-- 接收日志
CREATE TABLE IF NOT EXISTS receive_log
(
    id            BIGINT NOT NULL AUTO_INCREMENT,
    api_code      VARCHAR(64) COMMENT '接口编码',
    trace_id      VARCHAR(64),
    http_method   VARCHAR(8),
    remote_ip     VARCHAR(64),
    caller        VARCHAR(64) COMMENT '调用方',
    headers       VARCHAR(4000) COMMENT '请求头 JSON',
    request_body  LONGTEXT,
    response_body LONGTEXT,
    status        VARCHAR(16) COMMENT 'SUCCESS/FAIL',
    error_msg     LONGTEXT,
    cost_ms       BIGINT,
    receive_time  DATETIME,
    PRIMARY KEY (id),
    KEY idx_recv_api_code (api_code),
    KEY idx_recv_time (receive_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '接收日志';

-- ----------------------------------------------------------------------------
-- 关于管理员账号：
--   不要在本脚本里手工插入 admin。BCrypt 散列与密码强绑定，手抄散列极易导致登录失败。
--   应用首次启动时会自动创建管理员账号： admin / Admin@123
--   （见 DataInitializer#initAdmin，可登录后到「个人中心」修改密码）
-- ----------------------------------------------------------------------------
