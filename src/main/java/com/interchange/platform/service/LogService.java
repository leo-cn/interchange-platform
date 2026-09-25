package com.interchange.platform.service;

import com.interchange.platform.common.BizException;
import com.interchange.platform.common.Utils;
import com.interchange.platform.entity.ReceiveLog;
import com.interchange.platform.entity.TaskLog;
import com.interchange.platform.repository.ReceiveLogRepository;
import com.interchange.platform.repository.TaskLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 日志服务：条件查询 + 详情 + 导出下载（TXT / CSV）。
 */
@Service
public class LogService {

    private final TaskLogRepository taskLogRepository;
    private final ReceiveLogRepository receiveLogRepository;

    public LogService(TaskLogRepository taskLogRepository, ReceiveLogRepository receiveLogRepository) {
        this.taskLogRepository = taskLogRepository;
        this.receiveLogRepository = receiveLogRepository;
    }

    /* ===================== 推送日志 ===================== */

    public Page<TaskLog> pageTaskLogs(String taskCode, String taskName, String status, String triggerType,
                                      String keyword, LocalDateTime start, LocalDateTime end,
                                      int page, int size) {
        Specification<TaskLog> spec = taskLogSpec(taskCode, taskName, status, triggerType, keyword, start, end);
        return taskLogRepository.findAll(spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id")));
    }

    public List<TaskLog> listTaskLogsForExport(String taskCode, String taskName, String status, String triggerType,
                                               String keyword, LocalDateTime start, LocalDateTime end,
                                               int limit) {
        Specification<TaskLog> spec = taskLogSpec(taskCode, taskName, status, triggerType, keyword, start, end);
        return taskLogRepository.findAll(spec,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "id")))
                .getContent();
    }

    private Specification<TaskLog> taskLogSpec(String taskCode, String taskName, String status, String triggerType,
                                               String keyword, LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            List<Predicate> list = new ArrayList<>();
            if (notBlank(taskCode)) {
                list.add(cb.equal(root.get("taskCode"), taskCode.trim()));
            }
            if (notBlank(taskName)) {
                list.add(cb.like(root.get("taskName"), "%" + taskName.trim() + "%"));
            }
            if (notBlank(status)) {
                list.add(cb.equal(root.get("status"), status.trim()));
            }
            if (notBlank(triggerType)) {
                list.add(cb.equal(root.get("triggerType"), triggerType.trim()));
            }
            if (start != null) {
                list.add(cb.greaterThanOrEqualTo(root.get("startTime"), start));
            }
            if (end != null) {
                list.add(cb.lessThanOrEqualTo(root.get("startTime"), end));
            }
            if (notBlank(keyword)) {
                String like = "%" + keyword.trim() + "%";
                list.add(cb.or(
                        cb.like(root.get("traceId"), like),
                        cb.like(root.get("errorMsg"), like),
                        cb.like(root.get("targetUrl"), like),
                        cb.like(root.get("requestBody"), like),
                        cb.like(root.get("responseBody"), like)
                ));
            }
            return list.isEmpty() ? cb.conjunction() : cb.and(list.toArray(new Predicate[0]));
        };
    }

    public TaskLog getTaskLog(Long id) {
        return taskLogRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "日志不存在: " + id));
    }

    /* ===================== 接收日志 ===================== */

    public Page<ReceiveLog> pageReceiveLogs(String apiCode, String status, String keyword,
                                            LocalDateTime start, LocalDateTime end,
                                            int page, int size) {
        Specification<ReceiveLog> spec = receiveLogSpec(apiCode, status, keyword, start, end);
        return receiveLogRepository.findAll(spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id")));
    }

    public List<ReceiveLog> listReceiveLogsForExport(String apiCode, String status, String keyword,
                                                     LocalDateTime start, LocalDateTime end, int limit) {
        Specification<ReceiveLog> spec = receiveLogSpec(apiCode, status, keyword, start, end);
        return receiveLogRepository.findAll(spec,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "id")))
                .getContent();
    }

    private Specification<ReceiveLog> receiveLogSpec(String apiCode, String status, String keyword,
                                                     LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            List<Predicate> list = new ArrayList<>();
            if (notBlank(apiCode)) {
                list.add(cb.equal(root.get("apiCode"), apiCode.trim()));
            }
            if (notBlank(status)) {
                list.add(cb.equal(root.get("status"), status.trim()));
            }
            if (start != null) {
                list.add(cb.greaterThanOrEqualTo(root.get("receiveTime"), start));
            }
            if (end != null) {
                list.add(cb.lessThanOrEqualTo(root.get("receiveTime"), end));
            }
            if (notBlank(keyword)) {
                String like = "%" + keyword.trim() + "%";
                list.add(cb.or(
                        cb.like(root.get("traceId"), like),
                        cb.like(root.get("caller"), like),
                        cb.like(root.get("errorMsg"), like),
                        cb.like(root.get("requestBody"), like)
                ));
            }
            return list.isEmpty() ? cb.conjunction() : cb.and(list.toArray(new Predicate[0]));
        };
    }

    public ReceiveLog getReceiveLog(Long id) {
        return receiveLogRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "接收日志不存在: " + id));
    }

    /* ===================== 导出 ===================== */

    /** 单条推送日志导出为 TXT 文本 */
    public String singleTaskLogText(TaskLog l) {
        StringBuilder sb = new StringBuilder();
        sb.append("==================================================================\n");
        sb.append("日志ID: ").append(l.getId()).append("\n");
        sb.append("任务编码: ").append(safe(l.getTaskCode())).append("\n");
        sb.append("任务名称: ").append(safe(l.getTaskName())).append("\n");
        sb.append("第三方系统: ").append(safe(l.getPartnerName())).append("\n");
        sb.append("traceId: ").append(safe(l.getTraceId())).append("\n");
        sb.append("触发方式: ").append(safe(l.getTriggerType())).append("\n");
        sb.append("开始时间: ").append(Utils.format(l.getStartTime())).append("\n");
        sb.append("结束时间: ").append(Utils.format(l.getEndTime())).append("\n");
        sb.append("耗时(ms): ").append(l.getCostMs()).append("\n");
        sb.append("执行结果: ").append(safe(l.getStatus())).append("\n");
        sb.append("数据条数: ").append(l.getTotalCount())
                .append("  成功: ").append(l.getSuccessCount())
                .append("  失败: ").append(l.getFailCount()).append("\n");
        sb.append("目标地址: ").append(safe(l.getTargetUrl())).append("\n");
        sb.append("----------------- 请求报文 -----------------\n");
        sb.append(safe(l.getRequestBody())).append("\n");
        sb.append("----------------- 响应报文 -----------------\n");
        sb.append(safe(l.getResponseBody())).append("\n");
        if (l.getErrorMsg() != null && !l.getErrorMsg().isBlank()) {
            sb.append("----------------- 错误信息 -----------------\n");
            sb.append(l.getErrorMsg()).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    /** 批量导出推送日志 */
    public byte[] exportTaskLogs(List<TaskLog> logs, String format) {
        return export(logs, format,
                "日志ID,任务编码,任务名称,第三方系统,traceId,触发方式,开始时间,结束时间,耗时(ms),状态,数据条数,成功,失败,目标地址,错误信息",
                l -> new String[]{
                        str(l.getId()), safe(l.getTaskCode()), safe(l.getTaskName()), safe(l.getPartnerName()),
                        safe(l.getTraceId()), safe(l.getTriggerType()), Utils.format(l.getStartTime()),
                        Utils.format(l.getEndTime()), str(l.getCostMs()), safe(l.getStatus()),
                        str(l.getTotalCount()), str(l.getSuccessCount()), str(l.getFailCount()),
                        safe(l.getTargetUrl()), safe(l.getErrorMsg())
                },
                this::singleTaskLogText);
    }

    /** 批量导出接收日志 */
    public byte[] exportReceiveLogs(List<ReceiveLog> logs, String format) {
        return exportReceive(logs, format);
    }

    private byte[] exportReceive(List<ReceiveLog> logs, String format) {
        if ("csv".equalsIgnoreCase(format)) {
            StringBuilder sb = new StringBuilder();
            sb.append('\uFEFF');
            sb.append("日志ID,接口编码,traceId,请求方式,来源IP,调用方,接收时间,耗时(ms),状态,错误信息\n");
            for (ReceiveLog l : logs) {
                sb.append(csv(str(l.getId()), safe(l.getApiCode()), safe(l.getTraceId()), safe(l.getHttpMethod()),
                        safe(l.getRemoteIp()), safe(l.getCaller()), Utils.format(l.getReceiveTime()),
                        str(l.getCostMs()), safe(l.getStatus()), safe(l.getErrorMsg()))).append("\n");
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
        StringBuilder sb = new StringBuilder();
        for (ReceiveLog l : logs) {
            sb.append("==================================================================\n");
            sb.append("日志ID: ").append(l.getId()).append("\n");
            sb.append("接口编码: ").append(safe(l.getApiCode())).append("\n");
            sb.append("traceId: ").append(safe(l.getTraceId())).append("\n");
            sb.append("请求方式: ").append(safe(l.getHttpMethod())).append("\n");
            sb.append("来源IP: ").append(safe(l.getRemoteIp())).append("\n");
            sb.append("调用方: ").append(safe(l.getCaller())).append("\n");
            sb.append("接收时间: ").append(Utils.format(l.getReceiveTime())).append("\n");
            sb.append("耗时(ms): ").append(l.getCostMs()).append("\n");
            sb.append("处理结果: ").append(safe(l.getStatus())).append("\n");
            sb.append("----------------- 请求头 -----------------\n").append(safe(l.getHeaders())).append("\n");
            sb.append("----------------- 请求报文 -----------------\n").append(safe(l.getRequestBody())).append("\n");
            sb.append("----------------- 响应报文 -----------------\n").append(safe(l.getResponseBody())).append("\n");
            if (l.getErrorMsg() != null && !l.getErrorMsg().isBlank()) {
                sb.append("----------------- 错误信息 -----------------\n").append(l.getErrorMsg()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private interface RowBuilder<T> {
        String[] build(T item);
    }

    private interface TextBuilder<T> {
        String build(T item);
    }

    private <T> byte[] export(List<T> list, String format, String csvHeader,
                              RowBuilder<T> rowBuilder, TextBuilder<T> textBuilder) {
        if ("csv".equalsIgnoreCase(format)) {
            StringBuilder sb = new StringBuilder();
            sb.append('\uFEFF');   // BOM，保证 Excel 打开中文不乱码
            sb.append(csvHeader).append("\n");
            for (T item : list) {
                sb.append(csv(rowBuilder.build(item))).append("\n");
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
        StringBuilder sb = new StringBuilder();
        for (T item : list) {
            sb.append(textBuilder.build(item));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csv(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escapeCsv(cells[i]));
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("\r", " ").replace("\n", " ");
        if (v.contains(",") || v.contains("\"")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
