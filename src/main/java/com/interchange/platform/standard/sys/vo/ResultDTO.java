package com.interchange.platform.standard.sys.vo;

import com.interchange.platform.standard.utils.TraceId;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** 统一响应结构：REST 接口与任务处理结果都用它。resultCode 为 "0" 表示成功。 */
@Getter
@Setter
public class ResultDTO<T> implements Serializable {

    public static final String CODE_OK = "0";
    public static final String CODE_FAIL = "500";

    private String resultCode;
    private String resultMessage;
    private T data;
    /** 链路追踪号 */
    private String traceId;
    private long timestamp = System.currentTimeMillis();

    public ResultDTO() {
    }

    public ResultDTO(String resultCode, String resultMessage, T data, String traceId) {
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> ResultDTO<T> ok() {
        return new ResultDTO<>(CODE_OK, "成功", null, TraceId.current());
    }

    public static <T> ResultDTO<T> ok(T data) {
        return new ResultDTO<>(CODE_OK, "成功", data, TraceId.current());
    }

    public static <T> ResultDTO<T> ok(String resultMessage, T data) {
        return new ResultDTO<>(CODE_OK, resultMessage, data, TraceId.current());
    }

    public static <T> ResultDTO<T> fail(String resultMessage) {
        return new ResultDTO<>(CODE_FAIL, resultMessage, null, TraceId.current());
    }

    public static <T> ResultDTO<T> fail(String resultCode, String resultMessage) {
        return new ResultDTO<>(resultCode, resultMessage, null, TraceId.current());
    }

    /** 失败码直接用 int 传（401/404 等） */
    public static <T> ResultDTO<T> fail(int resultCode, String resultMessage) {
        return new ResultDTO<>(String.valueOf(resultCode), resultMessage, null, TraceId.current());
    }

    /** 是否成功；null 视为未判定 */
    public static boolean isOk(ResultDTO<?> r) {
        return r != null && CODE_OK.equals(r.getResultCode());
    }

    public static String traceId() {
        return TraceId.current();
    }
}
