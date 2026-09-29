package com.interchange.platform.dyg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

/**
 * 客商 {@code SYS_EXTERNAL_CORP}（dyg-erp 基线表）。只映射 MDM 接收用到的列。
 *
 * <p>{@code ddl-auto} 必须保持 {@code none}，否则会改到资金系统基表结构。
 */
@Data
@Entity
@Table(name = "SYS_EXTERNAL_CORP")
public class SysExternalCorp {

    @Id
    @Column(name = "ID", length = 32)
    private String id;

    @Column(name = "MD_ID", length = 32)
    private String mdId;

    @Column(name = "CODE", length = 50)
    private String code;

    @Column(name = "NAME", length = 200)
    private String name;

    @Column(name = "NAME_EN", length = 200)
    private String nameEn;

    @Column(name = "ABBREVIATE", length = 100)
    private String abbreviate;

    @Column(name = "SOC_CODE", length = 50)
    private String socCode;

    @Column(name = "EXTERNAL_TYPE", length = 2)
    private String externalType;

    @Column(name = "STATUS")
    private Integer status;

    @Column(name = "AUDIT_STATUS")
    private Integer auditStatus;

    @Column(name = "IS_NATIVE")
    private Integer isNative;

    @Column(name = "SOURCE_SYSTEM", length = 50)
    private String sourceSystem;

    @Column(name = "SUPPLIER_ID", length = 32)
    private String supplierId;

    @Column(name = "BW_TYPE", length = 2)
    private String bwType;
}
