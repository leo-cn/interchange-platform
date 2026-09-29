package com.interchange.platform.dyg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** 省市区域（就是基表 SYS_REGION，不是视图）。 */
@Getter
@Setter
@Entity
@Table(name = "SYS_REGION")
public class StandardRegionView implements Serializable {

    @Id
    private String id;

    @Column(name = "bank_input_city")
    private String bankInputCity;

    private String name;

    @Column(name = "bank_prov")
    private String bankProv;

    @Column(name = "prov_input_name")
    private String provInputName;
}
