package com.interchange.platform.controller;

import com.interchange.platform.common.BizException;
import com.interchange.platform.common.LoginUser;
import com.interchange.platform.common.LoginUserHolder;
import com.interchange.platform.common.R;
import com.interchange.platform.entity.ApiToken;
import com.interchange.platform.service.ApiTokenService;
import com.interchange.platform.service.ServerTokenService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接入方凭证（appKey / appSecret）管理接口。
 */
@RestController
@RequestMapping("/api/token")
public class ApiTokenController {

    private final ApiTokenService tokenService;
    private final ServerTokenService serverTokenService;

    public ApiTokenController(ApiTokenService tokenService, ServerTokenService serverTokenService) {
        this.tokenService = tokenService;
        this.serverTokenService = serverTokenService;
    }

    @PostMapping("/save")
    public R<Map<String, Object>> save(@RequestBody ApiToken form) {
        requireWrite();
        ApiToken saved = tokenService.save(form);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", saved.getId());
        data.put("name", saved.getName());
        data.put("appKey", saved.getAppKey());
        data.put("appSecret", saved.getAppSecret());
        return R.ok("保存成功", data);
    }

    @PostMapping("/toggle")
    public R<String> toggle(@RequestParam(value = "id", required = false) Long id,
                            @RequestParam(value = "enabled", defaultValue = "false") boolean enabled) {
        requireWrite();
        if (id == null) {
            throw new BizException(400, "缺少接入方 ID");
        }
        tokenService.toggle(id, enabled);
        return R.ok(enabled ? "已启用" : "已停用（该接入方已签发的令牌一并注销）", null);
    }

    /** 重置密钥：换一个新 appSecret，并注销该接入方名下所有已签发令牌 */
    @PostMapping("/reset-secret")
    public R<Map<String, Object>> resetSecret(@RequestParam(value = "id", required = false) Long id) {
        requireWrite();
        ApiToken saved = tokenService.resetSecret(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", saved.getId());
        data.put("appSecret", saved.getAppSecret());
        return R.ok("密钥已重置，旧令牌全部失效", data);
    }

    @PostMapping("/delete")
    public R<String> delete(@RequestParam(value = "id", required = false) Long id) {
        requireWrite();
        if (id == null) {
            throw new BizException(400, "缺少接入方 ID");
        }
        tokenService.delete(id);
        return R.ok("删除成功", null);
    }

    /** 页面内换票（联试用）：拿选中的凭证换一个 access_token */
    @PostMapping("/issue")
    public R<Map<String, Object>> issue(@RequestParam(value = "id", required = false) Long id,
                                        @RequestParam(value = "expiresIn", required = false) Integer expiresIn) {
        requireWrite();
        ApiToken client = tokenService.get(id);
        ServerTokenService.Issue issue = serverTokenService.issue(
                client.getAppKey(), client.getAppSecret(), expiresIn);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("access_token", issue.getAccessToken());
        data.put("token_type", "Bearer");
        data.put("expires_in", issue.getExpiresIn());
        data.put("expire_at", issue.getExpireAt() == null ? null
                : issue.getExpireAt().toString().replace('T', ' ').substring(0, 19));
        return R.ok("令牌已签发", data);
    }

    /** 当前缓存里有效的令牌（页面可查看，重启后清空） */
    @GetMapping("/active")
    public R<Map<String, Object>> active() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tokens", serverTokenService.activeTokens());
        data.put("total", serverTokenService.activeTokens().size());
        return R.ok(data);
    }

    @GetMapping("/detail/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        ApiToken t = tokenService.get(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", t.getId());
        data.put("name", t.getName());
        data.put("appKey", t.getAppKey());
        data.put("appSecret", t.getAppSecret());
        data.put("status", t.getStatus());
        data.put("allowApiCodes", t.getAllowApiCodes());
        data.put("ttlSeconds", t.getTtlSeconds());
        data.put("remark", t.getRemark());
        return R.ok(data);
    }

    private void requireWrite() {
        LoginUser user = LoginUserHolder.current();
        if (user != null && user.isViewer()) {
            throw new BizException(403, "当前账号为只读角色，无操作权限");
        }
    }
}
