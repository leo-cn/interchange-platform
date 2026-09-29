package com.interchange.platform.dyg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

/**
 * 银行账户 {@code BT_BANK_ACC}（dyg-erp 基线表）。
 *
 * <p>只映射 MDM 接收会写到的列，其余业务列不映射 —— 表不属于本平台，
 * 这里仅作为落库载体，不参与任何平台自身的表结构管理。
 *
 * <p><b>注意</b>：本实体一旦存在，Hibernate 便"拥有"该表，{@code ddl-auto} 必须保持
 * {@code none}，否则会去改资金系统基表的结构。
 */
@Data
@Entity
@Table(name = "BT_BANK_ACC")
public class BtBankAcc {

    @Id
    @Column(name = "ID", length = 32)
    private String id;

    @Column(name = "MD_ID", length = 32)
    private String mdId;

    @Column(name = "NAME", length = 100)
    private String name;

    @Column(name = "BANK_ACC", length = 50)
    private String bankAcc;

    @Column(name = "ACC_NAME", length = 100)
    private String accName;

    @Column(name = "CORP_ID", length = 32)
    private String corpId;

    @Column(name = "ACC_TYPE", length = 2)
    private String accType;

    @Column(name = "ATTRIBUTE_ID", length = 32)
    private String attributeId;

    @Column(name = "NATURE_ID", length = 32)
    private String natureId;

    @Column(name = "BANK_TYPE_ID", length = 32)
    private String bankTypeId;

    @Column(name = "BANK_CODE", length = 30)
    private String bankCode;

    @Column(name = "BANK_NAME", length = 100)
    private String bankName;

    @Column(name = "PROV", length = 32)
    private String prov;

    @Column(name = "CITY", length = 32)
    private String city;

    @Column(name = "BIF_CODE", length = 32)
    private String bifCode;

    @Column(name = "REG_DATE")
    private Date regDate;

    @Column(name = "IS_ONLINE", length = 1)
    private String isOnline;

    @Column(name = "VALID_SIGN", length = 1)
    private String validSign;

    @Column(name = "STATUS")
    private Integer status;

    @Column(name = "ELECTRIC_BILL", length = 1)
    private String electricBill;

    @Column(name = "RATES_FLOAT", length = 1)
    private String ratesFloat;

    @Column(name = "INTEREST_CYCLE")
    private Integer interestCycle;

    @Column(name = "IS_CAPITAL_POOL", length = 1)
    private String isCapitalPool;

    @Column(name = "IS_DOMESTIC_BANK", length = 1)
    private String isDomesticBank;

    @Column(name = "IS_LIMIT_QUOTA", length = 1)
    private String isLimitQuota;

    @Column(name = "IS_ONLINE_HANDLE", length = 1)
    private String isOnlineHandle;

    @Column(name = "IS_OFFSHORE_ACCOUNT", length = 1)
    private String isOffshoreAccount;

    @Column(name = "IS_RPA_ESCROW", length = 1)
    private String isRpaEscrow;

    @Column(name = "BASIC_ACCOUNT_SIGN", length = 1)
    private String basicAccountSign;

    @Column(name = "CREATE_DATE")
    private Date createDate;

    @Column(name = "CREATE_BY", length = 50)
    private String createBy;

    @Column(name = "UPDATE_DATE")
    private Date updateDate;

    @Column(name = "UPDATE_BY", length = 50)
    private String updateBy;
}
