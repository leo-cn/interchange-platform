package com.interchange.platform.dyg.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** 客商视图。 */
@Getter
@Setter
@Entity
@Table(name = "standard_externalcorp_view")
public class StandardExternalCorpView implements Serializable {

    @Id
    private String id;

    private String code;

    private String name;

    /** 客商类型 */
    private String externalType;

    /** 业务类型 */
    private String bwType;

    private String status;
}
