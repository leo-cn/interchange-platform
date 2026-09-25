package com.interchange.platform.controller;

import com.interchange.platform.common.R;
import com.interchange.platform.common.TraceId;
import com.interchange.platform.common.Utils;
import com.interchange.platform.service.ReceiveService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接收侧对外接口。
 *
 * <p>第三方系统调用：{@code POST /api/receive/{apiCode}}，请求头携带 {@code X-Token: <令牌>}。
 * 返回统一的 {@code {code,message,data,traceId}} 结构，并落地接收日志。
 */
@RestController
@RequestMapping("/api/receive")
public class ReceiveApiController {

    private final ReceiveService receiveService;

    public ReceiveApiController(ReceiveService receiveService) {
        this.receiveService = receiveService;
    }

    @PostMapping("/{apiCode}")
    public R<Map<String, Object>> receivePost(@PathVariable("apiCode") String apiCode,
                                              @RequestBody(required = false) String body,
                                              HttpServletRequest request) {
        return receiveService.handle(apiCode, "POST", headersToJson(request),
                extractToken(request), body, clientIp(request));
    }

    @GetMapping("/{apiCode}")
    public R<Map<String, Object>> receiveGet(@PathVariable("apiCode") String apiCode,
                                             HttpServletRequest request) {
        return receiveService.handle(apiCode, "GET", headersToJson(request),
                extractToken(request), null, clientIp(request));
    }

    /** 令牌提取：X-Token → token 参数 → Authorization */
    private String extractToken(HttpServletRequest request) {
        String token = request.getHeader("X-Token");
        if (token == null || token.isBlank()) {
            token = request.getParameter("token");
        }
        if (token == null || token.isBlank()) {
            token = request.getHeader("Authorization");
        }
        return token;
    }

    private String headersToJson(HttpServletRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            map.put(name, request.getHeader(name));
        }
        return Utils.toJson(map);
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /** 平台健康检查，供监控探活 */
    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("application", "interchange-platform");
        data.put("time", Utils.format(java.time.LocalDateTime.now()));
        R<Map<String, Object>> r = R.ok(data);
        r.setTraceId(TraceId.current());
        return r;
    }
}
