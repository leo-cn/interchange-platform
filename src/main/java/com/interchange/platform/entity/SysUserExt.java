package com.interchange.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台侧的用户扩展属性。
 *
 * <p>平台<strong>没有自己的用户表</strong>：账号主数据（登录名、姓名、口令、启停）
 * 全部在业务系统的用户表（t6.sys_user）里，本表只挂在业务用户主键上，
 * 存平台专有的东西 —— 角色、接口令牌、平台口令、最后登录时间。
 *
 * <p>这样两边共用一份账号，业务系统零改动，平台也不必把口令塞进业务表。
 * 关联键 {@code ext_id} = 业务库 t6.sys_user.ID（varchar(32) 的 UUID 主键）。
 */
@Data
@Entity
@Table(name = "sys_user_ext",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_ext_ext_id", columnNames = "ext_id"))
public class SysUserExt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务系统用户主键（t6.sys_user.ID） */
    @Column(name = "ext_id", nullable = false, length = 64)
    private String extId;

    /** ADMIN / OPERATOR / VIEWER */
    @Column(name = "role", length = 32)
    private String role = "OPERATOR";

    /**
     * 平台侧的启停：1 启用 0 停用。
     * 只影响能否登录本平台，不回写业务系统，业务账号照常使用。
     */
    @Column(name = "status")
    private Integer status = 1;

    /** 第三方调用本平台接收接口时使用的令牌 */
    @Column(name = "api_token", length = 128)
    private String apiToken;

    /**
     * 平台口令（BCrypt）。
     * 为空表示还没设过，登录时沿用业务系统的口令；一旦在平台改过密码就有值，此后只认它。
     */
    @Column(name = "platform_password", length = 128)
    private String platformPassword;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        if (updateTime == null) {
            updateTime = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
