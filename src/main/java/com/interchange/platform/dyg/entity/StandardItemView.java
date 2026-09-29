package com.interchange.platform.dyg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** 科目视图。 */
@Getter
@Setter
@Entity
@Table(name = "standard_item_view")
public class StandardItemView implements Serializable {

    @Id
    private String id;

    @Column(name = "item_code")
    private String itemCode;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "updateDate")
    private String updateDate;
}
