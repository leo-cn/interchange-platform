package com.interchange.platform.standard.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.interchange.platform.standard.task.core.TaskContext;
import com.interchange.platform.standard.task.core.Task;
import com.interchange.platform.standard.utils.JsonUtil;
import com.interchange.platform.standard.anotation.TaskInfo;
import com.interchange.platform.standard.utils.Crypto;
import com.interchange.platform.standard.sys.entity.Partner;
import com.interchange.platform.standard.sys.service.DataFetcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 出站令牌示例：推送前先向第三方换票，再带 {@code Authorization: Bearer} 推送。
 *
 */
@Component
@TaskInfo(code = TokenExchangeTask.CODE,
        desc = "动态换票：先调第三方 /oauth/token 取 access_token，再带 Authorization: Bearer 推送（带过期缓存）")
public class TokenExchangeTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(TokenExchangeTask.class);

    public static final String CODE = "tokenExchange";

    /** 令牌提前 60 秒过期，留出网络耗时，避免拿到手就过期 */
    private static final long EXPIRE_BUFFER_MS = 60_000L;

    /** 默认换票路径（拼在第三方 baseUrl 后面） */
    private static final String DEFAULT_TOKEN_PATH = "/oauth/token";

    /** 缓存：第三方 ID -> 令牌 + 过期时间 */
    private static final Map<Long, Token> CACHE = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = JsonUtil.getMapper()
            .registerModule(new JavaTimeModule());

    /** 发送前把换来的令牌塞进请求头 */
    @Override
    public void setHeaders(TaskContext ctx, Map<String, String> headers, String body) {
        String token = token(ctx);
        if (token != null && !token.isBlank()) {
            headers.put("Authorization", "Bearer " + token);
        }
    }

    private String token(TaskContext ctx) {
        Partner partner = ctx.getPartner();
        if (partner == null || partner.getId() == null) {
            return null;
        }
        Token cached = CACHE.get(partner.getId());
        if (cached != null && !cached.expired()) {
            return cached.value;
        }
        synchronized (CACHE) {
            // 双检：并发的多次推送只换一次票
            cached = CACHE.get(partner.getId());
            if (cached != null && !cached.expired()) {
                return cached.value;
            }
            Token fresh = requestToken(ctx, partner);
            if (fresh != null) {
                CACHE.put(partner.getId(), fresh);
                log.info("已为第三方[{}]换取新令牌，有效期至 {}", partner.getPartnerCode(), fresh.expireAt);
                return fresh.value;
            }
            // 换票失败：用旧的顶一下（可能已过期，但总比完全不带令牌更容易看出问题）
            return cached == null ? null : cached.value;
        }
    }

    /** 调第三方的换票接口。真实项目按对接文档改这里。 */
    private Token requestToken(TaskContext ctx, Partner partner) {
        String url = tokenUrl(ctx, partner);
        if (url == null) {
            log.warn("第三方[{}]未配置 baseUrl，无法换票", partner.getPartnerCode());
            return null;
        }
        String clientId = partner.getAuthUser() == null ? "" : partner.getAuthUser();
        String clientSecret = Crypto.decrypt(partner.getAuthSecret());
        if (clientSecret == null) {
            clientSecret = "";
        }
        try {
            String form = "grant_type=client_credentials"
                    + "&client_id=" + enc(clientId)
                    + "&client_secret=" + enc(clientSecret);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .header("X-Trace-Id", ctx.getTraceId() == null ? "" : ctx.getTraceId())
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("换票失败 HTTP {}：{}", resp.statusCode(), resp.body());
                return null;
            }
            JsonNode node = mapper.readTree(resp.body());
            // 常见三种返回结构都试一下：平铺 / data 里 / 驼峰
            String value = firstText(node, "access_token", "accessToken", "token");
            if (value == null && node.has("data")) {
                value = firstText(node.get("data"), "access_token", "accessToken", "token");
            }
            if (value == null || value.isBlank()) {
                log.warn("换票响应里没有令牌字段：{}", resp.body());
                return null;
            }
            long expiresIn = 3600;
            JsonNode exp = node.path("expires_in");
            if (exp.isMissingNode()) {
                exp = node.path("expiresIn");
            }
            if (exp.canConvertToLong()) {
                expiresIn = exp.asLong();
            }
            return new Token(value, System.currentTimeMillis() + expiresIn * 1000 - EXPIRE_BUFFER_MS);
        } catch (Exception e) {
            log.warn("换票异常 url={}：{}", url, e.getMessage());
            return null;
        }
    }

    /** 换票地址：任务请求头里的 X-Token-Url 优先，否则 baseUrl + /oauth/token */
    private String tokenUrl(TaskContext ctx, Partner partner) {
        Map<String, String> taskHeaders = DataFetcherService.parseHeaders(
                ctx.getTask() == null ? null : ctx.getTask().getHeadersJson());
        for (Map.Entry<String, String> e : taskHeaders.entrySet()) {
            if ("x-token-url".equalsIgnoreCase(e.getKey()) && e.getValue() != null
                    && !e.getValue().isBlank()) {
                return e.getValue().trim();
            }
        }
        String base = partner.getBaseUrl();
        if (base == null || base.isBlank()) {
            return null;
        }
        return base.trim().replaceAll("/+$", "") + DEFAULT_TOKEN_PATH;
    }

    private static String firstText(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String n : names) {
            JsonNode v = node.get(n);
            if (v != null && !v.isNull()) {
                return v.asText();
            }
        }
        return null;
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    /** 缓存的一张票 */
    private static class Token {
        private final String value;
        private final long expireAt;

        Token(String value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }

        boolean expired() {
            return System.currentTimeMillis() >= expireAt;
        }
    }
}
