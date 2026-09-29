package com.interchange.platform.standard.utils;

import com.interchange.platform.standard.exception.BizException;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 平台杂项工具：Cron 校验/预览、SQL 安全校验与格式化、文本截断。
 *
 * <p>字符串 / 日期 / JSON 能力分别见 {@link StringUtil}、{@link DateUtil}、{@link JsonUtil}
 * （命名与 dyg-erp 的 {@code com.byttersoft.framework.util.*} 对齐）。
 * 本类只保留不便归类的那几项，新代码请优先用那几个工具类。
 */
public final class Utils {

    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    /** 时间格式，转发到 {@link DateUtil} 以免调用点大改 */
    public static final String DT_PATTERN = DateUtil.FORMAT_DATETIME;

    private Utils() {
    }

    /* ===================== Cron ===================== */

    /** 校验 cron 表达式是否合法 */
    public static boolean isValidCron(String cron) {
        if (StringUtil.isBlank(cron)) {
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
                result.add(DateUtil.formatDate(time));
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

    /* ===================== SQL ===================== */

    /**
     * SQL 安全校验：定时任务的取数 SQL 只允许查询语句，禁止任何写操作与多语句。
     * 这是防止平台被当成任意 SQL 执行入口的第一道闸门。
     */
    public static void assertReadonlySql(String sql) {
        if (StringUtil.isBlank(sql)) {
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

    /** 表名白名单：防止配置里塞进奇怪的东西被拼进 SQL */
    public static boolean isSafeIdentifier(String name) {
        return name != null && SAFE_IDENTIFIER.matcher(name.trim()).matches();
    }

    private static final Pattern SAFE_IDENTIFIER =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?$");

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
        if (StringUtil.isBlank(sql)) {
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

    /* ===================== 文本 ===================== */

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

    /* ===================== 兼容转发（新代码请直接用 JsonUtil / DateUtil / StringUtil） ===================== */

    /** @deprecated 用 {@link JsonUtil#ObjToJson(Object)} */
    @Deprecated
    public static String toJson(Object obj) {
        return JsonUtil.ObjToJson(obj);
    }

    /** @deprecated 用 {@link JsonUtil#toNode(String)} */
    @Deprecated
    public static Object jsonToNode(String text) {
        return JsonUtil.toNode(text);
    }

    /** @deprecated 用 {@link JsonUtil#toMap(String)} */
    @Deprecated
    public static java.util.Map<String, Object> toMap(String text) {
        return JsonUtil.toMap(text);
    }

    /** @deprecated 用 {@link JsonUtil#pretty(String)} */
    @Deprecated
    public static String prettyJson(String text) {
        return JsonUtil.pretty(text);
    }

    /** @deprecated 用 {@link DateUtil#formatDate(java.util.Date)} */
    @Deprecated
    public static String format(LocalDateTime time) {
        return DateUtil.formatDate(DateUtil.toTimestamp(time));
    }
}
