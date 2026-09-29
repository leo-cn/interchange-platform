package com.interchange.platform.standard.sys.controller.api;

import com.interchange.platform.standard.sys.vo.ResultDTO;

import com.interchange.platform.standard.utils.TraceId;
import com.interchange.platform.standard.utils.Utils;
import com.interchange.platform.standard.sys.service.receiveService.ReceiveDispatchService;
import jakarta.annotation.Resource;
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

    @Resource
    private ReceiveDispatchService receiveService;

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
    public ResultDTO<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("application", "interchange-platform");
        data.put("time", Utils.format(java.time.LocalDateTime.now()));
        ResultDTO<Map<String, Object>> r = ResultDTO.ok(data);
        r.setTraceId(TraceId.current());
        return r;
    }
}
