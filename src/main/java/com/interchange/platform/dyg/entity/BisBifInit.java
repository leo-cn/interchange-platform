package com.interchange.platform.dyg.entity;

import com.interchange.platform.standard.core.base.IdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

/**
 * 接口初始化 {@code BIS_BIF_INIT}（dyg-erp 基线表）。只映射 MDM 接收用到的列。
 *
 * <p>{@code ddl-auto} 必须保持 {@code none}，否则会改到资金系统基表结构。
 */
@Data
@Entity
@Table(name = "BIS_BIF_INIT")
public class BisBifInit extends IdEntity {

    @Column(name = "BIF_CODE", length = 50)
    private String bifCode;

    @Column(name = "NAME", length = 100)
    private String name;

    @Column(name = "BANK_TYPE_ID", length = 32)
    private String bankTypeId;

    @Column(name = "VALID_SIGN", length = 1)
    private String validSign;

    @Column(name = "IS_SYSTEM", length = 1)
    private String isSystem;

    @Column(name = "CREATE_DATE")
    private Date createDate;

    @Column(name = "CREATE_BY", length = 50)
    private String createBy;
}
