package com.interchange.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 定时推送任务定义（平台的“接口配置”核心）。
 */
@Data
@Entity
@Table(name = "interface_task",
        uniqueConstraints = @UniqueConstraint(name = "uk_task_code", columnNames = "task_code"))
public class InterfaceTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_code", nullable = false, length = 64)
    private String taskCode;

    @Column(name = "task_name", nullable = false, length = 128)
    private String taskName;

    /** 目标系统 */
    @Column(name = "partner_id")
    private Long partnerId;

    /** 数据类型：SQL（查库取数）/ HTTP_PULL（调本系统接口取数）/ FIXED（固定报文） */
    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType = "SQL";

    /** SQL 模式使用的数据源 key，main = 平台库，或 application.yml 中 extra-datasources 的 key */
    @Column(name = "datasource_key", length = 64)
    private String datasourceKey = "main";

    /** SQL 取数语句（只允许 SELECT / WITH） */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "sql_text")
    private String sqlText;

    /** HTTP_PULL：拉取地址 */
    @Column(name = "pull_url", length = 1024)
    private String pullUrl;

    @Column(name = "pull_method", length = 8)
    private String pullMethod = "GET";

    /** HTTP_PULL：拉取请求头，JSON 对象 */
    @Column(name = "pull_headers", length = 4000)
    private String pullHeaders;

    /** FIXED：固定报文体 */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "fixed_payload")
    private String fixedPayload;

    /** 目标路径，相对 partner.base_url，例如 /api/v1/orders/receive */
    @Column(name = "target_path", length = 512)
    private String targetPath;

    @Column(name = "http_method", length = 8)
    private String httpMethod = "POST";

    @Column(name = "content_type", length = 128)
    private String contentType = "application/json;charset=UTF-8";

    /** 推送模式：BATCH 整批一次推送 / PER_ROW 逐条推送 */
    @Column(name = "push_mode", length = 16)
    private String pushMode = "BATCH";

    /** 请求头覆盖，JSON 对象 */
    @Column(name = "headers_json", length = 4000)
    private String headersJson;

    /**
     * 自定义处理器标识：对应某个 TaskHandler 实现的 code()（填 Spring Bean 名也认）。
     * 留空 = 走引擎默认逻辑（取数 → 默认信封 → POST → 2xx 判成功）。
     * 填了之后，引擎会在取数前/取数后/组装/发送前/判定/发送后回调该处理器，
     * 用于签名加密、字段转换、业务码判成败、回写业务表等特殊需求。
     */
    @Column(name = "handler_bean", length = 64)
    private String handlerBean;

    @Column(name = "cron_expression", length = 64)
    private String cronExpression;

    @Column(name = "timeout_ms")
    private Integer timeoutMs = 15000;

    /** 成功后是否把 SQL 结果标记处理（预留字段，业务侧可自行扩展） */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "remark", length = 512)
    private String remark;

    @Column(name = "last_exec_time")
    private LocalDateTime lastExecTime;

    /** 最近一次执行结果：SUCCESS / FAIL */
    @Column(name = "last_result", length = 32)
    private String lastResult;

    @Column(name = "last_cost_ms")
    private Long lastCostMs;

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
