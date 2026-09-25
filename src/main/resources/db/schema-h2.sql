-- ============================================================================
--  接口交换平台 · H2 建表脚本（默认数据库，一般无需手工执行）
--  应用启动时 ddl-auto=update 会自动建表；本脚本仅供审阅或手工初始化。
--  数据文件：./data/interchange.mv.db
--  控制台：http://127.0.0.1:18080/h2-console
--          JDBC URL: jdbc:h2:file:./data/interchange;AUTO_SERVER=TRUE
--          用户名: sa    密码: 空
--  注意：H2 2.x 不支持 AUTO_SERVER=TRUE 与 DB_CLOSE_ON_EXIT=FALSE 同时出现
-- ============================================================================

-- 平台侧用户扩展属性（账号主数据在业务系统用户表，平台不建用户表）
CREATE TABLE IF NOT EXISTS sys_user_ext (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    ext_id            VARCHAR(64)  NOT NULL COMMENT '业务系统用户主键',
    role              VARCHAR(32),
    status            INT DEFAULT 1,
    api_token         VARCHAR(128),
    platform_password VARCHAR(128),
    last_login_time   TIMESTAMP,
    create_time       TIMESTAMP,
    update_time       TIMESTAMP,
    CONSTRAINT uk_user_ext_ext_id UNIQUE (ext_id)
);

-- 接入方凭证：appKey / appSecret 用来换 access_token（令牌本身只在缓存里）
CREATE TABLE IF NOT EXISTS api_token (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL COMMENT '请求方名称',
    app_key         VARCHAR(64)  NOT NULL COMMENT '客户端标识',
    app_secret      VARCHAR(128) NOT NULL COMMENT '客户端密钥',
    status          INT DEFAULT 1,
    allow_api_codes VARCHAR(512) COMMENT '允许调用的接口编码，逗号分隔；空=不限',
    ttl_seconds     INT DEFAULT 7200 COMMENT '签发的令牌有效期（秒）',
    remark          VARCHAR(255),
    last_used_time  TIMESTAMP,
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    CONSTRAINT uk_api_token_app_key UNIQUE (app_key)
);

CREATE TABLE IF NOT EXISTS receive_api (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_code      VARCHAR(64)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    status        INT DEFAULT 1,
    auth_required INT DEFAULT 1,
    remark        VARCHAR(255),
    create_time   TIMESTAMP,
    update_time   TIMESTAMP,
    CONSTRAINT uk_receive_api_code UNIQUE (api_code)
);

CREATE TABLE IF NOT EXISTS partner (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    partner_code VARCHAR(64)  NOT NULL,
    partner_name VARCHAR(128) NOT NULL,
    base_url     VARCHAR(512),
    auth_type    VARCHAR(32),
    auth_user    VARCHAR(128),
    auth_secret  VARCHAR(1024),
    headers_json VARCHAR(4000),
    timeout_ms   INT DEFAULT 15000,
    status       INT DEFAULT 1,
    remark       VARCHAR(512),
    create_time  TIMESTAMP,
    update_time  TIMESTAMP,
    CONSTRAINT uk_partner_code UNIQUE (partner_code)
);

CREATE TABLE IF NOT EXISTS interface_task (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_code          VARCHAR(64)  NOT NULL,
    task_name          VARCHAR(128) NOT NULL,
    partner_id         BIGINT,
    source_type        VARCHAR(16)  NOT NULL,
    datasource_key     VARCHAR(64)     DEFAULT 'main',
    sql_text           CLOB,
    pull_url           VARCHAR(1024),
    pull_method        VARCHAR(8)      DEFAULT 'GET',
    pull_headers       VARCHAR(4000),
    fixed_payload      CLOB,
    target_path        VARCHAR(512),
    http_method        VARCHAR(8)      DEFAULT 'POST',
    content_type       VARCHAR(128)    DEFAULT 'application/json;charset=UTF-8',
    push_mode          VARCHAR(16)     DEFAULT 'BATCH',
    headers_json       VARCHAR(4000),
    handler_bean       VARCHAR(64),
    cron_expression    VARCHAR(64),
    timeout_ms         INT             DEFAULT 15000,
    enabled            BOOLEAN         DEFAULT TRUE NOT NULL,
    remark             VARCHAR(512),
    last_exec_time     TIMESTAMP,
    last_result        VARCHAR(32),
    last_cost_ms       BIGINT,
    create_time        TIMESTAMP,
    update_time        TIMESTAMP,
    CONSTRAINT uk_task_code UNIQUE (task_code)
);
CREATE INDEX IF NOT EXISTS idx_task_enabled ON interface_task (enabled);
CREATE INDEX IF NOT EXISTS idx_task_partner ON interface_task (partner_id);

CREATE TABLE IF NOT EXISTS task_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id       BIGINT,
    task_code     VARCHAR(64),
    task_name     VARCHAR(128),
    partner_name  VARCHAR(128),
    trace_id      VARCHAR(64),
    trigger_type  VARCHAR(16),
    start_time    TIMESTAMP,
    end_time      TIMESTAMP,
    cost_ms       BIGINT,
    status        VARCHAR(16),
    total_count   INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count    INT DEFAULT 0,
    target_url    VARCHAR(1024),
    request_body  CLOB,
    response_body CLOB,
    error_msg     CLOB,
    create_time   TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_log_task_code ON task_log (task_code);
CREATE INDEX IF NOT EXISTS idx_log_start_time ON task_log (start_time);
CREATE INDEX IF NOT EXISTS idx_log_status ON task_log (status);

CREATE TABLE IF NOT EXISTS receive_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_code      VARCHAR(64),
    trace_id      VARCHAR(64),
    http_method   VARCHAR(8),
    remote_ip     VARCHAR(64),
    caller        VARCHAR(64),
    headers       VARCHAR(4000),
    request_body  CLOB,
    response_body CLOB,
    status        VARCHAR(16),
    error_msg     CLOB,
    cost_ms       BIGINT,
    receive_time  TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_recv_api_code ON receive_log (api_code);
CREATE INDEX IF NOT EXISTS idx_recv_time ON receive_log (receive_time);
