-- ============================================================================
--  MDM 主数据接收接口 —— MySQL 扩容/加字段补丁
--  目标库：t6（MySQL）
-- ----------------------------------------------------------------------------
--  背景：这套接口原本在 dyg-erp 里跑的是 Oracle 库，新增字段是用 Oracle 的
--        BT_ADD_COLUMN 存储过程加的（MySQL 没有这个存储过程，也补不进去）：
--          源脚本 1：dyg-erp/sql/archived_20260916/dyg_erp_2026081101_主数据接收接口_zhours_ORCL.sql
--          源脚本 2：dyg-erp/sql/dyg_2026092401_单位增加所属集团_zhours_ORCL.sql
--          源脚本 3：dyg-erp/sql/dyg_2026092201_BIS_BIF_INIT名称字段长度调整_zhours_ORCL.sql
--        本文件是上述 Oracle 脚本按 MySQL 语法的等价翻译（VARCHAR2(n) → VARCHAR(n)）。
--
--  用法：mysql -uroot -p t6 < mdm-mysql-patch.sql
--        逐条执行即可；若报 1060 Duplicate column name，说明该列已存在，跳过即可，
--        不影响其余语句。执行后再启应用，看启动日志里的「MDM 落库自检」。
--
--  注意：本补丁【不含】单位授权（给管理员挂资源权限），那部分本期不做。
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. 银行类别：银行类别编码放宽到 4 位（源脚本 1 第 58 行）
-- ----------------------------------------------------------------------------
ALTER TABLE BT_BANK_TYPE MODIFY BANK_TYPE VARCHAR(4);

-- 主数据ID
ALTER TABLE BT_BANK_TYPE ADD COLUMN MD_ID VARCHAR(32) COMMENT '主数据ID(MDM主键，用于按MDM主键更新)';


-- ----------------------------------------------------------------------------
-- 2. 单位/网点表：mdId 与上级 mdId（源脚本 1 第 60-61 行）
--    说明：原逻辑只新增不更新，重复 mdId 会被判「重复接收」，md_id 建议加唯一索引。
-- ----------------------------------------------------------------------------
ALTER TABLE SYS_CORP ADD COLUMN MD_ID          VARCHAR(32) COMMENT '主数据ID(MDM主键)';
ALTER TABLE SYS_CORP ADD COLUMN PARENT_MD_ID   VARCHAR(32) COMMENT '父主数据ID';

-- 建议索引（可选，显著提升按 mdId 去重的查询速度；已有则忽略 1061 报错）
-- ALTER TABLE SYS_CORP ADD INDEX IDX_SYS_CORP_MD_ID (MD_ID);


-- ----------------------------------------------------------------------------
-- 3. 单位表：所属集团（源脚本 2）
-- ----------------------------------------------------------------------------
ALTER TABLE SYS_CORP ADD COLUMN GROUP_CODE VARCHAR(50) COMMENT '所属集团';


-- ----------------------------------------------------------------------------
-- 4. 银行网点（联行号）：主数据ID / 有效标志 / 简称（源脚本 1 第 63-65 行）
--    注意 VALID_SIGN 原定义是 1 位字符
-- ----------------------------------------------------------------------------
ALTER TABLE BT_INPUT_BANK_INFO ADD COLUMN MD_ID       VARCHAR(32)  COMMENT '主数据ID(MDM主键)';
ALTER TABLE BT_INPUT_BANK_INFO ADD COLUMN VALID_SIGN  VARCHAR(1)   COMMENT '有效标志';
ALTER TABLE BT_INPUT_BANK_INFO ADD COLUMN SHORT_NAME  VARCHAR(500) COMMENT '简称';


-- ----------------------------------------------------------------------------
-- 5. 银行账户：主数据ID / 账户名称（源脚本 1 第 66-67 行）
--    说明：BT_BANK_ACC 原本已有 ACC_NAME，「NAME」是 MDM 推送的显示名称，两者并存
-- ----------------------------------------------------------------------------
ALTER TABLE BT_BANK_ACC ADD COLUMN MD_ID VARCHAR(32)  COMMENT '主数据ID(MDM主键)';
ALTER TABLE BT_BANK_ACC ADD COLUMN NAME  VARCHAR(200) COMMENT '账户名称(MDM推送的显示名称)';


-- ----------------------------------------------------------------------------
-- 6. 外部客商：主数据ID（源脚本 1 第 68 行）
-- ----------------------------------------------------------------------------
ALTER TABLE SYS_EXTERNAL_CORP ADD COLUMN MD_ID VARCHAR(32) COMMENT '主数据ID(MDM主键)';


-- ----------------------------------------------------------------------------
-- 7. 银行接口初始表：名称字段放宽到 200（源脚本 3）
-- ----------------------------------------------------------------------------
ALTER TABLE BIS_BIF_INIT MODIFY NAME VARCHAR(200);


