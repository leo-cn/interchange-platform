package com.interchange.platform.dyg.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** 客商银行账号视图。 */
@Getter
@Setter
@Entity
@Table(name = "standard_externalcorp_acc_view")
public class StandardExternalCorpAccView implements Serializable {

    @Id
    private String id;

    private String code;

    private String name;

    private String externalType;

    /** 客商账号 */
    private String externalAcc;

    private String bankCode;

    private String bankName;
}
