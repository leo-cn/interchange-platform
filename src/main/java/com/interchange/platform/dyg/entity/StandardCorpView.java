package com.interchange.platform.dyg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** 单位视图。 */
@Getter
@Setter
@Entity
@Table(name = "standard_corp_view")
public class StandardCorpView implements Serializable {

    @Id
    private String code;

    private String name;

    /** 状态：0 停用 / 1 启用 */
    private String status;

    @Column(name = "updateDate")
    private String updateDate;
}
