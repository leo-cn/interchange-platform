package com.interchange.platform.dyg.vo;

import lombok.Data;

/**
 * 标准接口「币种查询」的结果，对应视图 {@code standard_cur_view}。
 *
 * <p>由 {@code BaseDao.listBySQLAliasToBean} 按 SQL 别名填充，
 * <b>字段名必须与 SQL 里的别名一致</b>，不要改字段名。
 */
@Data
public class StandardCurVO {

    private String id;
    /** 币种代码 */
    private String curCode;
    /** 币种名称 */
    private String curName;
    /** 币种简码 */
    private String englishCode;
    /** 币种汇率 */
    private String curExrate;
    /** 有效标志 */
    private String validSign;
    /** 更新日期（视图里已 to_char 成 yyyy-MM-dd） */
    private String updateDate;
}
