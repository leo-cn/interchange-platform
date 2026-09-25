package com.interchange.platform.controller;

import com.interchange.platform.common.LoginUserHolder;
import com.interchange.platform.common.Menus;
import com.interchange.platform.common.Utils;
import com.interchange.platform.entity.InterfaceTask;
import com.interchange.platform.entity.TaskLog;
import com.interchange.platform.service.ApiTokenService;
import com.interchange.platform.service.DashboardService;
import com.interchange.platform.service.InterfaceLogService;
import com.interchange.platform.service.LogService;
import com.interchange.platform.service.LogStore;
import com.interchange.platform.service.PartnerService;
import com.interchange.platform.service.ReceiveApiService;
import com.interchange.platform.service.TaskService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台页面路由。所有页面都在登录态下访问。
 */
@Controller
public class PageController {

    private final DashboardService dashboardService;
    private final TaskService taskService;
    private final LogService logService;
    private final PartnerService partnerService;
    private final InterfaceLogService interfaceLogService;
    private final com.interchange.platform.service.AuthService authService;
    private final LogStore logStore;
    private final ApiTokenService apiTokenService;
    private final ReceiveApiService receiveApiService;

    public PageController(DashboardService dashboardService,
                          TaskService taskService,
                          LogService logService,
                          PartnerService partnerService,
                          InterfaceLogService interfaceLogService,
                          com.interchange.platform.service.AuthService authService,
                          LogStore logStore,
                          ApiTokenService apiTokenService,
                          ReceiveApiService receiveApiService) {
        this.dashboardService = dashboardService;
        this.taskService = taskService;
        this.logService = logService;
        this.partnerService = partnerService;
        this.interfaceLogService = interfaceLogService;
        this.authService = authService;
        this.logStore = logStore;
        this.apiTokenService = apiTokenService;
        this.receiveApiService = receiveApiService;
    }

    /**
     * 日志表是否只保留最新一条。日志页据此提示"只看到最后一次执行"，
     * 免得被误认为是查询出了 bug。
     */
    @ModelAttribute("keepLatestOnly")
    public boolean keepLatestOnly() {
        return logStore.keepLatestOnly();
    }

    /**
     * 分组菜单：外壳页（shell.html）与内页侧边栏（fragments/layout.html）共用，
     * 数据源是 {@link Menus#ALL}，保证两处渲染一致。
     */
    @ModelAttribute("menuGroups")
    public Map<String, List<Menus.Item>> menuGroups() {
        Map<String, List<Menus.Item>> grouped = new LinkedHashMap<>();
        for (Menus.Item it : Menus.ALL) {
            grouped.computeIfAbsent(it.getGroup(), k -> new ArrayList<>()).add(it);
        }
        return grouped;
    }

    /** 外壳页：左侧菜单 + 右侧多标签页容器。所有功能页都在外壳的 iframe 中打开。 */
    @GetMapping("/")
    public String shell() {
        return "shell";
    }

    /** 运行看板（外壳页默认打开的第一个标签页） */
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("menu", "index");
        model.addAttribute("stats", dashboardService.stats());
        model.addAttribute("recentLogs", dashboardService.recentLogs());

        List<InterfaceTask> tasks = dashboardService.enabledTasks();
        Map<String, String> nextFireMap = new LinkedHashMap<>();
        for (InterfaceTask task : tasks) {
            try {
                List<String> next = Utils.nextFireTimes(task.getCronExpression(), 1);
                nextFireMap.put(task.getTaskCode(), next.isEmpty() ? "-" : next.get(0));
            } catch (Exception e) {
                nextFireMap.put(task.getTaskCode(), "-");
            }
        }
        model.addAttribute("enabledTasks", tasks);
        model.addAttribute("nextFireMap", nextFireMap);
        return "index";
    }

    /** 定时任务列表 */
    @GetMapping("/task/list")
    public String taskList(@RequestParam(value = "keyword", required = false) String keyword,
                           @RequestParam(value = "partnerId", required = false) Long partnerId,
                           @RequestParam(value = "enabled", required = false) Boolean enabled,
                           @RequestParam(value = "page", defaultValue = "1") int page,
                           @RequestParam(value = "size", defaultValue = "15") int size,
                           Model model) {
        Page<InterfaceTask> result = taskService.page(keyword, partnerId, enabled, page, size);
        model.addAttribute("menu", "task");
        model.addAttribute("page", result);
        model.addAttribute("keyword", keyword);
        model.addAttribute("partnerId", partnerId);
        model.addAttribute("enabled", enabled);
        model.addAttribute("partners", partnerService.listAll());
        model.addAttribute("datasourceOptions", taskService.datasourceOptions());
        return "task-list";
    }

    /** 任务配置（新增 / 编辑） */
    @GetMapping("/task/edit")
    public String taskEdit(@RequestParam(value = "id", required = false) Long id, Model model) {
        InterfaceTask task;
        if (id == null) {
            task = new InterfaceTask();
            task.setSourceType("SQL");
            task.setDatasourceKey("main");
            task.setHttpMethod("POST");
            task.setPushMode("BATCH");
            task.setPullMethod("GET");
            task.setContentType("application/json;charset=UTF-8");
            task.setCronExpression("0 0/5 * * * ?");
            task.setEnabled(Boolean.TRUE);
            task.setTimeoutMs(15000);
        } else {
            task = taskService.get(id);
        }
        model.addAttribute("menu", "task");
        model.addAttribute("task", task);
        model.addAttribute("partners", partnerService.listEnabled());
        model.addAttribute("datasourceOptions", taskService.datasourceOptions());
        model.addAttribute("handlerOptions", taskService.handlerOptions());
        return "task-edit";
    }

    /** 执行日志查询 */
    @GetMapping("/log/list")
    public String logList(@RequestParam(value = "taskCode", required = false) String taskCode,
                          @RequestParam(value = "taskName", required = false) String taskName,
                          @RequestParam(value = "status", required = false) String status,
                          @RequestParam(value = "triggerType", required = false) String triggerType,
                          @RequestParam(value = "keyword", required = false) String keyword,
                          @RequestParam(value = "startTime", required = false)
                          @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startTime,
                          @RequestParam(value = "endTime", required = false)
                          @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endTime,
                          @RequestParam(value = "page", defaultValue = "1") int page,
                          @RequestParam(value = "size", defaultValue = "20") int size,
                          Model model) {
        Page<TaskLog> result = logService.pageTaskLogs(taskCode, taskName, status, triggerType,
                keyword, startTime, endTime, page, size);
        model.addAttribute("menu", "log");
        model.addAttribute("page", result);
        model.addAttribute("taskCode", taskCode);
        model.addAttribute("taskName", taskName);
        model.addAttribute("status", status);
        model.addAttribute("triggerType", triggerType);
        model.addAttribute("keyword", keyword);
        model.addAttribute("startTime", startTime == null ? null : startTime.toString());
        model.addAttribute("endTime", endTime == null ? null : endTime.toString());
        model.addAttribute("taskOptions", taskService.page(null, null, null, 1, 200).getContent());
        return "log-list";
    }

    /** 接收日志 */
    @GetMapping("/receive/list")
    public String receiveList(@RequestParam(value = "apiCode", required = false) String apiCode,
                              @RequestParam(value = "status", required = false) String status,
                              @RequestParam(value = "keyword", required = false) String keyword,
                              @RequestParam(value = "startTime", required = false)
                              @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startTime,
                              @RequestParam(value = "endTime", required = false)
                              @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endTime,
                              @RequestParam(value = "page", defaultValue = "1") int page,
                              @RequestParam(value = "size", defaultValue = "20") int size,
                              Model model) {
        model.addAttribute("menu", "receive");
        model.addAttribute("page", logService.pageReceiveLogs(apiCode, status, keyword,
                startTime, endTime, page, size));
        model.addAttribute("apiCode", apiCode);
        model.addAttribute("status", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("startTime", startTime == null ? null : startTime.toString());
        model.addAttribute("endTime", endTime == null ? null : endTime.toString());
        return "receive-list";
    }

    /**
     * 接口日志：每个接口一个日志文件，可按接口查看 / 下载 / 清空。
     * 文件在 logs/interfaces/{接口编码}.log，由 InterfaceLogService 写入。
     */
    @GetMapping("/iface-log/list")
    public String ifaceLogList(Model model) {
        model.addAttribute("menu", "iface-log");
        model.addAttribute("files", interfaceLogService.listFiles());
        model.addAttribute("logDir", interfaceLogService.dirPath());
        return "iface-log-list";
    }

    /** 第三方系统管理 */
    @GetMapping("/partner/list")
    public String partnerList(Model model) {
        model.addAttribute("menu", "partner");
        model.addAttribute("partners", partnerService.listAll());
        return "partner-list";
    }

    /**
     * 接收接口清单：对外开放的 {@code /api/receive/{apiCode}} 都先在这里登记。
     * 未登记的接口会被直接拒绝（而不是被兜底逻辑回执成成功）。
     */
    @GetMapping("/receive-api/list")
    public String receiveApiList(Model model) {
        model.addAttribute("menu", "receive-api");
        model.addAttribute("apis", receiveApiService.listAll());
        return "receive-api-list";
    }

    /** 接收接口令牌：发给第三方调用方，用于调用 /api/receive/** */
    @GetMapping("/token/list")
    public String tokenList(Model model) {
        model.addAttribute("menu", "token");
        model.addAttribute("tokens", apiTokenService.listAll());
        return "token-list";
    }

    /** 个人中心 */
    @GetMapping("/profile")
    public String profile(Model model) {
        model.addAttribute("menu", "profile");
        model.addAttribute("today", LocalDate.now().toString());
        com.interchange.platform.common.LoginUser current = LoginUserHolder.current();
        if (current != null) {
            model.addAttribute("apiToken", authService.apiToken(current.getId()));
        }
        return "profile";
    }
}
