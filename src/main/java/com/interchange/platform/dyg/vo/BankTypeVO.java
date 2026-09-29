package com.interchange.platform.dyg.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 银行类别接收报文 → BT_BANK_TYPE。
 *
 * <p>Jackson 按字段名绑定，<b>不要重命名字段</b>，否则 MDM 推来的报文会静默丢字段。
 * 统一忽略未知字段：上游加字段不该把接收方打挂。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BankTypeVO {

    /** 主数据ID（幂等键） */
    private String mdId;
    private String mdCode;
    private String mdDescription;
    /** ACTIVE 启用 / INVALID 停用 */
    private String mdStatusCode;
    /** 银行类别编码 */
    private String code;
    /** 银行类别名称 */
    private String name;
    /** 人行前缀 */
    private String combinecode;
}
