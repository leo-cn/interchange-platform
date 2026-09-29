package com.interchange.platform.dyg.vo;

import lombok.Data;

/**
 * 标准接口「银行账号查询」的结果，对应视图 {@code standard_bankacc_view}。
 *
 * <p>由 {@code BaseDao.listBySQLAliasToBean} 按 SQL 别名填充，
 * <b>字段名必须与 SQL 里的别名一致</b>，不要改字段名。
 */
@Data
public class StandardBankAccVO {

    private String id;
    /** 银行账号 */
    private String bankAcc;
    /** 账户名称 */
    private String accName;
    /** 单位编码 */
    private String corpCode;
    /** 单位名称 */
    private String corpName;
    /** 币种代码 */
    private String curCode;
    /** 币种名称 */
    private String curName;
    /** 联行号 */
    private String bankCode;
    /** 开户行名称 */
    private String bankName;
    /** 是否网银：0 否 / 1 是 */
    private String isOnline;
    /** 有效标志：0 有效 / 1 无效 */
    private String validSign;
    /** 更新日期（视图里已 to_char 成 yyyy-MM-dd） */
    private String updateDate;
}
