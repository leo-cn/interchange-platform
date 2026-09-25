package com.interchange.platform.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 平台通用工具：Cron 校验/预览、SQL 安全校验、文本截断、JSON 美化。
 */
public final class Utils {

    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DT_FILE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 平台统一 ObjectMapper。
     *
     * <p>必须注册 {@code JavaTimeModule}：MySQL Connector/J 8、Oracle 等驱动会把
     * DATE / DATETIME 列映射成 {@code java.time.LocalDateTime} 之类的 Java 8 时间类型，
     * 而原生 ObjectMapper 不认识它们，会直接抛 InvalidDefinitionException，
     * 导致整个报文退化成 {@code {key=value}} 这种非法 JSON（曾真实踩过）。
     *
     * <p>同时关掉 WRITE_DATES_AS_TIMESTAMPS，让时间以 ISO 字符串输出，便于对方解析。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private Utils() {
    }

    public static String format(LocalDateTime time) {
        return time == null ? "" : DT.format(time);
    }

    /** 校验 cron 表达式是否合法 */
    public static boolean isValidCron(String cron) {
        if (cron == null || cron.trim().isEmpty()) {
            return false;
        }
        return CronExpression.isValidExpression(cron.trim());
    }

    /** 校验 cron，非法则抛业务异常 */
    public static void assertCron(String cron) {
        if (!isValidCron(cron)) {
            throw new BizException(400, "Cron 表达式非法: " + cron + "（示例：0 0/5 * * * ? 表示每 5 分钟）");
        }
    }

    /** 预览接下来 n 次执行时间 */
    public static List<String> nextFireTimes(String cron, int count) {
        assertCron(cron);
        List<String> result = new ArrayList<>();
        try {
            CronExpression expression = new CronExpression(cron.trim());
            Date time = new Date();
            for (int i = 0; i < count; i++) {
                time = expression.getNextValidTimeAfter(time);
                if (time == null) {
                    break;
                }
                result.add(DT.format(LocalDateTime.ofInstant(time.toInstant(),
                        java.time.ZoneId.systemDefault())));
            }
        } catch (ParseException e) {
            throw new BizException(400, "Cron 解析失败: " + e.getMessage());
        }
        return result;
    }

    /** cron 语义摘要（英文摘要，例如 Every 5 minutes） */
    public static String cronSummary(String cron) {
        try {
            return new CronExpression(cron.trim()).getExpressionSummary()
                    .replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * SQL 安全校验：定时任务的取数 SQL 只允许查询语句，禁止任何写操作与多语句。
     * 这是防止平台被当成任意 SQL 执行入口的第一道闸门。
     */
    public static void assertReadonlySql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new BizException(400, "取数 SQL 不能为空");
        }
        String s = sql.trim().toLowerCase(Locale.ROOT);
        // 去掉结尾分号后，中间不允许再出现分号（防多语句注入）
        if (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        if (s.contains(";")) {
            throw new BizException(400, "取数 SQL 不允许多条语句");
        }
        if (!(s.startsWith("select") || s.startsWith("with"))) {
            throw new BizException(400, "取数 SQL 只允许 SELECT / WITH 查询语句");
        }
        String[] forbidden = {" insert ", " update ", " delete ", " drop ", " truncate ", " alter ",
                " create ", " merge ", " grant ", " revoke ", " execute ", " call "};
        for (String f : forbidden) {
            if (s.contains(f)) {
                throw new BizException(400, "取数 SQL 含非法关键字: " + f.trim());
            }
        }
    }

    /** 报文截断，防止超大报文撑爆数据库 */
    public static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        if (max <= 0 || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n...[报文超长，已截断，原始长度 " + text.length() + " 字符]";
    }

    /** JSON 美化，失败时原样返回 */
    public static String prettyJson(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        String t = text.trim();
        if (!(t.startsWith("{") || t.startsWith("["))) {
            return text;
        }
        try {
            Object obj = MAPPER.readValue(t, Object.class);
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * 序列化为 JSON。
     *
     * <p>两段兜底，尽量不产出非法 JSON：
     * <ol>
     *   <li>直接序列化；</li>
     *   <li>失败则把驱动私有类型（BLOB/CLOB、厂商自定义时间类型等）先转成字符串再序列化；</li>
     *   <li>仍失败才退回 {@code String.valueOf}（此时一定会在日志里留下 WARN）。</li>
     * </ol>
     */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 序列化失败，把特殊类型转成字符串后再试一次: {}", e.getMessage());
            try {
                return MAPPER.writeValueAsString(sanitize(obj));
            } catch (Exception e2) {
                log.warn("JSON 序列化最终失败，回退为 toString: {}", e2.getMessage());
                return String.valueOf(obj);
            }
        }
    }

    /**
     * 把 JSON 文本解析成 JsonNode（能解析时），否则原样返回文本。
     *
     * <p>用于把多个报文拼成数组时保持结构化：逐条推送时若把各条报文当字符串塞进数组，
     * 结果会是"字符串数组"（整体仍是合法 JSON，但内部无法按字段取值）；
     * 解析成节点后再组装，才能得到真正的对象数组。
     */
    public static Object jsonToNode(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? null : text;
        }
        String t = text.trim();
        if (!((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]")))) {
            return text;
        }
        try {
            return MAPPER.readTree(t);
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * 需要换行并大写的关键字。**长关键字必须排在前面**（LEFT JOIN 先于 JOIN），
     * 否则 "LEFT JOIN" 会被拆成 "LEFT" 与 "JOIN" 两行。
     */
    private static final String[] SQL_CLAUSES = {
            "GROUP BY", "ORDER BY", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN",
            "INSERT INTO", "DELETE FROM", "UNION ALL",
            "SELECT", "FROM", "WHERE", "HAVING", "LIMIT", "VALUES",
            "UPDATE", "JOIN", "UNION", "SET", "AND", "OR", "ON"
    };

    private static final Pattern SQL_CLAUSE_PATTERN = Pattern.compile(
            "(?i)\\s+\\b(" + String.join("|", SQL_CLAUSES).replace(" ", "\\s+") + ")\\b\\s*");

    /**
     * 粗略格式化 SQL：关键字大写、主要子句换行。
     *
     * <p>只用于**日志展示**，不做语法解析，因此字符串字面量里若出现 AND / OR
     * 之类的词也会被换行（对读日志影响不大）。生产环境若需要严格排版，
     * 建议接入 JSqlParser 之类的解析器。
     */
    public static String prettySql(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        // 前置一个空格，让位于句首的关键字（如 select ...）也能命中换行规则
        String s = " " + sql.trim().replaceAll("\\s+", " ");
        Matcher m = SQL_CLAUSE_PATTERN.matcher(s);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(s, last, m.start())
              .append('\n')
              .append(m.group(1).toUpperCase(Locale.ROOT))
              .append(' ');
            last = m.end();
        }
        sb.append(s.substring(last));
        return sb.toString().trim();
    }

    /** 把 Jackson 不认识的类型（BLOB/CLOB/厂商私有类型）转成字符串，保留可序列化部分 */
    private static Object sanitize(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), sanitize(v)));
            return out;
        }
        if (value instanceof Iterable<?> it) {
            java.util.List<Object> out = new java.util.ArrayList<>();
            it.forEach(v -> out.add(sanitize(v)));
            return out;
        }
        if (value.getClass().isArray() && !(value instanceof byte[])) {
            int len = java.lang.reflect.Array.getLength(value);
            java.util.List<Object> out = new java.util.ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                out.add(sanitize(java.lang.reflect.Array.get(value, i)));
            }
            return out;
        }
        if (value instanceof byte[] bytes) {
            return java.util.Base64.getEncoder().encodeToString(bytes);
        }
        return String.valueOf(value);
    }
}
