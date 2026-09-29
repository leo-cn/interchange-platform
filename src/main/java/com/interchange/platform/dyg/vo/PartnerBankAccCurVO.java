package com.interchange.platform.dyg.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 客商账号的币种明细接收报文。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PartnerBankAccCurVO {

    /** 币种代码，对应 BT_CURRENCY.english_code */
    private String pkCurrtypeCodeShow;
}
