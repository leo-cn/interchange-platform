package com.interchange.platform.service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单接收业务处理器（order-receive，示例实现）。
 *
 * 对接真实第三方前先和对方确认报文结构，按需调整解析与校验。
 */
@Component
public class OrderReceiveHandler implements ReceiveHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderReceiveHandler.class);

    /** 按项目约定：ObjectMapper 必须 registerModule(JavaTimeModule) 且禁用时间戳 */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public String apiCode() {
        return "order-receive";
    }

    @Override
    public String description() {
        return "订单接收：解析报文、校验单号，业务落库逻辑在此扩展";
    }

    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        // 1) 报文校验
        if (body == null || body.isBlank()) {
            throw new ReceiveHandler.BusinessException(400, "报文为空（请以 JSON 体 POST）");
        }
        Map<?, ?> order;
        try {
            order = mapper.readValue(body, Map.class);
        } catch (Exception e) {
            throw new ReceiveHandler.BusinessException(400, "报文不是合法 JSON");
        }

        // 2) 业务校验
        Object orderNo = order.get("orderNo");
        if (orderNo == null || String.valueOf(orderNo).isBlank()) {
            orderNo = order.get("bizNo");
        }
        if (orderNo == null || String.valueOf(orderNo).isBlank()) {
            throw new ReceiveHandler.BusinessException(400, "缺少订单号（orderNo 或 bizNo 字段）");
        }

        // 3) 业务处理挂靠点：真实项目在这里写落库/转发/通知逻辑。
        //    示例只记一条 INFO，不碰数据库——别把示例行为当成已完成的入库。
        log.info("订单[{}] 已接收（traceId={}，来源 IP 见请求头），待接入真实入库逻辑",
                orderNo, traceId);

        // 4) 组回执
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("bodyLength", body.length());
        data.put("ack", "已接收，报文长度 " + body.length());
        if (order.get("amount") != null) {
            data.put("amount", order.get("amount"));
        }
        return data;
    }
}
