package com.interchange.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接入方凭证：作为<b>服务方</b>时，给每个请求方（第三方系统）发的 appKey / appSecret。
 *
 * <p>注意区分两个东西：
 * <ul>
 *   <li><b>本表存的是凭证</b>（appKey + appSecret），长期有效，用来换票；</li>
 *   <li><b>真正访问接口用的 access_token 不落库</b>，由
 *       {@code ServerTokenService} 签发后放内存缓存，带到期时间，过期自动失效。</li>
 * </ul>
 *
 * <p>流程：请求方拿 appKey/appSecret 调 {@code POST /api/oauth/token} 换一个
 * access_token（有时效），之后每次调 {@code /api/receive/**} 在请求头带上它；
 * 平台校验时查缓存，不查数据库。
 */
@Data
@Entity
@Table(name = "api_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_api_token_app_key", columnNames = "app_key"))
public class ApiToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 请求方名称，例如"东阳光 OA" */
    @Column(name = "name", length = 128, nullable = false)
    private String name;

    /** 客户端标识（换票时用） */
    @Column(name = "app_key", length = 64, nullable = false)
    private String appKey;

    /** 客户端密钥（换票时用） */
    @Column(name = "app_secret", length = 128, nullable = false)
    private String appSecret;

    /** 1 启用 / 0 停用 */
    @Column(name = "status")
    private Integer status = 1;

    /**
     * 允许调用的接口编码，逗号分隔（对应 /api/receive/{apiCode}）。
     * 为空表示不限制，该凭证换出的令牌可以调所有接收接口。
     */
    @Column(name = "allow_api_codes", length = 512)
    private String allowApiCodes;

    /** 签发的令牌有效期（秒），默认 2 小时 */
    @Column(name = "ttl_seconds")
    private Integer ttlSeconds = 7200;

    @Column(name = "remark", length = 255)
    private String remark;

    /** 最近一次换票时间 */
    @Column(name = "last_used_time")
    private LocalDateTime lastUsedTime;

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
        if (ttlSeconds == null || ttlSeconds <= 0) {
            ttlSeconds = 7200;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }

    /** 令牌有效期（秒），非法值回退到 2 小时 */
    public int effectiveTtl() {
        return ttlSeconds == null || ttlSeconds <= 0 ? 7200 : ttlSeconds;
    }

    /**
     * 这个凭证换出的令牌能不能调某个接口。
     *
     * @param apiCode 接口编码
     * @return 未配置允许清单时全部放行；配置了就只放行清单里的（忽略大小写）
     */
    public boolean allows(String apiCode) {
        if (allowApiCodes == null || allowApiCodes.isBlank()) {
            return true;
        }
        if (apiCode == null) {
            return false;
        }
        for (String part : allowApiCodes.split("[,，;；\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            if (part.trim().equalsIgnoreCase(apiCode.trim())) {
                return true;
            }
        }
        return false;
    }
}
