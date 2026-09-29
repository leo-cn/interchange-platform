package com.interchange.platform.dyg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** 用户视图。 */
@Getter
@Setter
@Entity
@Table(name = "STANDARD_USER_VIEW")
public class StandardUserView implements Serializable {

    @Id
    private String id;

    private String loginName;

    private String username;

    private String corpCode;

    private String corpName;

    /** 1 启用（默认） / 0 禁用 */
    private String status;

    @Column(name = "updateDate")
    private String updateDate;
}
