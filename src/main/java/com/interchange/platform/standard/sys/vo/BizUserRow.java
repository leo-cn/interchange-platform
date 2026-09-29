package com.interchange.platform.standard.sys.vo;

import lombok.Data;

/**
 * 业务系统账号表的行对象（只读）。
 *
 * <p>表名与数据源由 {@code app.user.*} 配置决定，所以**不建 JPA 实体**：
 * 表名可变、且目标库不一定是主库。由 {@code BizUserDao} 用原生 SQL 映射成本对象。
 */
@Data
public class BizUserRow {

    /** 业务系统主键 */
    private String id;

    /** 登录名 */
    private String loginName;

    /** 中文姓名 */
    private String username;

    /** 口令摘要 */
    private String password;

    /** 盐值 */
    private String salt;

    /** 状态：1 启用、其他停用 */
    private int status;

    /** 所属单位 */
    private String corpId;
}
