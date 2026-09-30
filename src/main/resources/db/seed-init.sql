-- ============================================================================
--  接口交换平台 —— 业务初始化数据（预置「对接方 + 定时任务」）
-- ----------------------------------------------------------------------------

-- ============================================================================
-- 一、对接方（partner）：推送任务的目标系统
-- ============================================================================

-- 1) 集团 OA —— 债券业务审批单推送目标（dyg/task/CmsBondIssueTask）
--    注意：partner.auth_secret 是 AES 密文（Crypto.encrypt），脚本里算不出来，
--          所以先给 auth_type='NONE'；若 OA 要令牌，请登录后在「第三方系统」页面填一次密钥。
INSERT INTO partner (partner_code, partner_name, base_url, auth_type, auth_user, auth_secret,
                     headers_json, timeout_ms, status, remark)
SELECT 'OA_JT', '集团 OA 审批系统', 'http://oa.example.com', 'NONE', NULL, NULL,
       '{"X-Client":"interchange-platform"}', 15000, 1,
       'TODO base_url 换成集团 OA 实际地址；债券审批单推送任务的目标系统'
WHERE NOT EXISTS (SELECT 1 FROM partner WHERE partner_code = 'OA_JT');

-- 2) MDM 主数据接收方 —— 5 个主数据推送任务的目标
--    这里默认指向本机（自己推给自己，可用来验证链路）；生产环境换成对端地址。
--    对端若是同款平台，路径就是 /api/receive/{apiCode}，与本脚本的 target_path 一致。
INSERT INTO partner (partner_code, partner_name, base_url, auth_type, auth_user, auth_secret,
                     headers_json, timeout_ms, status, remark)
SELECT 'MDM_RECEIVER', 'MDM 主数据接收方', 'http://127.0.0.1:18080', 'NONE', NULL, NULL,
       NULL, 15000, 1,
'TODO base_url 换成实际接收方；5 个 MDM 推送任务的目标系统'
WHERE NOT EXISTS (SELECT 1 FROM partner WHERE partner_code = 'MDM_RECEIVER');


-- ============================================================================
-- 二、定时任务（interface_task）：5 个 MDM 主数据推送 + 1 个债券审批单推送
-- ----------------------------------------------------------------------------
-- ============================================================================

-- 1) 银行类别
INSERT INTO interface_task (task_code, task_name, partner_id, source_type, datasource_key, sql_text,
                            target_path, http_method, content_type, push_mode, headers_json,
                            handler_bean, cron_expression, timeout_ms, enabled, remark,
                            create_time, update_time)
SELECT 'MDM-PUSH-BANK-TYPE', 'MDM 推送：银行类别', p.id, 'SQL', 'main',
       'select t.MD_ID as mdId, t.BANK_TYPE as code from BT_BANK_TYPE t where t.MD_ID is not null',
       '/api/receive/mdm-bank-type-receive', 'POST', 'application/json;charset=UTF-8', 'BATCH', NULL,
       'mdmPush', '0 0 2 * * ?', 15000, 0,
       '预置模板：请核对取数字段与对端地址后启用（第 1 批，必须先于网点/账户推送）',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM partner p
WHERE p.partner_code = 'MDM_RECEIVER'
  AND NOT EXISTS (SELECT 1 FROM interface_task WHERE task_code = 'MDM-PUSH-BANK-TYPE');

-- 2) 银行网点（联行号）
INSERT INTO interface_task (task_code, task_name, partner_id, source_type, datasource_key, sql_text,
                            target_path, http_method, content_type, push_mode, headers_json,
                            handler_bean, cron_expression, timeout_ms, enabled, remark,
                            create_time, update_time)
SELECT 'MDM-PUSH-BANK-BRANCH', 'MDM 推送：银行网点', p.id, 'SQL', 'main',
       'select t.MD_ID as mdId, t.BANK_NAME as name from BT_INPUT_BANK_INFO t where t.MD_ID is not null',
       '/api/receive/mdm-bank-branch-receive', 'POST', 'application/json;charset=UTF-8', 'BATCH', NULL,
       'mdmPush', '0 10 2 * * ?', 15000, 0,
       '预置模板：只接收境内银行（category=INSIDE），对端会据此过滤',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM partner p
WHERE p.partner_code = 'MDM_RECEIVER'
  AND NOT EXISTS (SELECT 1 FROM interface_task WHERE task_code = 'MDM-PUSH-BANK-BRANCH');

-- 3) 组织机构（单位）
INSERT INTO interface_task (task_code, task_name, partner_id, source_type, datasource_key, sql_text,
                            target_path, http_method, content_type, push_mode, headers_json,
                            handler_bean, cron_expression, timeout_ms, enabled, remark,
                            create_time, update_time)
SELECT 'MDM-PUSH-ORG', 'MDM 推送：组织机构', p.id, 'SQL', 'main',
       'select t.MD_ID as mdId, t.CODE as code, t.NAME as name from SYS_CORP t where t.MD_ID is not null',
       '/api/receive/mdm-org-receive', 'POST', 'application/json;charset=UTF-8', 'BATCH', NULL,
       'mdmPush', '0 20 2 * * ?', 15000, 0,
       '预置模板：对端只新增不更新，重复 mdId 会被判「重复接收」',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM partner p
WHERE p.partner_code = 'MDM_RECEIVER'
  AND NOT EXISTS (SELECT 1 FROM interface_task WHERE task_code = 'MDM-PUSH-ORG');

-- 4) 银行账户（主表 + 币种子表，对端串行接受）
INSERT INTO interface_task (task_code, task_name, partner_id, source_type, datasource_key, sql_text,
                            target_path, http_method, content_type, push_mode, headers_json,
                            handler_bean, cron_expression, timeout_ms, enabled, remark,
                            create_time, update_time)
SELECT 'MDM-PUSH-BANK-ACC', 'MDM 推送：银行账户', p.id, 'SQL', 'main',
       'select t.MD_ID as mdId, t.NAME as name from BT_BANK_ACC t where t.MD_ID is not null',
       '/api/receive/mdm-bank-acc-receive', 'POST', 'application/json;charset=UTF-8', 'BATCH', NULL,
       'mdmPush', '0 30 2 * * ?', 15000, 0,
       '预置模板：账号明细（bdBankaccsub）与关联主数据必须在同一报文里推全，否则对端校验不过',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM partner p
WHERE p.partner_code = 'MDM_RECEIVER'
  AND NOT EXISTS (SELECT 1 FROM interface_task WHERE task_code = 'MDM-PUSH-BANK-ACC');

-- 5) 客商（外部往来单位 + 账号子表）
INSERT INTO interface_task (task_code, task_name, partner_id, source_type, datasource_key, sql_text,
                            target_path, http_method, content_type, push_mode, headers_json,
                            handler_bean, cron_expression, timeout_ms, enabled, remark,
                            create_time, update_time)
SELECT 'MDM-PUSH-PARTNER', 'MDM 推送：客商', p.id, 'SQL', 'main',
       'select t.MD_ID as mdId, t.CODE as code, t.NAME as name from SYS_EXTERNAL_CORP t where t.MD_ID is not null',
       '/api/receive/mdm-partner-receive', 'POST', 'application/json;charset=UTF-8', 'BATCH', NULL,
       'mdmPush', '0 40 2 * * ?', 15000, 0,
       '预置模板：客商类型 bptype（SUP/CUS/BP）与账号明细按对端要求补全',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM partner p
WHERE p.partner_code = 'MDM_RECEIVER'
  AND NOT EXISTS (SELECT 1 FROM interface_task WHERE task_code = 'MDM-PUSH-PARTNER');

-- 6) 债券业务审批单推送 OA（dyg/task/CmsBondIssueTask）
--    取数 SQL 的字段名必须是 billCode / applyUser / postscript（处理器按这三个键取值），
--    表名与「待推送」的过滤条件按资金系统实际库调整。
INSERT INTO interface_task (task_code, task_name, partner_id, source_type, datasource_key, sql_text,
                            target_path, http_method, content_type, push_mode, headers_json,
                            handler_bean, cron_expression, timeout_ms, enabled, remark,
                            create_time, update_time)
SELECT 'OA-CMS-BOND-ISSUE', '债券业务审批单推送 OA', p.id, 'SQL', 'main',
       'select t.BILL_CODE as billCode, t.APPLY_USER as applyUser, t.POSTSCRIPT as postscript from CMS_BOND_ISSUE t where t.PUSH_FLAG = 0',
       '/oa/approve/save', 'POST', 'application/json;charset=UTF-8', 'PER_ROW', NULL,
       'cmsBondIssue', '0 0/5 * * * ?', 15000, 0,
       'TODO 表名/过滤条件/目标路径按实际调整；必须 PER_ROW（一张单一次请求）',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM partner p
WHERE p.partner_code = 'OA_JT'
  AND NOT EXISTS (SELECT 1 FROM interface_task WHERE task_code = 'OA-CMS-BOND-ISSUE');


-- ============================================================================
-- 三、执行后核对 / 启用
-- ============================================================================

-- 核对（应看到 2 个对接方、6 个任务，enabled 全为 0）
-- SELECT partner_code, partner_name, base_url FROM partner WHERE partner_code IN ('OA_JT', 'MDM_RECEIVER');
-- SELECT task_code, task_name, handler_bean, push_mode, enabled, cron_expression
--   FROM interface_task ORDER BY task_code;

-- 确认地址与 SQL 无误后启用（或在页面上点「启用」，效果相同，都会重新注册 Quartz 调度）：
-- UPDATE interface_task SET enabled = 1, update_time = CURRENT_TIMESTAMP
--  WHERE task_code LIKE 'MDM-PUSH-%';
-- UPDATE interface_task SET enabled = 1, update_time = CURRENT_TIMESTAMP
--  WHERE task_code = 'OA-CMS-BOND-ISSUE';
