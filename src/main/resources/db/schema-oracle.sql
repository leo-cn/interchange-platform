-- ============================================================================
--  接口交换平台 · Oracle 12c+ 建表脚本
--  使用方式：
--    1) 用 DBA 账号建用户：
--         CREATE USER interchange IDENTIFIED BY interchange;
--         GRANT CONNECT, RESOURCE, UNLIMITED TABLESPACE TO interchange;
--    2) 以 interchange 用户执行本脚本；
--    3) 启动参数：--spring.profiles.active=oracle
--    4) 生产环境建议 --spring.jpa.hibernate.ddl-auto=validate
--
--  类型映射说明：
--    · 布尔字段（enabled）用 NUMBER(1)：1=启用 0=停用
--    · 超长文本（SQL、报文、错误信息）用 CLOB
--    · Hibernate 读写 CLOB 时若报 ORA-24816，可改用 LONG 或将
--      hibernate.jdbc.use_streams_for_binary 保持默认
-- ============================================================================

-- 平台侧用户扩展属性（账号主数据在业务系统用户表，平台不建用户表）
CREATE TABLE sys_user_ext (
    id                NUMBER(19)     NOT NULL,
    ext_id            VARCHAR2(64)   NOT NULL,
    role              VARCHAR2(32),
    status            NUMBER(10)     DEFAULT 1,
    api_token         VARCHAR2(128),
    platform_password VARCHAR2(128),
    last_login_time   TIMESTAMP,
    create_time       TIMESTAMP,
    update_time       TIMESTAMP,
    CONSTRAINT pk_sys_user_ext PRIMARY KEY (id),
    CONSTRAINT uk_user_ext_ext_id UNIQUE (ext_id)
);
COMMENT ON TABLE sys_user_ext IS '平台用户扩展属性';
COMMENT ON COLUMN sys_user_ext.ext_id IS '业务系统用户主键';

-- 接入方凭证：appKey / appSecret 用来换 access_token（令牌本身只在缓存里）
CREATE TABLE api_token (
    id              NUMBER(19)    NOT NULL,
    name            VARCHAR2(128) NOT NULL,
    app_key         VARCHAR2(64)  NOT NULL,
    app_secret      VARCHAR2(128) NOT NULL,
    status          NUMBER(10)    DEFAULT 1,
    allow_api_codes VARCHAR2(512),
    ttl_seconds     NUMBER(10)    DEFAULT 7200,
    remark          VARCHAR2(255),
    last_used_time  TIMESTAMP,
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    CONSTRAINT pk_api_token PRIMARY KEY (id),
    CONSTRAINT uk_api_token_app_key UNIQUE (app_key)
);
COMMENT ON TABLE api_token IS '接入方凭证（换票用）';

-- 接收接口登记表（接口清单）。
-- 平台的接收端点只有一条 POST /api/receive/{apiCode}；这张表决定哪些 apiCode 是"存在"的。
-- 未登记或已停用的接口会被直接拒绝，不会被兜底回执成成功。
-- 本表不含任何令牌信息（令牌在 api_token 凭证 + 服务端签发缓存里）。
CREATE TABLE receive_api (
    id            NUMBER(19)      NOT NULL,
    api_code      VARCHAR2(64)    NOT NULL,
    name          VARCHAR2(128)   NOT NULL,
    status        NUMBER(3)       DEFAULT 1,
    auth_required NUMBER(3)       DEFAULT 1,
    remark        VARCHAR2(255),
    create_time   TIMESTAMP,
    update_time   TIMESTAMP,
    CONSTRAINT pk_receive_api PRIMARY KEY (id),
    CONSTRAINT uk_receive_api_code UNIQUE (api_code)
);
COMMENT ON TABLE receive_api IS '接收接口登记';

-- 第三方系统（对接方）
CREATE TABLE partner (
    id           NUMBER(19)      NOT NULL,
    partner_code VARCHAR2(64)    NOT NULL,
    partner_name VARCHAR2(128)   NOT NULL,
    base_url     VARCHAR2(512),
    auth_type    VARCHAR2(32),
    auth_user    VARCHAR2(128),
    auth_secret  VARCHAR2(1024),
    headers_json VARCHAR2(4000),
    timeout_ms   NUMBER(10)      DEFAULT 15000,
    status       NUMBER(10)      DEFAULT 1,
    remark       VARCHAR2(512),
    create_time  TIMESTAMP,
    update_time  TIMESTAMP,
    CONSTRAINT pk_partner PRIMARY KEY (id),
    CONSTRAINT uk_partner_code UNIQUE (partner_code)
);

-- 定时推送任务
CREATE TABLE interface_task (
    id                 NUMBER(19)     NOT NULL,
    task_code          VARCHAR2(64)   NOT NULL,
    task_name          VARCHAR2(128)  NOT NULL,
    partner_id         NUMBER(19),
    source_type        VARCHAR2(16)   NOT NULL,
    datasource_key     VARCHAR2(64)   DEFAULT 'main',
    sql_text           CLOB,
    pull_url           VARCHAR2(1024),
    pull_method        VARCHAR2(8)    DEFAULT 'GET',
    pull_headers       VARCHAR2(4000),
    fixed_payload      CLOB,
    target_path        VARCHAR2(512),
    http_method        VARCHAR2(8)    DEFAULT 'POST',
    content_type       VARCHAR2(128)  DEFAULT 'application/json;charset=UTF-8',
    push_mode          VARCHAR2(16)   DEFAULT 'BATCH',
    headers_json       VARCHAR2(4000),
    handler_bean       VARCHAR2(64),
    cron_expression    VARCHAR2(64),
    timeout_ms         NUMBER(10)     DEFAULT 15000,
    enabled            NUMBER(1)      DEFAULT 1 NOT NULL,
    remark             VARCHAR2(512),
    last_exec_time     TIMESTAMP,
    last_result        VARCHAR2(32),
    last_cost_ms       NUMBER(19),
    create_time        TIMESTAMP,
    update_time        TIMESTAMP,
    CONSTRAINT pk_interface_task PRIMARY KEY (id),
    CONSTRAINT uk_task_code UNIQUE (task_code)
);
CREATE INDEX idx_task_enabled ON interface_task (enabled);
CREATE INDEX idx_task_partner ON interface_task (partner_id);

-- 任务执行日志
CREATE TABLE task_log (
    id            NUMBER(19)    NOT NULL,
    task_id       NUMBER(19),
    task_code     VARCHAR2(64),
    task_name     VARCHAR2(128),
    partner_name  VARCHAR2(128),
    trace_id      VARCHAR2(64),
    trigger_type  VARCHAR2(16),
    start_time    TIMESTAMP,
    end_time      TIMESTAMP,
    cost_ms       NUMBER(19),
    status        VARCHAR2(16),
    total_count   NUMBER(10)    DEFAULT 0,
    success_count NUMBER(10)    DEFAULT 0,
    fail_count    NUMBER(10)    DEFAULT 0,
    target_url    VARCHAR2(1024),
    request_body  CLOB,
    response_body CLOB,
    error_msg     CLOB,
    create_time   TIMESTAMP,
    CONSTRAINT pk_task_log PRIMARY KEY (id)
);
CREATE INDEX idx_log_task_code ON task_log (task_code);
CREATE INDEX idx_log_start_time ON task_log (start_time);
CREATE INDEX idx_log_status ON task_log (status);

-- 接收日志
CREATE TABLE receive_log (
    id            NUMBER(19)   NOT NULL,
    api_code      VARCHAR2(64),
    trace_id      VARCHAR2(64),
    http_method   VARCHAR2(8),
    remote_ip     VARCHAR2(64),
    caller        VARCHAR2(64),
    headers       VARCHAR2(4000),
    request_body  CLOB,
    response_body CLOB,
    status        VARCHAR2(16),
    error_msg     CLOB,
    cost_ms       NUMBER(19),
    receive_time  TIMESTAMP,
    CONSTRAINT pk_receive_log PRIMARY KEY (id)
);
CREATE INDEX idx_recv_api_code ON receive_log (api_code);
CREATE INDEX idx_recv_time ON receive_log (receive_time);

-- 主键使用 IDENTITY（Oracle 12c+ 支持），Hibernate 采用 GenerationType.IDENTITY
ALTER TABLE sys_user_ext   MODIFY id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY;
ALTER TABLE api_token      MODIFY id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY;
ALTER TABLE receive_api    MODIFY id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY;
ALTER TABLE partner        MODIFY id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY;
ALTER TABLE interface_task MODIFY id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY;
ALTER TABLE task_log       MODIFY id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY;
ALTER TABLE receive_log    MODIFY id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY;

-- ----------------------------------------------------------------------------
-- 关于管理员账号：
--   不要在本脚本里手工插入 admin（BCrypt 散列与密码强绑定，手抄散列会导致登录失败）。
--   应用首次启动时会自动创建： admin / Admin@123
-- ----------------------------------------------------------------------------

COMMIT;
