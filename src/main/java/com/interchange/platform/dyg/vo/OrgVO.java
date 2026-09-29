package com.interchange.platform.dyg.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 组织机构接收报文 → SYS_CORP。
 *
 * <p>Jackson 按字段名绑定，<b>不要重命名字段</b>，否则 MDM 推来的报文会静默丢字段。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrgVO {

    private String mdId;
    private String mdCode;
    private String mdDescription;
    /** 组织编码 */
    private String orgCode;
    /** 组织名称 */
    private String orgName;
    /** 所属板块：报文里有，但原系统落库不用，真正落的是 legalParentidIdShow */
    private String ownerUnit;
    /** 父级组织主数据ID */
    private String legalParentidIdShow;
    /** 简称 */
    private String shortname;
    /** 英文名 */
    private String def5;
    /** 是否法人公司 */
    private String orgtype2;
    /** 启用状态 */
    private String enable;
    /** 本位币 CNY / USD */
    private String pkCurrtype;
    /** 纳税人识别号 */
    private String taxpayercode;
    /** 所属集团 */
    private String pkGroup;
}
