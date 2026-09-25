package com.interchange.platform.controller;

import com.interchange.platform.common.BizException;
import com.interchange.platform.common.LoginUser;
import com.interchange.platform.common.LoginUserHolder;
import com.interchange.platform.common.R;
import com.interchange.platform.entity.ReceiveApi;
import com.interchange.platform.service.ReceiveApiService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接收接口清单（登记表）的管理端接口：先登记，第三方才调得通 {@code /api/receive/{apiCode}}。
 *
 * <p>注意别和 {@link ReceiveApiController} 搞混：那个是<b>运行时</b>接收端点
 * （{@code /api/receive/**}），这个是<b>管理端</b>的接口清单维护（{@code /api/receive-api/**}）。
 *
 * <p>登记项本身不含任何令牌信息 —— 令牌在「接入方」那侧（凭证 + 签发缓存）。
 */
@RestController
@RequestMapping("/api/receive-api")
public class ReceiveEndpointController {

    private final ReceiveApiService apiService;

    public ReceiveEndpointController(ReceiveApiService apiService) {
        this.apiService = apiService;
    }

    /** 页面上给接入方勾选「可调用接口」时用 */
    @GetMapping("/list")
    public R<Map<String, Object>> list() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apis", apiService.listAll());
        return R.ok(data);
    }

    @PostMapping("/save")
    public R<Map<String, Object>> save(@RequestBody ReceiveApi form) {
        requireWrite();
        ReceiveApi saved = apiService.save(form);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", saved.getId());
        data.put("apiCode", saved.getApiCode());
        data.put("name", saved.getName());
        return R.ok("保存成功", data);
    }

    @PostMapping("/toggle")
    public R<String> toggle(@RequestParam(value = "id", required = false) Long id,
                            @RequestParam(value = "enabled", defaultValue = "false") boolean enabled) {
        requireWrite();
        if (id == null) {
            throw new BizException(400, "缺少接口 ID");
        }
        apiService.toggle(id, enabled);
        return R.ok(enabled ? "已启用" : "已停用，第三方调用将被拒绝", null);
    }

    @PostMapping("/delete")
    public R<String> delete(@RequestParam(value = "id", required = false) Long id) {
        requireWrite();
        if (id == null) {
            throw new BizException(400, "缺少接口 ID");
        }
        apiService.delete(id);
        return R.ok("删除成功", null);
    }

    @GetMapping("/detail/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        ReceiveApi a = apiService.get(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", a.getId());
        data.put("apiCode", a.getApiCode());
        data.put("name", a.getName());
        data.put("status", a.getStatus());
        data.put("authRequired", a.getAuthRequired());
        data.put("remark", a.getRemark());
        return R.ok(data);
    }

    private void requireWrite() {
        LoginUser user = LoginUserHolder.current();
        if (user != null && user.isViewer()) {
            throw new BizException(403, "当前账号为只读角色，无操作权限");
        }
    }
}
