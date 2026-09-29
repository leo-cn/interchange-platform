package com.interchange.platform.dyg.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 银行网点接收报文 → BT_INPUT_BANK_INFO。
 *
 * <p>Jackson 按字段名绑定，<b>不要重命名字段</b>，否则 MDM 推来的报文会静默丢字段。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BankBranchVO {

    private String mdId;
    private String mdCode;
    private String mdDescription;
    /** 银行类别 mdId，必须先在 BT_BANK_TYPE 里接收过 */
    private String banktype;
    private String banktypeCodeShow;
    private String banktypeNameShow;
    /** 联行号 */
    private String combinenum;
    /** 开户行名称 */
    private String name;
    /** INSIDE 境内 / OVERSEAS 境外 / FIN 财务机构 */
    private String categoryCode;
    private String bankPrefix;
    private String mdStatusCode;
    /** 银行简称 */
    private String shortname;
}
