package com.interchange.platform.dyg.vo;

import lombok.Data;

/**
 * 标准接口「科目查询」的结果，对应视图 {@code standard_item_view}。
 *
 * <p>由 {@code BaseDao.listBySQLAliasToBean} 按 SQL 别名填充，
 * <b>字段名必须与 SQL 里的别名一致</b>，不要改字段名。
 */
@Data
public class StandardItemVO {

    private String id;
    /** 科目代码 */
    private String itemCode;
    /** 科目名称 */
    private String itemName;
    /** 更新日期（视图里已 to_char 成 yyyy-MM-dd） */
    private String updateDate;
}
