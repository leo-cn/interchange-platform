package com.interchange.platform.dyg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 银行账户币种 {@code BT_BANK_ACC_CUR}（dyg-erp 基线表）。
 *
 * <p>{@code ddl-auto} 必须保持 {@code none}，否则会改到资金系统基表结构。
 */
@Data
@Entity
@Table(name = "BT_BANK_ACC_CUR")
public class BtBankAccCur {

    @Id
    @Column(name = "ID", length = 32)
    private String id;

    @Column(name = "BANK_ACC_ID", length = 32)
    private String bankAccId;

    @Column(name = "CUR_ID", length = 32)
    private String curId;
}
