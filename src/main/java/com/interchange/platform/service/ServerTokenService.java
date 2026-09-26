package com.interchange.platform.service;

import com.interchange.platform.common.BizException;
import com.interchange.platform.entity.ApiToken;
import com.interchange.platform.repository.ApiTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 服务方令牌签发：拿 appKey / appSecret 换一个<b>有时效的 access_token</b>。
 *
 * <p>签出来的令牌放在内存缓存里，带到期时间：
 * <ul>
 *   <li><b>生成</b>：{@code POST /api/oauth/token} 用凭证换票，返回 access_token + expires_in；</li>
 *   <li><b>缓存</b>：令牌只存在于本服务的内存（{@link #CACHE}），不落库；
 *       服务重启后缓存清空，调用方重新换一次票即可（标准 OAuth 客户端模式也是如此）；</li>
 *   <li><b>到期</b>：到点自动失效，校验时按过期处理并顺手清掉；也支持主动注销；</li>
 *   <li><b>校验</b>：请求进来时查缓存，命中且未过期即通过 —— 不查数据库。</li>
 * </ul>
 *
 * <p>接入方凭证（appKey/appSecret）本身存在 {@code api_token} 表，那是长期凭证，
 * 只在换票那一刻查一次库。
 *
 * <p>单实例部署时内存缓存足够；多实例部署把 {@link #CACHE} 换成 Redis 即可，
 * 对外接口（签发 / 校验 / 注销）不用改。
 */
@Service
public class ServerTokenService {

    private static final Logger log = LoggerFactory.getLogger(ServerTokenService.class);

    /** 令牌前缀，便于日志和报文里一眼认出 */
    private static final String PREFIX = "AT-";

    /** 缓存上限，防止被恶意刷爆内存 */
    private static final int MAX_TOKENS = 5000;

    /** 缓存：accessToken -> 令牌信息 */
    private static final Map<String, Ticket> CACHE = new ConcurrentHashMap<>();

    /** 每签发/校验多少次顺手清理一次过期令牌 */
    private static final AtomicInteger TICK = new AtomicInteger();

    private final ApiTokenRepository repository;

    public ServerTokenService(ApiTokenRepository repository) {
        this.repository = repository;
    }

    /** 一次签发的产物 */
    public static class Issue {
        private final String accessToken;
        private final long expiresIn;
        private final LocalDateTime expireAt;

        public Issue(String accessToken, long expiresIn, LocalDateTime expireAt) {
            this.accessToken = accessToken;
            this.expiresIn = expiresIn;
            this.expireAt = expireAt;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public long getExpiresIn() {
            return expiresIn;
        }

        public LocalDateTime getExpireAt() {
            return expireAt;
        }
    }

    /** 缓存里的一张票 */
    public static class Ticket {
        private final String accessToken;
        private final String appKey;
        private final String name;
        private final String allowApiCodes;
        private final long issueAt;
        private final long expireAt;
        private volatile long lastSeenAt;

        public Ticket(String accessToken, String appKey, String name, String allowApiCodes,
                      long issueAt, long expireAt) {
            this.accessToken = accessToken;
            this.appKey = appKey;
            this.name = name;
            this.allowApiCodes = allowApiCodes;
            this.issueAt = issueAt;
            this.expireAt = expireAt;
            this.lastSeenAt = issueAt;
        }

        public boolean expired() {
            return System.currentTimeMillis() >= expireAt;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getAppKey() {
            return appKey;
        }

        public String getName() {
            return name;
        }

        public long getExpireAt() {
            return expireAt;
        }

        /**
         * 到期时间（JSON 输出用，页面「当前有效令牌」的过期列直接取它）。
         * 注意命名必须是 getXxx 前缀，否则 Jackson 不序列化。
         */
        public LocalDateTime getExpireTime() {
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(expireAt),
                    java.time.ZoneId.systemDefault());
        }

        public LocalDateTime getIssueTime() {
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(issueAt),
                    java.time.ZoneId.systemDefault());
        }

        public LocalDateTime getLastSeenTime() {
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(lastSeenAt),
                    java.time.ZoneId.systemDefault());
        }

        /** 这个令牌能不能调某个接口 */
        public boolean allows(String apiCode) {
            if (allowApiCodes == null || allowApiCodes.isBlank()) {
                return true;
            }
            if (apiCode == null) {
                return false;
            }
            for (String part : allowApiCodes.split("[,，;；\\s]+")) {
                if (!part.isBlank() && part.trim().equalsIgnoreCase(apiCode.trim())) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 用凭证换票。
     *
     * @param appKey     客户端标识
     * @param appSecret  客户端密钥
     * @param ttlSeconds 指定的有效期（秒）；为空或非法时用接入方配置的有效期
     */
    public Issue issue(String appKey, String appSecret, Integer ttlSeconds) {
        if (appKey == null || appKey.isBlank()) {
            throw new BizException(400, "缺少 appKey");
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new BizException(400, "缺少 appSecret");
        }
        ApiToken client = repository.findByAppKey(appKey.trim())
                .orElseThrow(() -> new BizException(401, "appKey 不存在"));
        if (client.getStatus() == null || client.getStatus() != 1) {
            throw new BizException(401, "该接入方已停用");
        }
        if (!constantTimeEquals(client.getAppSecret(), appSecret.trim())) {
            throw new BizException(401, "appSecret 不正确");
        }
        cleanupIfNeeded();
        if (CACHE.size() >= MAX_TOKENS) {
            throw new BizException(429, "当前有效令牌过多，请稍后再试");
        }
        int ttl = ttlSeconds != null && ttlSeconds > 0 ? Math.min(ttlSeconds, 86400)
                : Math.min(client.effectiveTtl(), 86400);
        long now = System.currentTimeMillis();
        String token = PREFIX + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        CACHE.put(token, new Ticket(token, client.getAppKey(), client.getName(),
                client.getAllowApiCodes(), now, now + ttl * 1000L));
        client.setLastUsedTime(LocalDateTime.now());
        repository.save(client);
        log.info("接入方[{}]换取新令牌，有效期 {} 秒", client.getName(), ttl);
        return new Issue(token, ttl, LocalDateTime.now().plusSeconds(ttl));
    }

    /**
     * 校验一个访问令牌：命中缓存且未过期才通过。
     * 过期或不存在都返回 {@code null}（调用方据此拒绝请求）。
     */
    public Ticket verify(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        cleanupIfNeeded();
        Ticket t = CACHE.get(accessToken.trim());
        if (t == null) {
            return null;
        }
        if (t.expired()) {
            CACHE.remove(accessToken.trim());
            return null;
        }
        t.lastSeenAt = System.currentTimeMillis();
        return t;
    }

    /** 主动注销（令牌泄露时用），令牌不存在也返回成功 */
    public void revoke(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        CACHE.remove(accessToken.trim());
    }

    /** 注销某个接入方名下的全部令牌（改密钥、停用、删除接入方时用） */
    public int revokeByAppKey(String appKey) {
        if (appKey == null || appKey.isBlank()) {
            return 0;
        }
        int removed = 0;
        for (Map.Entry<String, Ticket> e : CACHE.entrySet()) {
            if (appKey.equalsIgnoreCase(e.getValue().getAppKey())) {
                CACHE.remove(e.getKey());
                removed++;
            }
        }
        return removed;
    }

    /** 当前缓存里的有效令牌（页面可查看） */
    public List<Ticket> activeTokens() {
        List<Ticket> list = new ArrayList<>();
        for (Ticket t : CACHE.values()) {
            if (!t.expired()) {
                list.add(t);
            }
        }
        list.sort((a, b) -> Long.compare(b.issueAt, a.issueAt));
        return list;
    }

    /** 清掉已过期的令牌，返回清理条数 */
    public int cleanup() {
        int removed = 0;
        for (Map.Entry<String, Ticket> e : CACHE.entrySet()) {
            if (e.getValue().expired()) {
                CACHE.remove(e.getKey());
                removed++;
            }
        }
        return removed;
    }

    /** 每隔一段时间顺手清一次，避免每次请求都遍历 */
    private void cleanupIfNeeded() {
        if (TICK.incrementAndGet() % 100 == 0) {
            int removed = cleanup();
            if (removed > 0) {
                log.info("令牌缓存清理过期令牌 {} 条，当前有效 {}", removed, CACHE.size());
            }
        }
    }

    /** 常量时间比较，降低被时序攻击猜出密钥的风险 */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
