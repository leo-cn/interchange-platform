package com.interchange.platform.dyg.vo;

import lombok.Data;

/**
 * 标准接口「客商查询」的结果，对应视图 {@code standard_externalcorp_view}。
 *
 * <p>由 {@code BaseDao.listBySQLAliasToBean} 按 SQL 别名填充，
 * <b>字段名必须与 SQL 里的别名一致</b>，不要改字段名。
 */
@Data
public class StandardExternalCorpVO {

    private String id;
    /** 客商编码 */
    private String code;
    /** 客商名称 */
    private String name;
    /** 客商类型 */
    private String externalType;
    /** 业务类型 */
    private String bwType;
    private String status;
}
