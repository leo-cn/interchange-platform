package com.interchange.platform.dyg.vo;

import lombok.Data;

/**
 * 标准接口「网点（联行号）查询」的结果，对应视图 {@code standard_bankinput_view}。
 *
 * <p>由 {@code BaseDao.listBySQLAliasToBean} 按 SQL 别名填充，
 * <b>字段名必须与 SQL 里的别名一致</b>，不要改字段名。
 */
@Data
public class StandardBankInputVO {

    private String id;
    /** 联行号 */
    private String bankCode;
    /** 开户行名称 */
    private String bankName;
    /** 市代码 */
    private String cityCode;
    /** 市名称 */
    private String cityName;
    /** 省代码 */
    private String provCode;
    /** 省名称 */
    private String provName;
    /** 更新日期（视图里已 to_char 成 yyyy-MM-dd） */
    private String updateDate;
}
