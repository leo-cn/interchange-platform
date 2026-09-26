package com.interchange.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 接收日志。第三方调用平台接口（/api/receive/**）时落库，用于追溯与对账。
 */
@Data
@Entity
@Table(name = "receive_log")
public class ReceiveLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接口编码，即 /api/receive/{apiCode} 中的 apiCode */
    @Column(name = "api_code", length = 64)
    private String apiCode;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "http_method", length = 8)
    private String httpMethod;

    @Column(name = "remote_ip", length = 64)
    private String remoteIp;

    /** 调用方标识（由 Token 反查） */
    @Column(name = "caller", length = 64)
    private String caller;

    @Column(name = "headers", length = 4000)
    private String headers;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "request_body")
    private String requestBody;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "response_body")
    private String responseBody;

    /** SUCCESS / FAIL */
    @Column(name = "status", length = 16)
    private String status;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "error_msg")
    private String errorMsg;

    @Column(name = "cost_ms")
    private Long costMs;

    @Column(name = "receive_time")
    private LocalDateTime receiveTime;

    @PrePersist
    public void prePersist() {
        if (receiveTime == null) {
            receiveTime = LocalDateTime.now();
        }
    }
}
