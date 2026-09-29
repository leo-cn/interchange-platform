package com.interchange.platform.dyg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 币种 {@code BT_CURRENCY}（dyg-erp 基线表）。只映射 MDM 接收用到的列。
 *
 * <p>{@code ddl-auto} 必须保持 {@code none}，否则会改到资金系统基表结构。
 */
@Data
@Entity
@Table(name = "BT_CURRENCY")
public class BtCurrency {

    @Id
    @Column(name = "ID", length = 32)
    private String id;

    @Column(name = "ENGLISH_CODE", length = 20)
    private String englishCode;
}
