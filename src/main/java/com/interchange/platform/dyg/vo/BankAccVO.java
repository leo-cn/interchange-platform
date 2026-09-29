package com.interchange.platform.dyg.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 银行账户接收报文 → BT_BANK_ACC。
 *
 * <p>Jackson 按字段名绑定，<b>不要重命名字段</b>，否则 MDM 推来的报文会静默丢字段。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BankAccVO {

    private String mdId;
    private String mdCode;
    private String mdDescription;
    /** 银行网点 mdId */
    private String pkBankdoc;
    /** 银行类别 mdId */
    private String pkBanktype;
    /** 银行账号 */
    private String accNum;
    /** 户名 */
    private String accName;
    /** ACTIVE 启用 / INVALID 停用 */
    private String mdStatusCode;
    /** 开户日期 yyyy-MM-dd HH:mm:ss */
    private String accopendate;
    /** 组织机构 mdId */
    private String financeorg;
    /** 网银查询标志 */
    private String netqueryflag;
    /** 账户名称 */
    private String name;
    /** 账户性质编码 */
    private String accattribute;
    /** 账户性质名称 */
    private String accattributeDesc;
    /** 账户币种列表 */
    private List<BankAccCurVO> bdBankaccsub;
}
