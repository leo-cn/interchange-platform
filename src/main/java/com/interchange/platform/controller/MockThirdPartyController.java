package com.interchange.platform.controller;

import com.interchange.platform.common.R;
import com.interchange.platform.common.Utils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置「模拟第三方系统」，仅用于联调自测：让平台开箱即可跑通完整推送链路。
 */
@RestController
@RequestMapping("/api/mock/thirdparty")
public class MockThirdPartyController {

    private static final Logger log = LoggerFactory.getLogger(MockThirdPartyController.class);

    /** 演示用令牌，对应内置对接方的认证配置 */
    private static final String EXPECTED_TOKEN = "demo-secret";

    /** 接收平台推送的业务数据 */
    @PostMapping("/order/push")
    public R<Map<String, Object>> push(@RequestBody(required = false) String body,
                                       @RequestHeader(value = "X-Partner-Token", required = false) String token,
                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        if (!EXPECTED_TOKEN.equals(token)) {
            log.warn("模拟第三方拒绝请求：令牌不正确 token={}", token);
            return R.fail(401, "模拟第三方鉴权失败：缺少或错误的 X-Partner-Token");
        }
        int length = body == null ? 0 : body.length();
        log.info("模拟第三方收到推送数据，traceId={}, 报文长度={}", traceId, length);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("received", true);
        data.put("bodyLength", length);
        data.put("traceId", traceId);
        data.put("receiveTime", Utils.format(LocalDateTime.now()));
        data.put("message", "模拟第三方已成功接收数据");
        return R.ok(data);
    }

    /** 心跳探测 */
    @PostMapping("/ping")
    public R<Map<String, Object>> ping(HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pong", true);
        data.put("serverTime", Utils.format(LocalDateTime.now()));
        data.put("clientToken", request.getHeader("X-Partner-Token") == null ? "" : "已携带");
        return R.ok(data);
    }

    /** 便于浏览器直接验证服务是否存活 */
    @GetMapping("/health")
    public R<String> health() {
        return R.ok("模拟第三方系统运行中");
    }
}
