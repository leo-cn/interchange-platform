package com.interchange.platform.dyg.vo;

import lombok.Data;

/**
 * 标准接口「单位查询」的结果，对应视图 {@code standard_corp_view}。
 *
 * <p>由 {@code BaseDao.listBySQLAliasToBean} 按 SQL 别名填充，
 * <b>字段名必须与 SQL 里的别名一致</b>，不要改字段名。
 */
@Data
public class StandardCorpVO {

    /** 单位编码 */
    private String code;
    /** 单位名称 */
    private String name;
    /** 状态：0 停用 / 1 启用 */
    private String status;
    /** 更新日期（视图里已 to_char 成 yyyy-MM-dd） */
    private String updateDate;
}
