package com.interchange.platform.dyg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** 银行账号视图。 */
@Getter
@Setter
@Entity
@Table(name = "STANDARD_BANKACC_VIEW")
public class StandardBankAccView implements Serializable {

    @Id
    private String id;

    private String bankAcc;

    private String accName;

    private String corpCode;

    private String corpName;

    private String curCode;

    private String curName;

    private String bankCode;

    private String bankName;

    /** 是否网银：0 否 / 1 是 */
    private String isOnline;

    /** 有效标志：0 有效 / 1 无效 */
    private String validSign;

    @Column(name = "updateDate")
    private String updateDate;
}
