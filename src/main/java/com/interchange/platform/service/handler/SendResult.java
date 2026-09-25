package com.interchange.platform.service.handler;

/**
 * 单次调用第三方的结果，交给 {@link TaskHandler#afterSend} 做后处理。
 */
public class SendResult {

    private final boolean success;
    private final int httpStatus;
    private final String requestBody;
    private final String responseBody;
    private final String errorMsg;
    private final long costMs;
    /** 第几条（整批模式固定为 1） */
    private final int index;
    /** 本次执行总条数（整批模式为 1） */
    private final int total;

    public SendResult(boolean success, int httpStatus, String requestBody,
                      String responseBody, String errorMsg, long costMs, int index, int total) {
        this.success = success;
        this.httpStatus = httpStatus;
        this.requestBody = requestBody;
        this.responseBody = responseBody;
        this.errorMsg = errorMsg;
        this.costMs = costMs;
        this.index = index;
        this.total = total;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    /** 成功时为 null */
    public String getErrorMsg() {
        return errorMsg;
    }

    public long getCostMs() {
        return costMs;
    }

    public int getIndex() {
        return index;
    }

    public int getTotal() {
        return total;
    }
}
