package com.interchange.platform.common;

import java.util.UUID;

/**
 * 链路追踪号。每个请求 / 每次任务执行生成一个，贯穿平台日志与业务日志。
 */
public final class TraceId {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceId() {
    }

    /** 获取当前线程的 traceId，没有则生成一个 */
    public static String current() {
        String id = HOLDER.get();
        if (id == null) {
            id = generate();
            HOLDER.set(id);
        }
        return id;
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    public static void set(String traceId) {
        HOLDER.set(traceId);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
