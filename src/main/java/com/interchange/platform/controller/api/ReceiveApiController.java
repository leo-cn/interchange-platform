package com.interchange.platform.controller.api;

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
 */
@RestController
@RequestMapping("/api/receive")
public class ReceiveApiController {

    private final ReceiveService receiveService;

    public ReceiveApiController(ReceiveService receiveService) {
        this.receiveService = receiveService;
    }

    /**
     * 返回 Object 而不是统一响应 R：
     * 处理器声明 {@code rawBody()} 时（如 MDM 主数据接收），响应体就是它自己返回的 Map，
     * 不再包一层 {@code {code,message,data}}，以便与第三方既定报文格式保持一致。
     */
    @PostMapping("/{apiCode}")
    public Object receivePost(@PathVariable("apiCode") String apiCode,
                              @RequestBody(required = false) String body,
                              HttpServletRequest request) {
        return receiveService.handle(apiCode, "POST", headersToJson(request),
                extractToken(request), body, clientIp(request));
    }

    @GetMapping("/{apiCode}")
    public Object receiveGet(@PathVariable("apiCode") String apiCode,
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
