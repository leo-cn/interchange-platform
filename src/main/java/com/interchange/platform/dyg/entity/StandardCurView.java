package com.interchange.platform.dyg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** 币种视图。 */
@Getter
@Setter
@Entity
@Table(name = "standard_cur_view")
public class StandardCurView implements Serializable {

    @Id
    private String id;

    @Column(name = "cur_code")
    private String curCode;

    @Column(name = "cur_name")
    private String curName;

    @Column(name = "english_code")
    private String englishCode;

    @Column(name = "cur_exrate")
    private String curExrate;

    @Column(name = "valid_sign")
    private String validSign;

    @Column(name = "updateDate")
    private String updateDate;
}
