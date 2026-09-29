package com.interchange.platform.dyg.entity;

import com.interchange.platform.standard.core.base.IdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

/**
 * 银行类别 {@code BT_BANK_TYPE}（dyg-erp 基线表）。只映射 MDM 接收用到的列。
 *
 * <p>{@code ddl-auto} 必须保持 {@code none}，否则会改到资金系统基表结构。
 */
@Data
@Entity
@Table(name = "BT_BANK_TYPE")
public class BtBankType extends IdEntity {

    @Column(name = "MD_ID", length = 32)
    private String mdId;

    @Column(name = "BANK_TYPE", length = 30)
    private String bankType;

    @Column(name = "TYPE_NAME", length = 100)
    private String typeName;

    @Column(name = "BANK_PREFIX", length = 10)
    private String bankPrefix;

    @Column(name = "VALID_SIGN", length = 1)
    private String validSign;

    @Column(name = "IS_SYSTEM", length = 1)
    private String isSystem;

    @Column(name = "CREATE_DATE")
    private Date createDate;

    @Column(name = "CREATE_BY", length = 50)
    private String createBy;

    @Column(name = "UPDATE_DATE")
    private Date updateDate;

    @Column(name = "UPDATE_BY", length = 50)
    private String updateBy;
}
