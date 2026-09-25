package com.interchange.platform.service;

import com.interchange.platform.common.BizException;
import com.interchange.platform.common.Crypto;
import com.interchange.platform.common.TraceId;
import com.interchange.platform.common.Utils;
import com.interchange.platform.config.AppProps;
import com.interchange.platform.entity.InterfaceTask;
import com.interchange.platform.entity.Partner;
import com.interchange.platform.entity.TaskLog;
import com.interchange.platform.repository.InterfaceTaskRepository;
import com.interchange.platform.repository.PartnerRepository;
import com.interchange.platform.service.handler.SendResult;
import com.interchange.platform.service.handler.TaskContext;
import com.interchange.platform.service.handler.TaskHandler;
import com.interchange.platform.service.handler.TaskHandlerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 推送引擎：一次任务执行的完整链路。
 *
 * <pre>
 *   取数(DataFetcher) → 组装报文 → 调用第三方(带认证/超时，失败不重试) → 落执行日志 → 回写任务执行状态
 * </pre>
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final InterfaceTaskRepository taskRepository;
    private final LogStore logStore;
    private final PartnerRepository partnerRepository;
    private final DataFetcher dataFetcher;
    private final AppProps appProps;
    private final InterfaceLogService interfaceLogService;
    private final TaskHandlerRegistry handlerRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 手工触发使用独立线程池，避免阻塞页面请求 */
    private final ExecutorService manualExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "manual-push-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    public PushService(InterfaceTaskRepository taskRepository,
                       LogStore logStore,
                       PartnerRepository partnerRepository,
                       DataFetcher dataFetcher,
                       AppProps appProps,
                       InterfaceLogService interfaceLogService,
                       TaskHandlerRegistry handlerRegistry) {
        this.taskRepository = taskRepository;
        this.logStore = logStore;
        this.partnerRepository = partnerRepository;
        this.dataFetcher = dataFetcher;
        this.appProps = appProps;
        this.interfaceLogService = interfaceLogService;
        this.handlerRegistry = handlerRegistry;
    }

    /** 异步执行（手工触发 / 定时触发统一入口） */
    public void executeAsync(Long taskId, String triggerType) {
        manualExecutor.submit(() -> {
            try {
                execute(taskId, triggerType);
            } catch (Throwable t) {
                log.error("任务异步执行异常 taskId={}", taskId, t);
            }
        });
    }

    /**
     * 同步执行一次任务，返回日志 ID。
     * 无论成功失败都会落一条执行日志，便于日志查询页追溯。
     */
    public Long execute(Long taskId, String triggerType) {
        InterfaceTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(404, "任务不存在: " + taskId));

        TaskLog taskLog = new TaskLog();
        taskLog.setTaskId(task.getId());
        taskLog.setTaskCode(task.getTaskCode());
        taskLog.setTaskName(task.getTaskName());
        taskLog.setTriggerType(triggerType == null ? "MANUAL" : triggerType);
        taskLog.setTraceId(TraceId.generate());
        taskLog.setStartTime(LocalDateTime.now());

        long start = System.currentTimeMillis();
        String code = task.getTaskCode();
        String name = task.getTaskName();
        String traceId = taskLog.getTraceId();
        boolean cron = "CRON".equalsIgnoreCase(taskLog.getTriggerType());
        String triggerName = cron ? "定时任务" : "手动执行";
        // 过程日志的人话前缀：定时/手动各一套，避免出现"开始运行手动执行"这种别扭说法。
        // 收尾句带上任务编码（"OaUserTask 定时任务执行结束，…"），
        // 因为日志按接口分文件后，同一份文件里也可能穿插多次执行，带上编码才对得上号。
        String beginPrefix = cron ? "开始运行定时任务：" : "开始手动执行推送任务：";
        // 收尾句形如「OaUserTask 定时任务执行结束：失败，共 1 条…」，成败写在文案里，
        // 不再单独落「执行结果」行（见下面 END 阶段的说明）
        String endPrefix = (cron ? code + " 定时任务执行结束：" : code + " 手动执行结束：");
        try {
            // 每次执行前先空一行：把本次运行和上一次的记录在文件里隔开
            interfaceLogService.blankLine(code);
            // 自定义处理器：任务上没配就是 null，全程走默认逻辑
            TaskHandler handler = handlerRegistry.find(task.getHandlerBean());
            // 过程日志 1/5：开始
            log.info("任务[{}] 开始运行（{}）", code, triggerName);
            Map<String, Object> startExtra = new LinkedHashMap<>();
            if (handler != null) {
                startExtra.put("handler", handler.code());
            }
            interfaceLogService.stage("PUSH", code, name, traceId, "START",
                    beginPrefix + code + (name == null ? "" : "（" + name + "）"),
                    startExtra.isEmpty() ? null : startExtra);

            Partner partner = resolvePartner(task);
            taskLog.setPartnerName(partner == null ? "-" : partner.getPartnerName());

            String targetUrl = buildTargetUrl(partner, task);
            taskLog.setTargetUrl(targetUrl);

            // 贯穿本次执行的上下文，六个钩子共用
            TaskContext ctx = new TaskContext(task, partner, targetUrl, traceId, taskLog.getTriggerType());

            // 1) 取数：处理器可以先改写 SQL（增量条件），再对结果做字段转换
            if (handler != null) {
                handler.beforeFetch(ctx);
            }
            DataFetcher.FetchResult fetched = dataFetcher.fetch(task, ctx.getSqlOverride());
            if (handler != null) {
                fetched = applyTransform(ctx, handler, fetched);
            }
            taskLog.setTotalCount(fetched.getTotal());

            // 过程日志 2/5：取数完成
            int total = fetched.getTotal();
            Map<String, Object> fetchedExtra = new LinkedHashMap<>();
            fetchedExtra.put("total", total);
            fetchedExtra.put("targetUrl", targetUrl);
            if (task.getSqlText() != null && !task.getSqlText().isBlank()) {
                fetchedExtra.put("sql", appProps.getIfaceLog().isSqlPretty()
                        ? Utils.prettySql(task.getSqlText()) : task.getSqlText().trim());
            }
            String fetchedMsg = "查询到 " + total + " 条待发送的数据";
            if (total <= 0) {
                fetchedMsg = "未查询到待发送的数据";
            }
            log.info("任务[{}] {}", code, fetchedMsg);
            interfaceLogService.stage("PUSH", code, name, traceId, "FETCHED", fetchedMsg, fetchedExtra);

            // 2) 推送
            if ("PER_ROW".equalsIgnoreCase(task.getPushMode())) {
                pushPerRow(ctx, handler, fetched, taskLog);
            } else {
                pushBatch(ctx, handler, fetched, taskLog);
            }
        } catch (Exception e) {
            taskLog.setStatus("FAIL");
            taskLog.setFailCount(taskLog.getTotalCount() == null ? 1 : Math.max(taskLog.getTotalCount(), 1));
            taskLog.setSuccessCount(0);
            taskLog.setErrorMsg(Utils.truncate("[" + e.getClass().getSimpleName() + "] " + e.getMessage(),
                    appProps.getPush().getLogBodyLimit()));
            log.error("任务[{}]执行失败: {}", task.getTaskCode(), e.getMessage(), e);
        } finally {
            long cost = System.currentTimeMillis() - start;
            taskLog.setCostMs(cost);
            taskLog.setEndTime(LocalDateTime.now());
            if (taskLog.getStatus() == null) {
                taskLog.setStatus("FAIL");
            }
            // 走统一落库入口：默认每个任务只留最后一次，避免日志表随时间膨胀
            TaskLog saved = logStore.saveTaskLog(taskLog);
            updateTaskStat(task, saved);
            // 过程日志 5/5：结束（汇总）。
            // 成败直接写进主文案，**不再**额外追加「执行结果」「结束报文」两行、
            // 也不再落一行 JSON 汇总：同样的字段数据库 task_log 里全都有（页面可导出
            // CSV / 文本），文件里重复输出只会把每次执行的边界（空行分隔）冲淡。
            Map<String, Object> endExtra = new LinkedHashMap<>();
            endExtra.put("costMs", cost);
            endExtra.put("total", saved.getTotalCount());
            endExtra.put("success", saved.getSuccessCount());
            endExtra.put("fail", saved.getFailCount());
            endExtra.put("targetUrl", saved.getTargetUrl());
            interfaceLogService.stage("PUSH", task.getTaskCode(), task.getTaskName(), saved.getTraceId(), "END",
                    endPrefix + statusText(saved.getStatus()) + "，共 " + saved.getTotalCount()
                            + " 条，成功 " + saved.getSuccessCount()
                            + " 条，失败 " + saved.getFailCount() + " 条，耗时 " + cost + "ms", endExtra);
            // 收尾不留第二条记录：上面这行 END 汇总 + 数据库 task_log 已经够用，
            // 再往文件里落一行 JSON 只是把每次执行的边界（空行分隔）冲淡。
            log.info("任务[{}]执行结束: status={}, 总数={}, 成功={}, 失败={}, 耗时={}ms, traceId={}",
                    task.getTaskCode(), saved.getStatus(), saved.getTotalCount(),
                    saved.getSuccessCount(), saved.getFailCount(), cost, saved.getTraceId());
            return saved.getId();
        }
    }

    /** 整批推送：一次 HTTP 调用把全部数据推过去 */
    private void pushBatch(TaskContext ctx, TaskHandler handler,
                           DataFetcher.FetchResult fetched, TaskLog taskLog) {
        InterfaceTask task = ctx.getTask();
        String targetUrl = ctx.getTargetUrl();
        String body = buildBatchBody(ctx, handler, fetched);
        taskLog.setRequestBody(Utils.truncate(body, appProps.getPush().getLogBodyLimit()));

        // 过程日志 3/5：开始推送（整批）
        int total = Math.max(fetched.getTotal(), 1);
        log.info("任务[{}] 开始推送 {} 条数据（整批）-> {}", task.getTaskCode(), total, targetUrl);
        Map<String, Object> sendExtra = new LinkedHashMap<>();
        sendExtra.put("total", total);
        sendExtra.put("targetUrl", targetUrl);
        sendExtra.put("request", interfaceLogService.payload(body));
        interfaceLogService.stage("PUSH", task.getTaskCode(), task.getTaskName(), taskLog.getTraceId(),
                "SEND_START", "开始推送 " + total + " 条数据（整批）", sendExtra);

        long sendStart = System.currentTimeMillis();
        PushOutcome outcome = callOnce(ctx, handler, body, taskLog);
        long sendCost = System.currentTimeMillis() - sendStart;
        taskLog.setResponseBody(Utils.truncate(outcome.responseBody, appProps.getPush().getLogBodyLimit()));

        // 推送后回调：回写业务表 / 保存第三方凭据。
        // 这里抛异常只记 WARN，不会把已送达的这次调用判成失败（避免下次调度重复推送）
        invokeAfterSend(handler, ctx, new SendResult(outcome.success, outcome.statusCode, body,
                outcome.responseBody, outcome.errorMsg, sendCost, 1, 1));

        // 过程日志 4/5：推送结束
        Map<String, Object> sendEndExtra = new LinkedHashMap<>();
        sendEndExtra.put("status", outcome.success ? "SUCCESS" : "FAIL");
        sendEndExtra.put("costMs", sendCost);
        sendEndExtra.put("httpStatus", outcome.statusCode);
        sendEndExtra.put("response", interfaceLogService.payload(outcome.responseBody));
        if (!outcome.success) {
            sendEndExtra.put("errorMsg", outcome.errorMsg);
        }
        // 注意：成功/失败不在主文案里说，由日志的「执行结果」行在响应报文之后给出
        interfaceLogService.stage("PUSH", task.getTaskCode(), task.getTaskName(), taskLog.getTraceId(),
                "SEND_END", "整批推送结束，耗时 " + sendCost + "ms", sendEndExtra);

        if (outcome.success) {
            taskLog.setStatus("SUCCESS");
            taskLog.setSuccessCount(Math.max(fetched.getTotal(), 1));
            taskLog.setFailCount(0);
        } else {
            taskLog.setStatus("FAIL");
            taskLog.setSuccessCount(0);
            taskLog.setFailCount(Math.max(fetched.getTotal(), 1));
            taskLog.setErrorMsg(Utils.truncate(outcome.errorMsg, appProps.getPush().getLogBodyLimit()));
        }
    }

    /** 逐条推送：每条数据一次 HTTP 调用 */
    private void pushPerRow(TaskContext ctx, TaskHandler handler,
                            DataFetcher.FetchResult fetched, TaskLog taskLog) {
        InterfaceTask task = ctx.getTask();
        List<Map<String, Object>> rows = DataFetcher.asRowList(fetched.getData());
        if (rows.isEmpty()) {
            // 非集合数据退化为整批
            pushBatch(ctx, handler, fetched, taskLog);
            return;
        }

        int success = 0;
        int fail = 0;
        // 逐条模式下，每条数据的报文按**数组**保存，保证整体仍是合法 JSON
        // （早期版本用 "---- 第 N 条 ----" 分隔拼接，导致落库报文不是 JSON，
        //  第三方对账、日志页、接口日志文件都无法按字段解析）
        List<Object> requestItems = new ArrayList<>();
        List<Object> responseItems = new ArrayList<>();
        StringBuilder errors = new StringBuilder();

        for (Map<String, Object> row : rows) {
            int index = success + fail + 1;
            String body = buildRowBody(ctx, handler, row);
            requestItems.add(Utils.jsonToNode(body));

            // 过程日志 3/5（逐条）：开始推送第 i 条
            log.info("任务[{}] 开始推送第 {}/{} 条数据", task.getTaskCode(), index, rows.size());
            Map<String, Object> startExtra = new LinkedHashMap<>();
            startExtra.put("index", index);
            startExtra.put("total", rows.size());
            startExtra.put("request", interfaceLogService.payload(body));
            interfaceLogService.stage("PUSH", task.getTaskCode(), task.getTaskName(), taskLog.getTraceId(),
                    "SEND_START", "开始推送第 " + index + "/" + rows.size() + " 条数据", startExtra);

            long sendStart = System.currentTimeMillis();
            PushOutcome outcome = callOnce(ctx, handler, body, taskLog);
            long sendCost = System.currentTimeMillis() - sendStart;
            responseItems.add(Utils.jsonToNode(outcome.responseBody));

            // 每条都回调一次，回写时可用 result.getIndex() 区分是哪条
            invokeAfterSend(handler, ctx, new SendResult(outcome.success, outcome.statusCode, body,
                    outcome.responseBody, outcome.errorMsg, sendCost, index, rows.size()));

            // 过程日志 4/5（逐条）：第 i 条结果
            Map<String, Object> endExtra = new LinkedHashMap<>();
            endExtra.put("index", index);
            endExtra.put("total", rows.size());
            endExtra.put("status", outcome.success ? "SUCCESS" : "FAIL");
            endExtra.put("costMs", sendCost);
            endExtra.put("httpStatus", outcome.statusCode);
            endExtra.put("response", interfaceLogService.payload(outcome.responseBody));
            if (!outcome.success) {
                endExtra.put("errorMsg", outcome.errorMsg);
            }
            // 成功/失败同样交给日志的「执行结果」行（排在响应报文之后）
            interfaceLogService.stage("PUSH", task.getTaskCode(), task.getTaskName(), taskLog.getTraceId(),
                    "SEND_END", "第 " + index + " 条推送结束，耗时 " + sendCost + "ms", endExtra);

            if (outcome.success) {
                success++;
            } else {
                fail++;
                if (errors.length() < 4000) {
                    errors.append("第 ").append(success + fail).append(" 条: ").append(outcome.errorMsg).append("\n");
                }
            }
        }

        taskLog.setTotalCount(rows.size());
        taskLog.setSuccessCount(success);
        taskLog.setFailCount(fail);
        taskLog.setRequestBody(Utils.truncate(Utils.toJson(requestItems), appProps.getPush().getLogBodyLimit()));
        taskLog.setResponseBody(Utils.truncate(Utils.toJson(responseItems), appProps.getPush().getLogBodyLimit()));
        if (fail == 0) {
            taskLog.setStatus("SUCCESS");
        } else if (success == 0) {
            taskLog.setStatus("FAIL");
            taskLog.setErrorMsg(Utils.truncate(errors.toString(), appProps.getPush().getLogBodyLimit()));
        } else {
            taskLog.setStatus("PARTIAL");
            taskLog.setErrorMsg(Utils.truncate(errors.toString(), appProps.getPush().getLogBodyLimit()));
        }
    }

    /**
     * 调用第三方：**只调一次，失败不重试**。
     *
     * <p>重试机制已移除：接口交换场景下失败基本都是配置/网络问题，重试只会让一次执行
     * 拖满几十秒（曾经 OaUserTask 每 30 秒跑一次、每次再重试两轮，日志全是无效重试），
     * 而且重复推送容易造成第三方重复入库。失败就如实记为 FAIL，由下一次调度重新推送。
     */
    private PushOutcome callOnce(TaskContext ctx, TaskHandler handler, String body, TaskLog taskLog) {
        InterfaceTask task = ctx.getTask();
        Partner partner = ctx.getPartner();
        String targetUrl = ctx.getTargetUrl();
        int timeout = resolveTimeout(task, partner);
        PushOutcome outcome = new PushOutcome();
        try {
            // 请求头先收进 Map，处理器可以覆盖任意一项（含签名、Content-Type），
            // 最后统一写进 builder，避免同一个头被写两遍
            Map<String, String> headers = new LinkedHashMap<>();
            // 优先级：第三方系统级 → 任务级 → 处理器
            if (partner != null) {
                DataFetcher.parseHeaders(partner.getHeadersJson()).forEach(headers::put);
                applyAuth(headers, partner);
            }
            DataFetcher.parseHeaders(task.getHeadersJson()).forEach(headers::put);
            if (handler != null) {
                handler.beforeSend(ctx, headers, body);
            }
            // 链路 ID 最后压入，不允许被配置或处理器覆盖
            headers.put("X-Trace-Id", taskLog.getTraceId());
            String contentType = takeHeader(headers, "content-type");
            if (contentType == null) {
                contentType = task.getContentType() == null
                        ? "application/json;charset=UTF-8" : task.getContentType();
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(targetUrl))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", contentType);
            headers.forEach(builder::header);

            String method = task.getHttpMethod() == null ? "POST" : task.getHttpMethod().toUpperCase();
            if ("GET".equals(method)) {
                builder.GET();
            } else if ("PUT".equals(method)) {
                builder.PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            } else {
                builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            outcome.responseBody = response.body();
            outcome.statusCode = response.statusCode();
            // 成败判定交给处理器：第三方经常是 HTTP 200 但响应体里 code != 0
            Boolean judged = handler == null ? null : handler.judge(ctx, response.statusCode(), response.body());
            outcome.success = judged != null
                    ? judged : (response.statusCode() >= 200 && response.statusCode() < 300);
            if (outcome.success) {
                outcome.errorMsg = null;
                return outcome;
            }
            String reason = handler == null ? null
                    : handler.failureReason(ctx, response.statusCode(), response.body());
            outcome.errorMsg = (reason == null || reason.isBlank())
                    ? "HTTP " + response.statusCode() + ": " + abbreviate(response.body()) : reason;
            logCallFailure(task, taskLog, targetUrl, outcome.errorMsg);
        } catch (Exception e) {
            outcome.success = false;
            // getMessage() 经常是 null（例如 ConnectException 把原因放在 cause 里），
            // 直接打印就是"失败: null"，等于没说，这里统一兜底成人话
            outcome.errorMsg = errorReason(e);
            outcome.responseBody = outcome.responseBody == null ? "" : outcome.responseBody;
            logCallFailure(task, taskLog, targetUrl, outcome.errorMsg);
        }
        return outcome;
    }

    /**
     * 调用第三方失败时写一条日志：既进平台主日志，也进该接口的专属日志文件
     * （logs/interfaces/{任务编码}/{任务编码}.log）。
     */
    private void logCallFailure(InterfaceTask task, TaskLog taskLog, String targetUrl, String reason) {
        String msg = "调用第三方失败：" + reason;
        log.warn("任务[{}]{}", task.getTaskCode(), msg);

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("errorMsg", reason);
        InterfaceLogService.putIf(extra, "targetUrl", targetUrl);
        interfaceLogService.stage("PUSH", task.getTaskCode(), task.getTaskName(),
                taskLog.getTraceId(), "CALL_FAIL", msg, extra);
    }

    /**
     * 异常 → 人话原因。getMessage() 为 null 时继续往 cause 里找，
     * 实在没有就退回异常类名，绝不出现 "null"。
     */
    private static String errorReason(Exception e) {
        String simple = e.getClass().getSimpleName();
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            return "[" + simple + "] " + e.getMessage();
        }
        Throwable cause = e.getCause();
        if (cause != null) {
            String cm = cause.getMessage() == null || cause.getMessage().isBlank()
                    ? cause.getClass().getSimpleName() : cause.getMessage();
            return "[" + simple + "] " + cm;
        }
        return "[" + simple + "] 无异常信息";
    }

    /** 组装整批报文体 */
    private String buildBatchBody(TaskContext ctx, TaskHandler handler, DataFetcher.FetchResult fetched) {
        InterfaceTask task = ctx.getTask();
        if (handler != null) {
            String custom = handler.buildBody(ctx, fetched.getData());
            if (custom != null && !custom.isBlank()) {
                return custom;
            }
        }
        if ("FIXED".equalsIgnoreCase(task.getSourceType()) && fetched.getRawText() != null) {
            return fetched.getRawText();
        }
        if ("HTTP_PULL".equalsIgnoreCase(task.getSourceType()) && fetched.getRawText() != null) {
            return fetched.getRawText();
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("taskCode", task.getTaskCode());
        envelope.put("taskName", task.getTaskName());
        envelope.put("traceId", TraceId.current());
        envelope.put("sendTime", Utils.format(LocalDateTime.now()));
        envelope.put("total", fetched.getTotal());
        envelope.put("data", fetched.getData());
        return Utils.toJson(envelope);
    }

    /** 组装单条报文体；处理器返回非 null 时以处理器为准 */
    private String buildRowBody(TaskContext ctx, TaskHandler handler, Map<String, Object> row) {
        InterfaceTask task = ctx.getTask();
        if (handler != null) {
            String custom = handler.buildBody(ctx, row);
            if (custom != null && !custom.isBlank()) {
                return custom;
            }
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("taskCode", task.getTaskCode());
        envelope.put("traceId", TraceId.current());
        envelope.put("sendTime", Utils.format(LocalDateTime.now()));
        envelope.put("data", row);
        return Utils.toJson(envelope);
    }

    /** 取数后交给处理器做字段转换 / 过滤 */
    private DataFetcher.FetchResult applyTransform(TaskContext ctx, TaskHandler handler,
                                                   DataFetcher.FetchResult fetched) {
        Object origin = fetched.getData();
        Object converted = handler.transform(ctx, origin);
        if (converted == null || converted == origin) {
            return fetched;
        }
        int total = converted instanceof List ? ((List<?>) converted).size() : fetched.getTotal();
        // 数据被改写后原始报文文本就不再可信，置空强制用转换后的数据重新序列化
        return new DataFetcher.FetchResult(total, converted, null, fetched.getCostMs());
    }

    /**
     * 推送后回调。处理器在这里回写业务表、保存第三方返回的凭据。
     * 抛异常只记 WARN，不参与成败判定：数据已经送达，回写出错不该让下次调度重复推送。
     */
    private void invokeAfterSend(TaskHandler handler, TaskContext ctx, SendResult result) {
        if (handler == null) {
            return;
        }
        try {
            handler.afterSend(ctx, result);
        } catch (Exception e) {
            log.warn("任务[{}] 处理器[{}] afterSend 执行失败（不影响本次结果）: {}",
                    ctx.getTask().getTaskCode(), handler.code(), errorReason(e));
        }
    }

    /** 从请求头 Map 中取出并移除同名头（忽略大小写） */
    private String takeHeader(Map<String, String> headers, String lowerName) {
        String found = null;
        for (String key : headers.keySet()) {
            if (key != null && key.equalsIgnoreCase(lowerName)) {
                found = key;
                break;
            }
        }
        if (found == null) {
            return null;
        }
        return headers.remove(found);
    }

    /** 目标地址 = 第三方 baseUrl + 任务 targetPath */
    private String buildTargetUrl(Partner partner, InterfaceTask task) {
        if (task.getTargetPath() == null || task.getTargetPath().isBlank()) {
            throw new BizException(400, "任务未配置目标路径 targetPath");
        }
        String path = task.getTargetPath().trim();
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (partner == null || partner.getBaseUrl() == null || partner.getBaseUrl().isBlank()) {
            throw new BizException(400, "任务未关联第三方系统，或该系统未配置 baseUrl");
        }
        String base = partner.getBaseUrl().trim();
        if (base.endsWith("/") && path.startsWith("/")) {
            return base.substring(0, base.length() - 1) + path;
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }

    private Partner resolvePartner(InterfaceTask task) {
        if (task.getPartnerId() == null) {
            return null;
        }
        return partnerRepository.findById(task.getPartnerId())
                .orElseThrow(() -> new BizException(400, "关联的第三方系统不存在，请重新选择"));
    }

    /** 认证信息注入（写进请求头 Map，可被任务级与处理器覆盖） */
    private void applyAuth(Map<String, String> headers, Partner partner) {
        String type = partner.getAuthType() == null ? "NONE" : partner.getAuthType().toUpperCase();
        String secret = Crypto.decrypt(partner.getAuthSecret());
        switch (type) {
            case "BASIC" -> {
                String raw = (partner.getAuthUser() == null ? "" : partner.getAuthUser()) + ":"
                        + (secret == null ? "" : secret);
                headers.put("Authorization", "Basic "
                        + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
            }
            case "BEARER" -> headers.put("Authorization", "Bearer " + (secret == null ? "" : secret));
            case "API_KEY" -> headers.put("X-API-KEY", secret == null ? "" : secret);
            case "HEADER" -> {
                String name = partner.getAuthUser() == null || partner.getAuthUser().isBlank()
                        ? "X-Auth-Token" : partner.getAuthUser();
                headers.put(name, secret == null ? "" : secret);
            }
            default -> {
                // NONE：不带认证
            }
        }
    }

    private int resolveTimeout(InterfaceTask task, Partner partner) {
        if (task.getTimeoutMs() != null) {
            return task.getTimeoutMs();
        }
        if (partner != null && partner.getTimeoutMs() != null) {
            return partner.getTimeoutMs();
        }
        return appProps.getPush().getTimeoutMs();
    }

    /** 回写任务最近执行状态 */
    private void updateTaskStat(InterfaceTask task, TaskLog taskLog) {
        try {
            task.setLastExecTime(taskLog.getStartTime());
            task.setLastResult(taskLog.getStatus());
            task.setLastCostMs(taskLog.getCostMs());
            taskRepository.save(task);
        } catch (Exception e) {
            log.warn("回写任务执行状态失败: {}", e.getMessage());
        }
    }

    /** 状态英文 → 人话，用于过程日志 */
    private String statusText(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case "SUCCESS" -> "成功";
            case "FAIL" -> "失败";
            case "PARTIAL" -> "部分成功";
            default -> status;
        };
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
    }

    /** 单次调用结果 */
    private static class PushOutcome {
        boolean success;
        String responseBody = "";
        String errorMsg;
        int statusCode;
    }

    @PreDestroy
    public void shutdown() {
        manualExecutor.shutdown();
    }

    /** 供页面展示的推送模式选项 */
    public static List<String> pushModes() {
        return new ArrayList<>(List.of("BATCH", "PER_ROW"));
    }
}
