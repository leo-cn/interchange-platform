package com.interchange.platform.controller;

import com.interchange.platform.common.BizException;
import com.interchange.platform.common.R;
import com.interchange.platform.common.Utils;
import com.interchange.platform.service.ServerTokenService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务方令牌签发接口（免登录，第三方用凭证来换票）。
 *
 * <pre>
 *   POST /api/oauth/token
 *   Content-Type: application/x-www-form-urlencoded
 *   appKey=AK-XXXX&amp;appSecret=XXXX          （也认 client_id / client_secret）
 *
 *   200 {"code":0,"data":{"access_token":"AT-…","token_type":"Bearer",
 *                         "expires_in":7200,"expire_at":"2026-09-25 10:00:00"}}
 * </pre>
 *
 * <p>换到的 access_token 只在服务端缓存里存在（不落库），到期自动失效。
 * 之后每次调 {@code /api/receive/{apiCode}} 在请求头带上它：
 * {@code X-Token: AT-…} 或 {@code Authorization: Bearer AT-…}。
 */
@RestController
@RequestMapping("/api/oauth")
public class OAuthTokenController {

    private final ServerTokenService serverTokenService;

    public OAuthTokenController(ServerTokenService serverTokenService) {
        this.serverTokenService = serverTokenService;
    }

    /** 换票 */
    @PostMapping("/token")
    public R<Map<String, Object>> token(
            @RequestParam(value = "appKey", required = false) String appKey,
            @RequestParam(value = "appSecret", required = false) String appSecret,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "expiresIn", required = false) Integer expiresIn) {
        // 兼容 OAuth2 习惯的 client_id / client_secret 命名
        String key = blank(appKey) ? clientId : appKey;
        String secret = blank(appSecret) ? clientSecret : appSecret;
        if (blank(key)) {
            throw new BizException(400, "缺少 appKey（或 client_id）");
        }
        if (blank(secret)) {
            throw new BizException(400, "缺少 appSecret（或 client_secret）");
        }
        if (grantType != null && !grantType.isBlank()
                && !"client_credentials".equalsIgnoreCase(grantType.trim())) {
            throw new BizException(400, "暂不支持的 grant_type: " + grantType + "（当前支持 client_credentials）");
        }
        ServerTokenService.Issue issue = serverTokenService.issue(key.trim(), secret.trim(), expiresIn);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("access_token", issue.getAccessToken());
        data.put("token_type", "Bearer");
        data.put("expires_in", issue.getExpiresIn());
        data.put("expire_at", Utils.format(issue.getExpireAt()));
        return R.ok("签发成功", data);
    }

    /** 注销令牌（令牌泄露时用），令牌不存在也返回成功 */
    @PostMapping("/revoke")
    public R<String> revoke(@RequestParam(value = "access_token", required = false) String accessToken,
                            @RequestParam(value = "token", required = false) String token) {
        String t = blank(accessToken) ? token : accessToken;
        if (blank(t)) {
            throw new BizException(400, "缺少 access_token");
        }
        serverTokenService.revoke(t.trim());
        return R.ok("令牌已注销", null);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
