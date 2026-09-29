package com.interchange.platform.dyg.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 客商账号接收报文。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PartnerBankAccVO {

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
    private List<PartnerBankAccCurVO> bankaccsub;
}
