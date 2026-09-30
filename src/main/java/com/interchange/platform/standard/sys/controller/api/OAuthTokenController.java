package com.interchange.platform.standard.sys.controller.api;

import com.interchange.platform.standard.sys.vo.ResultDTO;
import com.interchange.platform.standard.utils.DateUtil;
import com.interchange.platform.standard.utils.StringUtil;
import jakarta.annotation.Resource;

import com.interchange.platform.standard.exception.BizException;
import com.interchange.platform.standard.utils.Utils;
import com.interchange.platform.standard.sys.service.auth.ServerTokenService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务方令牌签发接口（免登录，第三方用凭证来换票）。
 */
@RestController
@RequestMapping("/api/oauth")
public class OAuthTokenController {

    @Resource
    private ServerTokenService serverTokenService;

    /** 换票 */
    @PostMapping("/token")
    public ResultDTO<Map<String, Object>> token(
            @RequestParam(value = "appKey", required = false) String appKey,
            @RequestParam(value = "appSecret", required = false) String appSecret,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "expiresIn", required = false) Integer expiresIn) {
        // 兼容 OAuth2 习惯的 client_id / client_secret 命名
        String key = StringUtil.isBlank(appKey) ? clientId : appKey;
        String secret = StringUtil.isBlank(appSecret) ? clientSecret : appSecret;
        if (StringUtil.isBlank(key)) {
            throw new BizException(400, "缺少 appKey（或 client_id）");
        }
        if (StringUtil.isBlank(secret)) {
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
        data.put("expire_at", DateUtil.formatDateTime(issue.getExpireAt()));
        return ResultDTO.ok("签发成功", data);
    }

    /** 注销令牌（令牌泄露时用），令牌不存在也返回成功 */
    @PostMapping("/revoke")
    public ResultDTO<String> revoke(@RequestParam(value = "access_token", required = false) String accessToken,
                            @RequestParam(value = "token", required = false) String token) {
        String t = StringUtil.isBlank(accessToken) ? token : accessToken;
        if (StringUtil.isBlank(t)) {
            throw new BizException(400, "缺少 access_token");
        }
        serverTokenService.revoke(t.trim());
        return ResultDTO.ok("令牌已注销", null);
    }

}
