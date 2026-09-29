package com.interchange.platform.dyg.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 客商接收报文 → SYS_EXTERNAL_CORP。
 *
 * <p>Jackson 按字段名绑定，<b>不要重命名字段</b>，否则 MDM 推来的报文会静默丢字段。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PartnerVO {

    private String mdId;
    private String mdCode;
    private String mdDescription;
    /** SUP 供应商 / CUS 客户 / BP 客商 */
    private String bptype;
    /** 客商编号 */
    private String code;
    /** 客商名称 */
    private String name;
    /** 英文名称 */
    private String ename;
    /** 简称 */
    private String shortname;
    /** 统一社会信用代码 */
    private String unifiedSocialCode;
    /** 客商账号列表 */
    private List<PartnerBankAccVO> bdBankaccbas;
}
