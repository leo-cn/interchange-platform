package com.interchange.platform.service.handler;

import java.util.Map;

/**
 * 接收侧业务处理器：本平台作为<b>服务方</b>对外提供接口时，单个接口的业务逻辑挂靠点。
 *
 * <p>与出向侧的 {@link TaskHandler} 对称：接收侧的"特殊逻辑"不要往
 * {@code ReceiveService.handleBusiness} 里堆 if-else，一个接口写一个实现类。
 *
 * <p>用法三步：
 * <ol>
 *   <li>在「接收接口」页登记 apiCode（决定 URL 是 {@code /api/receive/{apiCode}}）；</li>
 *   <li>写一个 Spring Bean 实现本接口，{@link #apiCode()} 返回该编码；</li>
 *   <li>重启后自动生效——引擎按 apiCode 路由到处理器，没命中处理器的已登记接口走 ack 兜底。</li>
 * </ol>
 *
 * <p>引擎已经处理好的事，处理器<b>不用管</b>：接口是否登记/启用（未登记 401）、
 * 令牌鉴权与可调用范围校验、接收日志与接口日志落库、traceId 生成与回执包装
 * （处理器的返回值会被包进 {@code R.ok("接收成功", data).data}）。
 *
 * <p>业务校验失败抛 {@link BusinessException}，其 httpStatus 会原样透给调用方
 * （如 400 参数错误）；抛其他异常按 500 处理，均不影响日志落库。
 */
public interface ReceiveHandler {

    /** 本处理器负责的接口编码，对应 receive_api.apiCode（匹配不区分大小写） */
    String apiCode();

    /** 处理器用途说明 */
    default String description() {
        return "";
    }

    /**
     * 处理一次接收请求。
     *
     * @param body    报文体原文（GET 请求或空报文时为 null）
     * @param traceId 本次调用的链路 ID，需要异步落库/转发时带上它
     * @param headers 请求头只读快照（键为原始头名）
     * @return 回执 data 部分的业务字段；apiCode/received 两个字段由引擎统一填，无需重复
     */
    Map<String, Object> handle(String body, String traceId, Map<String, String> headers);

    /** 业务失败：接收侧处理器用它表达"校验没过/单据不存在"这类可预期的失败 */
    class BusinessException extends RuntimeException {

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
}
