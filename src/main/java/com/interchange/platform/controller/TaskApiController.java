package com.interchange.platform.controller;

import com.interchange.platform.common.BizException;
import com.interchange.platform.common.LoginUser;
import com.interchange.platform.common.LoginUserHolder;
import com.interchange.platform.common.R;
import com.interchange.platform.common.Utils;
import com.interchange.platform.entity.InterfaceTask;
import com.interchange.platform.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务相关接口：保存、启停、立即执行、删除、数据预览、Cron 预览。
 */
@RestController
@RequestMapping("/api/task")
public class TaskApiController {

    private final TaskService taskService;

    public TaskApiController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** 保存任务配置（新增或修改） */
    @PostMapping("/save")
    public R<Map<String, Object>> save(@RequestBody InterfaceTask form) {
        requireWrite();
        InterfaceTask saved = taskService.save(form);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", saved.getId());
        data.put("taskCode", saved.getTaskCode());
        return R.ok("保存成功", data);
    }

    /** 启用 / 停用 */
    @PostMapping("/toggle")
    public R<String> toggle(@RequestParam(value = "id", required = false) Long id,
                            @RequestParam(value = "enabled", defaultValue = "false") boolean enabled) {
        requireWrite();
        if (id == null) {
            throw new BizException(400, "缺少任务 ID");
        }
        taskService.toggle(id, enabled);
        return R.ok(enabled ? "任务已启用" : "任务已停用", null);
    }

    /** 立即执行一次（异步，结果去日志页查看） */
    @PostMapping("/run")
    public R<String> run(@RequestParam(value = "id", required = false) Long id) {
        requireWrite();
        if (id == null) {
            throw new BizException(400, "缺少任务 ID");
        }
        LoginUser user = LoginUserHolder.current();
        taskService.runNow(id, user == null ? "system" : user.getUsername());
        return R.ok("已触发执行，请稍后到执行日志页查看结果", null);
    }

    /** 删除任务（连同历史日志） */
    @PostMapping("/delete")
    public R<String> delete(@RequestParam(value = "id", required = false) Long id) {
        requireWrite();
        if (id == null) {
            throw new BizException(400, "缺少任务 ID");
        }
        taskService.delete(id);
        return R.ok("删除成功", null);
    }

    /** 数据预览：按当前配置取前 N 条数据，用于配置页试跑 */
    @PostMapping("/preview")
    public R<Map<String, Object>> preview(@RequestBody InterfaceTask form,
                                          @RequestParam(defaultValue = "20") int limit) {
        requireWrite();
        return R.ok("预览成功", taskService.previewData(form, limit));
    }

    /** Cron 表达式校验与下次执行时间预览 */
    @PostMapping("/cron-preview")
    public R<Map<String, Object>> cronPreview(
            @RequestParam(value = "cron", required = false) String cron) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (cron == null || cron.trim().isEmpty()) {
            data.put("valid", false);
            data.put("message", "请输入 Cron 表达式");
            return R.ok(data);
        }
        cron = cron.trim();
        if (!Utils.isValidCron(cron)) {
            data.put("valid", false);
            data.put("message", "Cron 表达式非法");
            return R.ok(data);
        }
        data.put("valid", true);
        List<String> next = Utils.nextFireTimes(cron, 5);
        data.put("nextFireTimes", next);
        data.put("summary", Utils.cronSummary(cron));
        return R.ok(data);
    }

    /** 任务详情（含最近 10 次执行） */
    @GetMapping("/detail/{id}")
    public R<InterfaceTask> detail(@PathVariable Long id) {
        return R.ok(taskService.get(id));
    }

    /** 可用的数据源下拉项 */
    @GetMapping("/datasources")
    public R<List<Map<String, String>>> datasources() {
        return R.ok(taskService.datasourceOptions());
    }

    /** 只读角色不允许写操作 */
    private void requireWrite() {
        LoginUser user = LoginUserHolder.current();
        if (user != null && user.isViewer()) {
            throw new BizException(403, "当前账号为只读角色，无操作权限");
        }
    }
}
