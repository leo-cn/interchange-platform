package com.interchange.platform.dyg.entity;

import com.interchange.platform.standard.core.base.IdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 客商银行账号 {@code SYS_EXTERNAL_CORP_BANKACC}（dyg-erp 基线表）。
 *
 * <p>{@code ddl-auto} 必须保持 {@code none}，否则会改到资金系统基表结构。
 */
@Data
@Entity
@Table(name = "SYS_EXTERNAL_CORP_BANKACC")
public class SysExternalCorpBankacc extends IdEntity {

    @Column(name = "EXTERNAL_CORP_ID", length = 32)
    private String externalCorpId;

    @Column(name = "EXTERNAL_ACC", length = 50)
    private String externalAcc;

    @Column(name = "EXTERNAL_ACC_NAME", length = 100)
    private String externalAccName;

    @Column(name = "BANK", length = 100)
    private String bank;

    @Column(name = "BANK_TYPE", length = 30)
    private String bankType;

    @Column(name = "BANK_SOURCECODE", length = 30)
    private String bankSourcecode;

    @Column(name = "CUR_ID", length = 32)
    private String curId;
}
