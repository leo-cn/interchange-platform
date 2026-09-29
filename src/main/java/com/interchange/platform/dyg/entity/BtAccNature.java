package com.interchange.platform.dyg.entity;

import com.interchange.platform.standard.core.base.IdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

/**
 * 账户性质 {@code BT_ACC_NATURE}（dyg-erp 基线表）。只映射 MDM 接收用到的列。
 *
 * <p>{@code nature_code} 在库里可能是定长 CHAR，用 HQL 比较时请配合 {@code trim}。
 * {@code ddl-auto} 必须保持 {@code none}，否则会改到资金系统基表结构。
 */
@Data
@Entity
@Table(name = "BT_ACC_NATURE")
public class BtAccNature extends IdEntity {

    @Column(name = "NATURE_CODE", length = 30)
    private String natureCode;

    @Column(name = "NATURE_NAME", length = 100)
    private String natureName;

    @Column(name = "VALID_SIGN", length = 1)
    private String validSign;

    @Column(name = "CREATE_DATE")
    private Date createDate;

    @Column(name = "CREATE_BY", length = 50)
    private String createBy;
}
