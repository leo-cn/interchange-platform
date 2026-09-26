package com.interchange.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接收接口登记表（服务方视角的接口清单）。
 */
@Data
@Entity
@Table(name = "receive_api",
        uniqueConstraints = @UniqueConstraint(name = "uk_receive_api_code", columnNames = "api_code"))
public class ReceiveApi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 接口编码，对应 URL 上的 {@code /api/receive/{apiCode}}。
     * 不区分大小写（校验时统一转小写比对），全库唯一。
     */
    @Column(name = "api_code", length = 64, nullable = false)
    private String apiCode;

    /** 接口名称，例如"支付结果回执" */
    @Column(name = "name", length = 128, nullable = false)
    private String name;

    /** 1 启用 / 0 停用（停用后第三方调用会被拒绝） */
    @Column(name = "status")
    private Integer status = 1;

    /**
     * 是否需要令牌鉴权：1 需要（默认）/ 0 免鉴权。
     * 免鉴权仅供内网探针类接口使用（如健康检查），对外开放的业务接口请保持需要令牌。
     */
    @Column(name = "auth_required")
    private Integer authRequired = 1;

    @Column(name = "remark", length = 255)
    private String remark;

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
        if (status == null) {
            status = 1;
        }
        if (authRequired == null) {
            authRequired = 1;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }

    public boolean enabled() {
        return status != null && status == 1;
    }

    /** 是否需要校验访问令牌 */
    public boolean needAuth() {
        return authRequired == null || authRequired == 1;
    }
}
