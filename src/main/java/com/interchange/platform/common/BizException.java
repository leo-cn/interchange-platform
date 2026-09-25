package com.interchange.platform.common;

/**
 * 业务异常。抛出后由 {@link GlobalExceptionHandler} 统一转换为标准响应。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(500, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }

    public int getCode() {
        return code;
    }
}
