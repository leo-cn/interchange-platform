package com.interchange.platform.standard.sys.service.receiveService;

import com.interchange.platform.standard.anotation.ReceiveApi;

import java.util.Map;

/** 接收侧业务处理器：单个对外接口的业务逻辑挂靠点，编码用 {@link ReceiveApi} 声明。 */
public interface ReceiveService {

    /** 是否要求鉴权；探活类接口可覆盖为 false */
    default boolean authRequired() {
        return true;
    }

    /** 处理一次接收请求，返回响应 data 部分的业务字段 */
    Map<String, Object> handle(String body, String traceId, Map<String, String> headers);
}
