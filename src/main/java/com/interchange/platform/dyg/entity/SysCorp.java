package com.interchange.platform.dyg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

/**
 * 组织机构 {@code SYS_CORP}（dyg-erp 基线表）。只映射 MDM 接收用到的列。
 *
 * <p>{@code ddl-auto} 必须保持 {@code none}，否则会改到资金系统基表结构。
 */
@Data
@Entity
@Table(name = "SYS_CORP")
public class SysCorp {

    @Id
    @Column(name = "ID", length = 32)
    private String id;

    @Column(name = "MD_ID", length = 32)
    private String mdId;

    @Column(name = "CODE", length = 50)
    private String code;

    @Column(name = "NAME", length = 200)
    private String name;

    @Column(name = "SHORT_NAME", length = 100)
    private String shortName;

    @Column(name = "NAME_EN", length = 200)
    private String nameEn;

    @Column(name = "PARENT_MD_ID", length = 32)
    private String parentMdId;

    @Column(name = "CUR_ID", length = 32)
    private String curId;

    @Column(name = "SOC_CODE", length = 50)
    private String socCode;

    @Column(name = "UNIT_ATTRIBUTE", length = 2)
    private String unitAttribute;

    @Column(name = "STATUS")
    private Integer status;

    @Column(name = "GROUP_CODE", length = 50)
    private String groupCode;

    @Column(name = "NET_ID", length = 32)
    private String netId;

    @Column(name = "TYPE")
    private Integer type;

    @Column(name = "LISTED_COMPANY")
    private Integer listedCompany;

    @Column(name = "RAT_GROUP")
    private Integer ratGroup;

    @Column(name = "IS_LIMIT_QUOTA", length = 1)
    private String isLimitQuota;

    @Column(name = "CREATE_TIME")
    private Date createTime;

    @Column(name = "CREATE_BY", length = 50)
    private String createBy;

    @Column(name = "USE_ACCOUNT_CODE", length = 50)
    private String useAccountCode;
}
