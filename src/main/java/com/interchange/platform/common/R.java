package com.interchange.platform.common;

import java.io.Serializable;

/**
 * 统一响应结构。所有 REST 接口（含对外接收接口）都返回该结构。
 */
public class R<T> implements Serializable {

    /** 业务码：0 成功，非 0 失败 */
    private int code;
    private String message;
    private T data;
    /** 链路追踪号，便于日志排查 */
    private String traceId;
    private long timestamp = System.currentTimeMillis();

    public R() {
    }

    public R(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> R<T> ok() {
        return new R<>(0, "成功", null, TraceId.current());
    }

    public static <T> R<T> ok(T data) {
        return new R<>(0, "成功", data, TraceId.current());
    }

    public static <T> R<T> ok(String message, T data) {
        return new R<>(0, message, data, TraceId.current());
    }

    public static <T> R<T> fail(String message) {
        return new R<>(500, message, null, TraceId.current());
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null, TraceId.current());
    }

    /** 当前链路追踪号，异常处理里复用，避免出现两个不同的 traceId */
    public static String traceId() {
        return TraceId.current();
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
