package com.interchange.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interchange.platform.common.BizException;
import com.interchange.platform.common.Utils;
import com.interchange.platform.config.AppProps;
import com.interchange.platform.config.DataSourceRegistry;
import com.interchange.platform.entity.InterfaceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据获取器：负责把“要推送什么数据”取出来，支持三种来源。
 * <ul>
 *   <li>{@code SQL} —— 从本系统库（或配置的业务库）查询；</li>
 *   <li>{@code HTTP_PULL} —— 先调用本系统已有 HTTP 接口拿数；</li>
 *   <li>{@code FIXED} —— 固定报文（联调/心跳场景）。</li>
 * </ul>
 */
@Service
public class DataFetcher {

    private static final Logger log = LoggerFactory.getLogger(DataFetcher.class);

    private final DataSourceRegistry dataSourceRegistry;
    private final AppProps appProps;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public DataFetcher(DataSourceRegistry dataSourceRegistry, AppProps appProps) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.appProps = appProps;
    }

    /** 取数结果 */
    public static class FetchResult {
        /** 数据条数（FIXED 模式固定为 1） */
        private int total;
        /** 结构化数据：List<Map> 或 Map 或 String */
        private Object data;
        /** 原始文本（HTTP_PULL / FIXED 用） */
        private String rawText;
        /** 取数耗时（毫秒） */
        private long costMs;

        public FetchResult(int total, Object data, String rawText, long costMs) {
            this.total = total;
            this.data = data;
            this.rawText = rawText;
            this.costMs = costMs;
        }

        public int getTotal() {
            return total;
        }

        public Object getData() {
            return data;
        }

        public String getRawText() {
            return rawText;
        }

        public long getCostMs() {
            return costMs;
        }
    }

    public FetchResult fetch(InterfaceTask task) {
        return fetch(task, null);
    }

    /**
     * 取数。
     *
     * @param sqlOverride 自定义处理器（beforeFetch）改写的 SQL；null 表示用任务上配置的原 SQL。
     *                    只对 SQL 模式生效，仍然要做只读校验，防止处理器里写出的 UPDATE/DELETE 被执行。
     */
    public FetchResult fetch(InterfaceTask task, String sqlOverride) {
        String type = task.getSourceType() == null ? "SQL" : task.getSourceType().toUpperCase();
        long start = System.currentTimeMillis();
        switch (type) {
            case "SQL":
                return fetchBySql(task, start, sqlOverride);
            case "HTTP_PULL":
                return fetchByHttp(task, start);
            case "FIXED":
                return fetchFixed(task, start);
            default:
                throw new BizException(400, "不支持的数据来源类型: " + task.getSourceType());
        }
    }

    /** 从数据库查询取数 */
    private FetchResult fetchBySql(InterfaceTask task, long start, String sqlOverride) {
        String sql = (sqlOverride == null || sqlOverride.isBlank()) ? task.getSqlText() : sqlOverride;
        Utils.assertReadonlySql(sql);
        String dsKey = task.getDatasourceKey() == null || task.getDatasourceKey().isBlank()
                ? DataSourceRegistry.MAIN : task.getDatasourceKey();
        if (!dataSourceRegistry.exists(dsKey)) {
            throw new BizException(400, "数据源不存在: " + dsKey);
        }
        // 用独立包装的 JdbcTemplate，避免行数/超时设置污染共享实例
        JdbcTemplate template = new JdbcTemplate(dataSourceRegistry.template(dsKey).getDataSource());
        template.setMaxRows(appProps.getPush().getMaxRows());
        template.setQueryTimeout(60);

        List<Map<String, Object>> rows = template.queryForList(sql);
        long cost = System.currentTimeMillis() - start;
        if (sqlOverride != null && !sqlOverride.isBlank()) {
            log.info("任务[{}] SQL 取数完成（处理器改写 SQL），共 {} 条，耗时 {} ms",
                    task.getTaskCode(), rows.size(), cost);
        } else {
            log.info("任务[{}] SQL 取数完成，共 {} 条，耗时 {} ms", task.getTaskCode(), rows.size(), cost);
        }
        return new FetchResult(rows.size(), rows, null, cost);
    }

    /** 调用本系统 HTTP 接口取数 */
    private FetchResult fetchByHttp(InterfaceTask task, long start) {
        if (task.getPullUrl() == null || task.getPullUrl().isBlank()) {
            throw new BizException(400, "HTTP_PULL 模式必须配置拉取地址");
        }
        Map<String, String> headers = parseHeaders(task.getPullHeaders());
        headers.put("X-Trace-Id", com.interchange.platform.common.TraceId.current());

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(task.getPullUrl()))
                .timeout(Duration.ofMillis(defaultTimeout(task)));
        headers.forEach(builder::header);

        String method = task.getPullMethod() == null ? "GET" : task.getPullMethod().toUpperCase();
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString("{}"));
        } else {
            builder.GET();
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            long cost = System.currentTimeMillis() - start;
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("拉取数据失败，HTTP " + response.statusCode() + ": " + abbreviate(response.body()));
            }
            Object data = parseBody(response.body());
            int total = data instanceof List ? ((List<?>) data).size() : 1;
            return new FetchResult(total, data, response.body(), cost);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("拉取数据异常: " + e.getMessage(), e);
        }
    }

    /** 固定报文 */
    private FetchResult fetchFixed(InterfaceTask task, long start) {
        String payload = task.getFixedPayload();
        if (payload == null || payload.isBlank()) {
            throw new BizException(400, "FIXED 模式必须配置固定报文");
        }
        Object data = parseBody(payload);
        int total = data instanceof List ? ((List<?>) data).size() : 1;
        return new FetchResult(total, data, payload, System.currentTimeMillis() - start);
    }

    /** 解析 JSON；非 JSON 时按纯文本处理 */
    private Object parseBody(String text) {
        if (text == null) {
            return null;
        }
        String trim = text.trim();
        if (trim.startsWith("{") || trim.startsWith("[")) {
            try {
                return objectMapper.readValue(trim, Object.class);
            } catch (Exception e) {
                log.warn("报文不是合法 JSON，按文本处理: {}", e.getMessage());
            }
        }
        return text;
    }

    /** 解析 JSON 对象形式的请求头配置 */
    public static Map<String, String> parseHeaders(String json) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return headers;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<?, ?> map = mapper.readValue(json, Map.class);
            map.forEach((k, v) -> {
                if (k != null && v != null) {
                    headers.put(String.valueOf(k), String.valueOf(v));
                }
            });
        } catch (Exception e) {
            throw new BizException(400, "请求头配置不是合法的 JSON 对象: " + json);
        }
        return headers;
    }

    public static List<Map<String, Object>> asRowList(Object data) {
        if (data instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(o -> {
                        Map<?, ?> m = (Map<?, ?>) o;
                        Map<String, Object> row = new LinkedHashMap<>();
                        m.forEach((k, v) -> row.put(String.valueOf(k), v));
                        return row;
                    })
                    .toList();
        }
        return Collections.emptyList();
    }

    private int defaultTimeout(InterfaceTask task) {
        return task.getTimeoutMs() == null ? appProps.getPush().getTimeoutMs() : task.getTimeoutMs();
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }
}
