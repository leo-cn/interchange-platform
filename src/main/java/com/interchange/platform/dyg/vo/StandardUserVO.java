package com.interchange.platform.dyg.vo;

import lombok.Data;

/**
 * 标准接口「用户查询」的结果，对应视图 {@code standard_user_view}。
 *
 * <p>由 {@code BaseDao.listBySQLAliasToBean} 按 SQL 别名填充，
 * <b>字段名必须与 SQL 里的别名一致</b>，不要改字段名。
 */
@Data
public class StandardUserVO {

    private String id;
    /** 登录名 */
    private String loginName;
    /** 姓名 */
    private String username;
    /** 所属单位编码 */
    private String corpCode;
    /** 所属单位名称 */
    private String corpName;
    /** 1 启用（默认） / 0 禁用 */
    private String status;
    /** 更新日期（视图里已 to_char 成 yyyy-MM-dd） */
    private String updateDate;
}
