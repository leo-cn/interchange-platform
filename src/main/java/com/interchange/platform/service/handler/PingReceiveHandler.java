package com.interchange.platform.service.handler;

import com.interchange.platform.common.Utils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ping 探针处理器：原来硬编码在 ReceiveService.handleBusiness 里的逻辑，
 * 迁到 SPI 后作为第一个样例——最简单的处理器就长这样。
 */
@Component
public class PingReceiveHandler implements ReceiveHandler {

    @Override
    public String apiCode() {
        return "ping";
    }

    @Override
    public String description() {
        return "心跳探测：返回 pong 与服务器时间";
    }

    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pong", true);
        data.put("serverTime", Utils.format(LocalDateTime.now()));
        return data;
    }
}
