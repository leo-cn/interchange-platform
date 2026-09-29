package com.interchange.platform.standard.task.core;

import com.interchange.platform.standard.sys.entity.InterfaceTask;
import com.interchange.platform.standard.sys.entity.Partner;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/** 一次任务执行的上下文，作为 {@link Task} 各环节的首参传入；跨环节传值用 {@link #attr}。 */
@Getter
@RequiredArgsConstructor
public class TaskContext {

    private final InterfaceTask task;
    private final Partner partner;
    private final String targetUrl;
    private final String traceId;

    /** 环节之间共享的临时数据 */
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    /** 存一个跨环节的值 */
    public TaskContext attr(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    /** 取前一环节存下的值；不存在返回 null */
    @SuppressWarnings("unchecked")
    public <T> T attr(String key) {
        return (T) attributes.get(key);
    }
}
