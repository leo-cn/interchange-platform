package com.interchange.platform.service;

import com.interchange.platform.common.BizException;
import com.interchange.platform.entity.ReceiveApi;
import com.interchange.platform.repository.ReceiveApiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 接收接口登记表（接口清单）的维护与查询。
 *
 * <p><b>为什么查询要过一层缓存</b>：接收接口是别人的调用热路径，
 * 每次请求都回数据库查一次"这个接口存不存在"白白浪费 IO。
 * 这里用一张 60 秒 TTL 的内存表承接读请求，页面上任何变更都立即失效重加载。
 *
 * <p>因此存在最多 60 秒的生效延迟：需要立刻停用某个接口来止损时，
 * 用「停用接入方 / 注销令牌」（那些是实时的）比停用接口更快。
 */
@Service
public class ReceiveApiService {

    private static final Logger log = LoggerFactory.getLogger(ReceiveApiService.class);

    /** 接口编码允许的字符：字母数字 + 短横线 + 下划线，避免有人填出带空格的 URL 段 */
    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private static final long TTL_MS = 60_000L;

    private final ReceiveApiRepository repository;

    private final Map<String, Def> cache = new ConcurrentHashMap<>();
    private volatile long loadedAt = 0L;
    private volatile boolean loaded = false;

    public ReceiveApiService(ReceiveApiRepository repository) {
        this.repository = repository;
    }

    public List<ReceiveApi> listAll() {
        return repository.findAllByOrderByIdAsc();
    }

    /** 页面用的可选清单：只列启用的 */
    public List<ReceiveApi> listEnabled() {
        return repository.findAllByStatusOrderByNameAsc(1);
    }

    public ReceiveApi get(Long id) {
        if (id == null) {
            throw new BizException(400, "缺少接口 ID");
        }
        return repository.findById(id).orElseThrow(() -> new BizException(404, "接口不存在: " + id));
    }

    /**
     * 查已启用的接口（走缓存）。
     *
     * @param apiCode 接口编码，忽略大小写
     * @return 已启用则返回该接口；未登记或已停用返回 empty
     */
    public Optional<Def> findEnabled(String apiCode) {
        if (apiCode == null || apiCode.isBlank()) {
            return Optional.empty();
        }
        ensureLoaded();
        return Optional.ofNullable(cache.get(apiCode.trim().toLowerCase()));
    }

    @Transactional
    public ReceiveApi save(ReceiveApi form) {
        String code = form.getApiCode() == null ? null : form.getApiCode().trim();
        if (code == null || code.isEmpty()) {
            throw new BizException(400, "请填写接口编码");
        }
        if (!CODE.matcher(code).matches()) {
            throw new BizException(400, "接口编码只能包含字母、数字、短横线和下划线，例如 pay-callback");
        }
        String name = form.getName() == null ? null : form.getName().trim();
        if (name == null || name.isEmpty()) {
            throw new BizException(400, "请填写接口名称");
        }
        ReceiveApi target;
        if (form.getId() != null) {
            target = get(form.getId());
        } else {
            target = new ReceiveApi();
            if (repository.findByApiCode(code).isPresent()) {
                throw new BizException(400, "接口编码已存在：" + code);
            }
            target.setApiCode(code);
        }
        if (target.getApiCode() == null || !target.getApiCode().equalsIgnoreCase(code)) {
            // 已存在的记录上改编码，同样要查重
            Optional<ReceiveApi> dup = repository.findByApiCode(code);
            if (dup.isPresent() && !dup.get().getId().equals(target.getId())) {
                throw new BizException(400, "接口编码已存在：" + code);
            }
            target.setApiCode(code);
        }
        target.setName(name);
        target.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        target.setAuthRequired(form.getAuthRequired() == null ? 1 : form.getAuthRequired());
        target.setRemark(form.getRemark());
        ReceiveApi saved = repository.save(target);
        evict();
        return saved;
    }

    @Transactional
    public void toggle(Long id, boolean enabled) {
        ReceiveApi api = get(id);
        api.setStatus(enabled ? 1 : 0);
        repository.save(api);
        evict();
    }

    @Transactional
    public void delete(Long id) {
        ReceiveApi api = get(id);
        repository.delete(api);
        evict();
        log.info("已删除接收接口登记：{}（{}）", api.getApiCode(), api.getName());
    }

    /**
     * 登记接口，已存在则什么都不做。
     *
     * <p>用于启动时补登记内置接口，以及把历史上被调用过、但没登记过的 apiCode 自动补上
     * —— 否则老版本升级上来，所有第三方会集体 401。
     *
     * @return true 表示本次新登记了
     */
    @Transactional
    public boolean registerIfAbsent(String apiCode, String name, String remark) {
        return registerIfAbsent(apiCode, name, remark, true);
    }

    @Transactional
    public boolean registerIfAbsent(String apiCode, String name, String remark, boolean authRequired) {
        if (apiCode == null || apiCode.isBlank()) {
            return false;
        }
        String code = apiCode.trim();
        if (!CODE.matcher(code).matches()) {
            log.warn("接口编码不合法，跳过自动登记：{}", apiCode);
            return false;
        }
        if (repository.findByApiCode(code).isPresent()) {
            return false;
        }
        ReceiveApi api = new ReceiveApi();
        api.setApiCode(code);
        api.setName(name == null || name.isBlank() ? code : name);
        api.setStatus(1);
        api.setAuthRequired(authRequired ? 1 : 0);
        api.setRemark(remark);
        repository.save(api);
        evict();
        log.info("已登记接收接口：{}（{}）", code, api.getName());
        return true;
    }

    /** 页面改了数据，让缓存立刻失效（不等 TTL） */
    public void evict() {
        loaded = false;
        loadedAt = 0L;
        cache.clear();
    }

    /** 把当前库里所有已启用的接口重新装载进缓存 */
    public List<Def> reloadForTest() {
        evict();
        ensureLoaded();
        return new ArrayList<>(cache.values());
    }

    private void ensureLoaded() {
        if (loaded && System.currentTimeMillis() - loadedAt < TTL_MS) {
            return;
        }
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (loaded && now - loadedAt < TTL_MS) {
                return;
            }
            Map<String, Def> fresh = new ConcurrentHashMap<>();
            for (ReceiveApi a : repository.findAllByOrderByIdAsc()) {
                if (a.getApiCode() == null || !a.enabled()) {
                    continue;
                }
                fresh.put(a.getApiCode().trim().toLowerCase(), new Def(a.getApiCode(), a.needAuth()));
            }
            cache.clear();
            cache.putAll(fresh);
            loaded = true;
            loadedAt = now;
        }
    }

    /**
     * 校验时真正需要的两个字段。刻意不缓存实体对象本身：
     * 实体会随 JPA 会话变化，缓存里放快照更安全。
     */
    public static final class Def {
        private final String apiCode;
        private final boolean authRequired;

        Def(String apiCode, boolean authRequired) {
            this.apiCode = apiCode;
            this.authRequired = authRequired;
        }

        public String getApiCode() {
            return apiCode;
        }

        public boolean isAuthRequired() {
            return authRequired;
        }
    }
}
