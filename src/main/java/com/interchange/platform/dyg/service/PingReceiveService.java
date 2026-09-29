package com.interchange.platform.dyg.service;

import com.interchange.platform.standard.anotation.ReceiveApi;
import com.interchange.platform.standard.sys.service.receiveService.ReceiveService;
import com.interchange.platform.standard.utils.DateUtil;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ping 探针处理器：原来硬编码在 ReceiveService.handleBusiness 里的逻辑，
 * 迁到 SPI 后作为第一个样例——最简单的处理器就长这样。
 */
@Component
@ReceiveApi(code = "ping", desc = "心跳探测：返回 pong 与服务器时间")
public class PingReceiveService implements ReceiveService {

    /** 探活接口不鉴权，否则第三方要先取 token 才能探测连通性 */
    @Override
    public boolean authRequired() {
        return false;
    }

    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pong", true);
        data.put("serverTime", DateUtil.formatDate(DateUtil.getCurDate(), DateUtil.FORMAT_DATETIME));
        return data;
    }
}
