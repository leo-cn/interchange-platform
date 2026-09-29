package com.interchange.platform.dyg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

/**
 * 银行网点 {@code BT_INPUT_BANK_INFO}（dyg-erp 基线表）。只映射 MDM 接收用到的列。
 *
 * <p>{@code ddl-auto} 必须保持 {@code none}，否则会改到资金系统基表结构。
 */
@Data
@Entity
@Table(name = "BT_INPUT_BANK_INFO")
public class BtInputBankInfo {

    @Id
    @Column(name = "ID", length = 32)
    private String id;

    @Column(name = "MD_ID", length = 32)
    private String mdId;

    @Column(name = "SYS_BANK_CODE", length = 30)
    private String sysBankCode;

    @Column(name = "BANK_NAME", length = 100)
    private String bankName;

    @Column(name = "SHORT_NAME", length = 50)
    private String shortName;

    @Column(name = "BANK_PREFIX", length = 10)
    private String bankPrefix;

    @Column(name = "BANK_CITY_CODE", length = 20)
    private String bankCityCode;

    @Column(name = "VALID_SIGN", length = 1)
    private String validSign;

    @Column(name = "CREATE_DATE")
    private Date createDate;

    @Column(name = "CREATE_BY", length = 50)
    private String createBy;

    @Column(name = "UPDATE_DATE")
    private Date updateDate;

    @Column(name = "UPDATE_BY", length = 50)
    private String updateBy;
}
