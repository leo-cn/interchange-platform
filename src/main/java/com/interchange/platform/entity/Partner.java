package com.interchange.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 第三方系统（对接方）。集中管理地址与认证方式，任务只引用它，避免每个任务重复配置。
 */
@Data
@Entity
@Table(name = "partner",
        uniqueConstraints = @UniqueConstraint(name = "uk_partner_code", columnNames = "partner_code"))
public class Partner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_code", nullable = false, length = 64)
    private String partnerCode;

    @Column(name = "partner_name", nullable = false, length = 128)
    private String partnerName;

    /** 例如 https://api.thirdparty.com */
    @Column(name = "base_url", length = 512)
    private String baseUrl;

    /** NONE / BASIC / BEARER / API_KEY / HEADER */
    @Column(name = "auth_type", length = 32)
    private String authType = "NONE";

    /** BASIC 的用户名 / HEADER 模式的 Header 名 */
    @Column(name = "auth_user", length = 128)
    private String authUser;

    /** 密钥，AES 加密入库 */
    @Column(name = "auth_secret", length = 1024)
    private String authSecret;

    /** 公共请求头，JSON 对象格式 */
    @Column(name = "headers_json", length = 4000)
    private String headersJson;

    @Column(name = "timeout_ms")
    private Integer timeoutMs = 15000;

    /** 1 启用 0 停用 */
    @Column(name = "status")
    private Integer status = 1;

    @Column(name = "remark", length = 512)
    private String remark;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        createTime = LocalDateTime.now();
        updateTime = createTime;
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
