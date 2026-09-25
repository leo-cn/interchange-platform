package com.interchange.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志。每一次触发（自动或手工）落一条，含完整请求/响应报文，支持查询与下载。
 */
@Data
@Entity
@Table(name = "task_log",
        indexes = {
                @Index(name = "idx_log_task_code", columnList = "task_code"),
                @Index(name = "idx_log_start_time", columnList = "start_time"),
                @Index(name = "idx_log_status", columnList = "status")
        })
public class TaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "task_code", length = 64)
    private String taskCode;

    @Column(name = "task_name", length = 128)
    private String taskName;

    @Column(name = "partner_name", length = 128)
    private String partnerName;

    /** 链路追踪号 */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    /** 触发方式：CRON 自动 / MANUAL 手工 */
    @Column(name = "trigger_type", length = 16)
    private String triggerType;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "cost_ms")
    private Long costMs;

    /** SUCCESS 成功 / FAIL 失败 / PARTIAL 部分成功 */
    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "total_count")
    private Integer totalCount = 0;

    @Column(name = "success_count")
    private Integer successCount = 0;

    @Column(name = "fail_count")
    private Integer failCount = 0;

    /** 目标地址 */
    @Column(name = "target_url", length = 1024)
    private String targetUrl;

    /** 推送报文体 */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "request_body")
    private String requestBody;

    /** 第三方返回报文体 */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "response_body")
    private String responseBody;

    /** 异常信息 */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "error_msg")
    private String errorMsg;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
