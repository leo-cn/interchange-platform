package com.interchange.platform.mdm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 主数据接收报文结构。字段与小驼峰命名严格对齐 dyg-erp 的 DTO。
 *
 * <p>Jackson 按字段名绑定，<b>不要重命名字段</b>，否则 MDM 推来的报文会静默丢字段。
 * 统一忽略未知字段：上游加字段不该把接收方打挂。
 */
public final class MdmDto {

    private MdmDto() {
    }

    /** 银行类别 → BT_BANK_TYPE */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BankType {
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

    /** 银行网点 → BT_INPUT_BANK_INFO */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BankBranch {
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

    /** 组织机构 → SYS_CORP */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Org {
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

    /** 银行账户 → BT_BANK_ACC */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BankAcc {
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
        private List<BankAccCur> bdBankaccsub;
    }

    /** 银行账户的币种明细 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BankAccCur {
        private String id;
        /** 币种代码，对应 BT_CURRENCY.english_code */
        private String pkCurrtypeCodeShow;
    }

    /** 客商 → SYS_EXTERNAL_CORP */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Partner {
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
        private List<PartnerBankAcc> bdBankaccbas;
    }

    /** 客商账号 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PartnerBankAcc {
        private String id;
        /** 银行账号 */
        private String accnum01;
        /** 户名 */
        private String accname;
        /** 银行网点 mdId */
        private String pkBankdoc;
        /** 银行类别 mdId */
        private String pkBankdocBanktypeShow;
        /** 币种列表 */
        private List<PartnerBankAccCur> bankaccsub;
    }

    /** 客商账号的币种明细 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PartnerBankAccCur {
        /** 币种代码，对应 BT_CURRENCY.english_code */
        private String pkCurrtypeCodeShow;
    }
}
