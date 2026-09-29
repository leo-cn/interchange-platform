package com.interchange.platform.standard.exception;

/**
 * @author : liucl
 * @date : 2026-09-29 12:48
 * @desc :
 */
public class BusinessException  extends RuntimeException{

    private final int httpStatus;

    public BusinessException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /** 默认 400（参数/报文错误） */
    public BusinessException(String message) {
        this(400, message);
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
