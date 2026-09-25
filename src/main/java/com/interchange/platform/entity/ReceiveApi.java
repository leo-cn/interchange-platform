package com.interchange.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接收接口登记表（服务方视角的接口清单）。
 *
 * <p>平台的接收端点只有一个 {@code POST /api/receive/{apiCode}}，apiCode 是 URL 上的一段。
 * 本表把这些 {@code apiCode} 变成平台上的正式资源：先登记，第三方才调得通。
 *
 * <p>为什么要这张表：
 * <ul>
 *   <li><b>接口有清单</b>：页面上能看到对外开放了哪些接口，不再散落在代码、配置和人的记忆里；</li>
 *   <li><b>拼错不再静默通过</b>：未登记的接口直接按"接口未定义"拒绝，
 *       而不是被 {@code ReceiveService#handleBusiness} 的兜底逻辑原样回执成成功；</li>
 *   <li><b>授权能勾选</b>：接入方的"可调用接口"从手工填字符串变成从这份清单里多选。</li>
 * </ul>
 *
 * <p>本表不存令牌。令牌是 {@link ApiToken}（凭证）+ {@code ServerTokenService}（签发缓存）的事，
 * 本表只回答"有没有这个接口、开没开、要不要校验令牌"。
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
