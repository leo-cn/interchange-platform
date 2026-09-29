package com.interchange.platform.standard.exception;

/**
 * 业务异常。
 *
 * <p>抛出后由 {@code GlobalExceptionHandler} 统一转换为标准响应
 * （{@code com.interchange.platform.standard.web.advice.GlobalExceptionHandler}）。
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
