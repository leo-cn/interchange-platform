package com.interchange.platform.service.handler;

import com.interchange.platform.entity.InterfaceTask;
import com.interchange.platform.entity.Partner;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次任务执行的上下文，在六个钩子之间透传。
 *
 * <p>处理器如果需要跨钩子传值（例如 beforeFetch 算出的批次号、afterSend 回写要用），
 * 用 {@link #attr(String, Object)} / {@link #attr(String)}，不要自己搞静态变量。
 */
public class TaskContext {

    private final InterfaceTask task;
    private final Partner partner;
    private final String targetUrl;
    private final String traceId;
    private final String triggerType;
    private final LocalDateTime startTime = LocalDateTime.now();

    /** beforeFetch 可以改写取数 SQL（例如补增量条件），null 表示用任务上配置的原 SQL */
    private String sqlOverride;

    /** 钩子之间共享的临时数据 */
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public TaskContext(InterfaceTask task, Partner partner, String targetUrl,
                       String traceId, String triggerType) {
        this.task = task;
        this.partner = partner;
        this.targetUrl = targetUrl;
        this.traceId = traceId;
        this.triggerType = triggerType;
    }

    public InterfaceTask getTask() {
        return task;
    }

    public Partner getPartner() {
        return partner;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public String getTraceId() {
        return traceId;
    }

    /** CRON（定时） / MANUAL（手工） */
    public String getTriggerType() {
        return triggerType;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public boolean isCron() {
        return "CRON".equalsIgnoreCase(triggerType);
    }

    public String getSqlOverride() {
        return sqlOverride;
    }

    /**
     * 覆盖本次执行的取数 SQL。
     * 只允许 SELECT / WITH，引擎会在取数前做只读校验，写操作会直接报错。
     */
    public void setSqlOverride(String sqlOverride) {
        this.sqlOverride = sqlOverride;
    }

    public TaskContext attr(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T attr(String key) {
        return (T) attributes.get(key);
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
