package com.interchange.platform.dyg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** 银行网点（联行号）视图。 */
@Getter
@Setter
@Entity
@Table(name = "STANDARD_BANKINPUT_VIEW")
public class StandardBankInputView implements Serializable {

    @Id
    private String id;

    /** 联行号 */
    private String bankCode;

    private String bankName;

    private String cityCode;

    private String cityName;

    private String provCode;

    private String provName;

    @Column(name = "updateDate")
    private String updateDate;
}
