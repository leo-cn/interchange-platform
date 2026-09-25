package com.interchange.platform.controller;

import com.interchange.platform.common.BizException;
import com.interchange.platform.common.R;
import com.interchange.platform.common.Utils;
import com.interchange.platform.entity.ReceiveLog;
import com.interchange.platform.entity.TaskLog;
import com.interchange.platform.service.InterfaceLogService;
import com.interchange.platform.service.LogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志接口：详情查询 + 导出下载（TXT / CSV）。
 *
 * <p>下载接口沿用列表页的查询条件，导出“当前筛选结果”，最多 10000 条。
 */
@RestController
@RequestMapping("/api")
public class LogApiController {

    private static final int EXPORT_LIMIT = 10000;

    private final LogService logService;
    private final InterfaceLogService interfaceLogService;

    public LogApiController(LogService logService, InterfaceLogService interfaceLogService) {
        this.logService = logService;
        this.interfaceLogService = interfaceLogService;
    }

    /* ===================== 推送执行日志 ===================== */

    /** 日志详情（含完整请求/响应报文） */
    @GetMapping("/log/{id}")
    public R<Map<String, Object>> taskLogDetail(@PathVariable Long id) {
        TaskLog l = logService.getTaskLog(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", l.getId());
        data.put("taskCode", l.getTaskCode());
        data.put("taskName", l.getTaskName());
        data.put("partnerName", l.getPartnerName());
        data.put("traceId", l.getTraceId());
        data.put("triggerType", l.getTriggerType());
        data.put("status", l.getStatus());
        data.put("startTime", Utils.format(l.getStartTime()));
        data.put("endTime", Utils.format(l.getEndTime()));
        data.put("costMs", l.getCostMs());
        data.put("totalCount", l.getTotalCount());
        data.put("successCount", l.getSuccessCount());
        data.put("failCount", l.getFailCount());
        data.put("targetUrl", l.getTargetUrl());
        data.put("requestBody", Utils.prettyJson(l.getRequestBody()));
        data.put("responseBody", Utils.prettyJson(l.getResponseBody()));
        data.put("errorMsg", l.getErrorMsg());
        return R.ok(data);
    }

    /** 单条日志下载（TXT） */
    @GetMapping("/log/{id}/download")
    public ResponseEntity<byte[]> downloadOne(@PathVariable Long id) {
        TaskLog l = logService.getTaskLog(id);
        byte[] bytes = logService.singleTaskLogText(l).getBytes(StandardCharsets.UTF_8);
        return file(bytes, "push-log-" + l.getId() + "-" + timestamp() + ".txt", "txt");
    }

    /** 批量导出当前筛选条件下的执行日志 */
    @GetMapping("/log/download")
    public ResponseEntity<byte[]> downloadTaskLogs(
            @RequestParam(value = "taskCode", required = false) String taskCode,
            @RequestParam(value = "taskName", required = false) String taskName,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "triggerType", required = false) String triggerType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endTime,
            @RequestParam(value = "format", defaultValue = "txt") String format) {

        List<TaskLog> logs = logService.listTaskLogsForExport(taskCode, taskName, status, triggerType,
                keyword, startTime, endTime, EXPORT_LIMIT);
        byte[] bytes = logService.exportTaskLogs(logs, format);
        String ext = "csv".equalsIgnoreCase(format) ? "csv" : "txt";
        return file(bytes, "push-log-" + timestamp() + "-" + logs.size() + "rows." + ext, ext);
    }

    /* ===================== 接收日志 ===================== */

    @GetMapping("/receive-log/{id}")
    public R<Map<String, Object>> receiveLogDetail(@PathVariable Long id) {
        ReceiveLog l = logService.getReceiveLog(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", l.getId());
        data.put("apiCode", l.getApiCode());
        data.put("traceId", l.getTraceId());
        data.put("httpMethod", l.getHttpMethod());
        data.put("remoteIp", l.getRemoteIp());
        data.put("caller", l.getCaller());
        data.put("status", l.getStatus());
        data.put("receiveTime", Utils.format(l.getReceiveTime()));
        data.put("costMs", l.getCostMs());
        data.put("headers", Utils.prettyJson(l.getHeaders()));
        data.put("requestBody", Utils.prettyJson(l.getRequestBody()));
        data.put("responseBody", Utils.prettyJson(l.getResponseBody()));
        data.put("errorMsg", l.getErrorMsg());
        return R.ok(data);
    }

    @GetMapping("/receive-log/download")
    public ResponseEntity<byte[]> downloadReceiveLogs(
            @RequestParam(value = "apiCode", required = false) String apiCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endTime,
            @RequestParam(value = "format", defaultValue = "txt") String format) {

        List<ReceiveLog> logs = logService.listReceiveLogsForExport(apiCode, status, keyword,
                startTime, endTime, EXPORT_LIMIT);
        byte[] bytes = logService.exportReceiveLogs(logs, format);
        String ext = "csv".equalsIgnoreCase(format) ? "csv" : "txt";
        return file(bytes, "receive-log-" + timestamp() + "-" + logs.size() + "rows." + ext, ext);
    }

    /* ===================== 接口日志（每个接口一个文件） ===================== */

    /** 接口日志文件列表 */
    @GetMapping("/iface-log/list")
    public R<Map<String, Object>> ifaceLogList() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dir", interfaceLogService.dirPath());
        data.put("files", interfaceLogService.listFiles());
        return R.ok(data);
    }

    /**
     * 按 traceId 查一次执行的接口日志（含请求/响应报文）—— 数据库报文被关掉或截断时的兜底入口。
     * 先查当天文件，找不到再按时间倒序回翻历史归档（.log.gz）。
     */
    @GetMapping("/iface-log/trace")
    public R<Map<String, Object>> ifaceLogByTrace(@RequestParam(value = "iface", required = false) String iface,
                                                  @RequestParam(value = "traceId", required = false) String traceId,
                                                  @RequestParam(value = "date", required = false) String date) {
        // iface 可空：留空时跨所有接口查找，手里只有一个 traceId 也能用
        if (traceId == null || traceId.isBlank()) {
            throw new BizException(400, "请提供 traceId");
        }
        return R.ok(interfaceLogService.findByTraceId(iface, traceId, date));
    }

    /** 查看某接口日志的末尾若干行 */
    @GetMapping("/iface-log/view")
    public R<Map<String, Object>> ifaceLogView(@RequestParam(value = "file", required = false) String file,
                                               @RequestParam(value = "lines", defaultValue = "300") int lines) {
        if (file == null || file.isBlank()) {
            throw new BizException(400, "请指定要查看的接口日志文件");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("file", file);
        data.put("content", interfaceLogService.tail(file, lines));
        return R.ok(data);
    }

    /** 下载某接口的完整日志文件 */
    @GetMapping("/iface-log/download")
    public ResponseEntity<byte[]> ifaceLogDownload(@RequestParam(value = "file", required = false) String file) {
        if (file == null || file.isBlank()) {
            throw new BizException(400, "请指定要下载的接口日志文件");
        }
        byte[] bytes = interfaceLogService.readAll(file);
        return file(bytes, file, "txt");
    }

    /** 清空某接口的日志（截断当前文件 + 删除滚动出来的历史文件） */
    @PostMapping("/iface-log/clear")
    public R<String> ifaceLogClear(@RequestParam(value = "file", required = false) String file) {
        if (file == null || file.isBlank()) {
            throw new BizException(400, "请指定要清空的接口日志文件");
        }
        return R.ok(interfaceLogService.clear(file), null);
    }

    /* ===================== 工具方法 ===================== */

    private ResponseEntity<byte[]> file(byte[] bytes, String filename, String ext) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType("csv".equalsIgnoreCase(ext)
                ? new MediaType("text", "csv", StandardCharsets.UTF_8)
                : new MediaType("text", "plain", StandardCharsets.UTF_8));
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(bytes, headers, org.springframework.http.HttpStatus.OK);
    }

    private String timestamp() {
        return Utils.DT_FILE.format(LocalDateTime.now());
    }
}
