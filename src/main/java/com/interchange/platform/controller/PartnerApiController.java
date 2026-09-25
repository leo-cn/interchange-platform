package com.interchange.platform.controller;

import com.interchange.platform.common.BizException;
import com.interchange.platform.common.LoginUser;
import com.interchange.platform.common.LoginUserHolder;
import com.interchange.platform.common.R;
import com.interchange.platform.entity.Partner;
import com.interchange.platform.service.PartnerService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 第三方系统（对接方）配置接口。
 */
@RestController
@RequestMapping("/api/partner")
public class PartnerApiController {

    private final PartnerService partnerService;

    public PartnerApiController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    @PostMapping("/save")
    public R<Map<String, Object>> save(@RequestBody Partner form) {
        requireWrite();
        Partner saved = partnerService.save(form);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", saved.getId());
        data.put("partnerCode", saved.getPartnerCode());
        return R.ok("保存成功", data);
    }

    @PostMapping("/toggle")
    public R<String> toggle(@RequestParam(value = "id", required = false) Long id,
                            @RequestParam(value = "enabled", defaultValue = "false") boolean enabled) {
        requireWrite();
        if (id == null) {
            throw new BizException(400, "缺少对接方 ID");
        }
        partnerService.toggle(id, enabled);
        return R.ok(enabled ? "已启用" : "已停用", null);
    }

    @PostMapping("/delete")
    public R<String> delete(@RequestParam(value = "id", required = false) Long id) {
        requireWrite();
        if (id == null) {
            throw new BizException(400, "缺少对接方 ID");
        }
        partnerService.delete(id);
        return R.ok("删除成功", null);
    }

    @GetMapping("/detail/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        Partner partner = partnerService.get(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", partner.getId());
        data.put("partnerCode", partner.getPartnerCode());
        data.put("partnerName", partner.getPartnerName());
        data.put("baseUrl", partner.getBaseUrl());
        data.put("authType", partner.getAuthType());
        data.put("authUser", partner.getAuthUser());
        data.put("authSecret", partnerService.maskSecret(partner));
        data.put("headersJson", partner.getHeadersJson());
        data.put("timeoutMs", partner.getTimeoutMs());
        data.put("status", partner.getStatus());
        data.put("remark", partner.getRemark());
        return R.ok(data);
    }

    private void requireWrite() {
        LoginUser user = LoginUserHolder.current();
        if (user != null && user.isViewer()) {
            throw new BizException(403, "当前账号为只读角色，无操作权限");
        }
    }
}
