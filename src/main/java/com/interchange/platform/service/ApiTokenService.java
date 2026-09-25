package com.interchange.platform.service;

import com.interchange.platform.common.BizException;
import com.interchange.platform.entity.ApiToken;
import com.interchange.platform.repository.ApiTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 接入方凭证（appKey / appSecret）管理。
 *
 * <p>凭证本身长期有效、存在库里，但<b>只是用来换票的</b>：
 * 请求方拿它调 {@code POST /api/oauth/token} 换一个有时效的 access_token，
 * 真正调接口用的是那个 access_token（在缓存里，不落库）。
 */
@Service
public class ApiTokenService {

    private static final Logger log = LoggerFactory.getLogger(ApiTokenService.class);

    private final ApiTokenRepository repository;
    private final ServerTokenService serverTokenService;

    public ApiTokenService(ApiTokenRepository repository, ServerTokenService serverTokenService) {
        this.repository = repository;
        this.serverTokenService = serverTokenService;
    }

    public List<ApiToken> listAll() {
        return repository.findAllByOrderByIdAsc();
    }

    public ApiToken get(Long id) {
        if (id == null) {
            throw new BizException(400, "缺少接入方 ID");
        }
        return repository.findById(id).orElseThrow(() -> new BizException(404, "接入方不存在: " + id));
    }

    /** 新增或更新接入方。appKey 新建后不可改；appSecret 留空表示不改（新建则自动生成） */
    public ApiToken save(ApiToken form) {
        String name = form.getName() == null ? null : form.getName().trim();
        if (name == null || name.isEmpty()) {
            throw new BizException(400, "请填写请求方名称");
        }
        ApiToken target;
        if (form.getId() != null) {
            target = get(form.getId());
        } else {
            target = new ApiToken();
            String key = form.getAppKey() == null ? null : form.getAppKey().trim();
            if (key == null || key.isEmpty()) {
                key = generateKey();
            } else if (repository.findByAppKey(key).isPresent()) {
                throw new BizException(400, "该 appKey 已存在，请换一个");
            }
            target.setAppKey(key);
            target.setAppSecret(generateSecret());
        }
        String oldAllow = target.getAllowApiCodes();
        target.setName(name);
        target.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        target.setAllowApiCodes(form.getAllowApiCodes());
        target.setTtlSeconds(form.getTtlSeconds() == null || form.getTtlSeconds() <= 0
                ? 7200 : form.getTtlSeconds());
        target.setRemark(form.getRemark());
        ApiToken saved = repository.save(target);

        // 关键：令牌签发时就把当时的授权范围写进了票里，只改凭证上的名单，
        // 对方手上那张旧票在到期前照样能用（默认 2 小时）。
        // 收窄授权必须立刻生效 —— 多系统对接时，"把某家系统踢出某个接口"
        // 如果两小时后才生效，等于这段时间是敞开的。
        if (narrowed(oldAllow, saved.getAllowApiCodes())) {
            int n = serverTokenService.revokeByAppKey(saved.getAppKey());
            log.info("接入方[{}]的授权范围已收窄，注销其名下 {} 个有效令牌，对方需重新换票",
                    saved.getName(), n);
        }
        return saved;
    }

    /**
     * 授权范围是不是变窄了：{} 表示不限。
     *
     * <p>变窄的三种情况：不限 → 限定名单；删掉了某些接口；由名单变回不限算放宽，不算收窄。
     */
    private boolean narrowed(String oldAllow, String newAllow) {
        if (oldAllow == null || oldAllow.isBlank()) {
            // 原来不限，现在开始限定 —— 收窄
            return newAllow != null && !newAllow.isBlank();
        }
        if (newAllow == null || newAllow.isBlank()) {
            return false;
        }
        List<String> fresh = split(newAllow);
        for (String code : split(oldAllow)) {
            boolean still = false;
            for (String f : fresh) {
                if (f.equalsIgnoreCase(code)) {
                    still = true;
                    break;
                }
            }
            if (!still) {
                return true;
            }
        }
        return false;
    }

    private static List<String> split(String value) {
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        for (String part : value.split("[,，;；\\s]+")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    /**
     * 重置密钥：换一个新的 appSecret，并<b>立刻注销该接入方已签发的全部令牌</b>
     * —— 凭证换了，旧令牌就不该继续能用。
     */
    public ApiToken resetSecret(Long id) {
        ApiToken token = get(id);
        token.setAppSecret(generateSecret());
        ApiToken saved = repository.save(token);
        serverTokenService.revokeByAppKey(saved.getAppKey());
        return saved;
    }

    public void toggle(Long id, boolean enabled) {
        ApiToken token = get(id);
        token.setStatus(enabled ? 1 : 0);
        repository.save(token);
        if (!enabled) {
            serverTokenService.revokeByAppKey(token.getAppKey());
        }
    }

    public void delete(Long id) {
        ApiToken token = get(id);
        serverTokenService.revokeByAppKey(token.getAppKey());
        repository.delete(token);
    }

    public String generateKey() {
        for (int i = 0; i < 20; i++) {
            String v = "AK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            if (repository.findByAppKey(v).isEmpty()) {
                return v;
            }
        }
        throw new BizException(500, "appKey 生成失败，请重试");
    }

    public String generateSecret() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
