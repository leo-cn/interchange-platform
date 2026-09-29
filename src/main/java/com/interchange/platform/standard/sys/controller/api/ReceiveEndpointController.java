package com.interchange.platform.standard.sys.controller.api;

import com.interchange.platform.standard.sys.vo.ResultDTO;
import jakarta.annotation.Resource;

import com.interchange.platform.standard.exception.BizException;
import com.interchange.platform.standard.session.LoginUser;
import com.interchange.platform.standard.session.LoginUserHolder;
import com.interchange.platform.standard.sys.entity.ReceiveApi;
import com.interchange.platform.standard.sys.service.receiveService.ReceiveApiService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接收接口清单（登记表）的管理端接口：先登记，第三方才调得通 {@code /api/receive/{apiCode}}。
 */
@RestController
@RequestMapping("/api/receive-api")
public class ReceiveEndpointController {

    @Resource
    private ReceiveApiService apiService;

    /** 页面上给接入方勾选「可调用接口」时用 */
    @GetMapping("/list")
    public ResultDTO<Map<String, Object>> list() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apis", apiService.listAll());
        return ResultDTO.ok(data);
    }

    @PostMapping("/save")
    public ResultDTO<Map<String, Object>> save(@RequestBody ReceiveApi form) {
        requireWrite();
        ReceiveApi saved = apiService.save(form);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", saved.getId());
        data.put("apiCode", saved.getApiCode());
        data.put("name", saved.getName());
        return ResultDTO.ok("保存成功", data);
    }

    @PostMapping("/toggle")
    public ResultDTO<String> toggle(@RequestParam(value = "id", required = false) Long id,
                            @RequestParam(value = "enabled", defaultValue = "false") boolean enabled) {
        requireWrite();
        if (id == null) {
            throw new BizException(400, "缺少接口 ID");
        }
        apiService.toggle(id, enabled);
        return ResultDTO.ok(enabled ? "已启用" : "已停用，第三方调用将被拒绝", null);
    }

    @PostMapping("/delete")
    public ResultDTO<String> delete(@RequestParam(value = "id", required = false) Long id) {
        requireWrite();
        if (id == null) {
            throw new BizException(400, "缺少接口 ID");
        }
        apiService.delete(id);
        return ResultDTO.ok("删除成功", null);
    }

    @GetMapping("/detail/{id}")
    public ResultDTO<Map<String, Object>> detail(@PathVariable Long id) {
        ReceiveApi a = apiService.get(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", a.getId());
        data.put("apiCode", a.getApiCode());
        data.put("name", a.getName());
        data.put("status", a.getStatus());
        data.put("authRequired", a.getAuthRequired());
        data.put("remark", a.getRemark());
        return ResultDTO.ok(data);
    }

    private void requireWrite() {
        LoginUser user = LoginUserHolder.current();
        if (user != null && user.isViewer()) {
            throw new BizException(403, "当前账号为只读角色，无操作权限");
        }
    }
}
