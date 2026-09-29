package com.interchange.platform.standard.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 字符串工具。
 *
 * <p>方法命名与 dyg-erp 的 {@code com.byttersoft.framework.util.StringUtil} 对齐
 * （{@code isBlank} / {@code isEmpty} / {@code formatNull} / {@code formatInt} /
 * {@code formatTimestamp} / {@code stringToDate} / {@code split} / {@code join} 等），
 * 便于对照原工程理解；平台侧额外补了 {@code str} / {@code isTrue} / {@code uuid} /
 * {@code reason} 这几个原工程没有、但本平台大量使用的方法。
 *
 * <p>业务类里不要再自己写这类小方法。
 */
public class StringUtil {

    /** 「是」的各种写法：1 / Y / true / yes / 是 */
    private static final String[] TRUE_VALUES = {"1", "y", "true", "yes", "是"};

    /** 原工程中用来表示空值的字面量 */
    private static final String NULL_STR = "null";

    protected StringUtil() {
    }

    /* ===================== 判空（与 ERP 同名同语义） ===================== */

    /**
     * 是否为空。null、纯空白、以及字面量 "null" 都算空。
     *
     * <p>沿用原工程口径：比 {@code String#isBlank} 多认一个 "null" 字符串 ——
     * 这是和外部系统对接时非常常见的脏数据。
     */
    public static boolean isBlank(String str) {
        if (str == null) {
            return true;
        }
        if (str.trim().isEmpty()) {
            return true;
        }
        return NULL_STR.equals(str);
    }

    /** 是否为空 */
    public static boolean isEmpty(String str) {
        return isBlank(str);
    }

    /** 是否不为空 */
    public static boolean isNotEmpty(String str) {
        return !isBlank(str);
    }

    /** 是否不为空 */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /** 空值转空串（原工程 formatNull） */
    public static String formatNull(String str) {
        return isBlank(str) ? "" : str;
    }

    /** 去空格，为空返回 null */
    public static String trim(String str) {
        if (isBlank(str)) {
            return null;
        }
        return str.trim();
    }

    /* ===================== 转换（与 ERP 同名同语义） ===================== */

    /**
     * 转 int，失败返回默认值。
     * 原工程 {@code formatInt(String, int)} 遇到非法值会抛异常，这里改成返回默认值，
     * 因为报文里的脏数据不应该中断整批接收。
     */
    public static int formatInt(String str, int defaultValue) {
        if (isBlank(str)) {
            return defaultValue;
        }
        try {
            return new java.math.BigDecimal(str.trim()).intValue();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static int formatInt(String str) {
        return formatInt(str, 0);
    }

    /** 转 double，兼容 null 与脏数据（原工程 convertToDouble） */
    public static double convertToDouble(String str) {
        if (isBlank(str)) {
            return 0d;
        }
        try {
            return Double.parseDouble(str.trim());
        } catch (Exception e) {
            return 0d;
        }
    }

    /** 是否为数字（原工程 isNumeric / isNumber） */
    public static boolean isNumeric(String str) {
        if (isBlank(str)) {
            return false;
        }
        try {
            new java.math.BigDecimal(str.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isNumber(String str) {
        return isNumeric(str);
    }

    /** 任意对象转 int：驱动可能返回 decimal / varchar，统一走字符串口径 */
    public static int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return formatInt(String.valueOf(value), defaultValue);
    }

    public static int toInt(Object value) {
        return toInt(value, 0);
    }

    /* ===================== 拆分与拼接（与 ERP 同名同语义） ===================== */

    /** 按分隔符拆分，空值返回 null（原工程 split） */
    public static String[] split(String str, String separator) {
        if (isBlank(str)) {
            return null;
        }
        return str.split(separator);
    }

    /** 数组按 format 拼接（原工程 join） */
    public static String join(Object[] arr, String format) {
        if (arr == null || arr.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object obj : arr) {
            if (sb.length() > 0) {
                sb.append(format);
            }
            sb.append(obj);
        }
        return sb.toString();
    }

    /** 去掉末尾字符（原工程 deleteLastChar） */
    public static String deleteLastChar(String str) {
        if (isBlank(str)) {
            return str;
        }
        return str.substring(0, str.length() - 1);
    }

    /**
     * 按显示宽度截断，一个汉字算 2 个宽度（原工程 formatString）。
     * 用于对接方对字段长度有硬限制的场景。
     */
    public static String formatString(String str, int length) {
        if (isBlank(str) || length <= 0) {
            return str;
        }
        int width = 0;
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            width += (c > 255) ? 2 : 1;
            if (width > length) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /* ===================== 日期（与 ERP 同名同语义，实现在 DateUtil） ===================== */

    /** 字符串转 Date，需给出格式（原工程 stringToDate） */
    public static Date stringToDate(String str, String format) {
        return DateUtil.getDateByString(str, format);
    }

    /** 按格式转 Date（原工程 formatDate(String) 的语义） */
    public static Date formatDate(String str, String format) {
        return DateUtil.getDateByString(str, format);
    }

    /** 字符串转 Timestamp，兼容常见的几种格式；解析不出返回 null */
    public static java.sql.Timestamp formatTimestamp(String str) {
        return DateUtil.parseToTimestamp(str);
    }

    /* ===================== 平台扩展 ===================== */

    /** 从 Map 行里按列名取字符串值 */
    public static String str(Map<String, Object> row, String col) {
        if (row == null || col == null) {
            return null;
        }
        Object v = row.get(col);
        return v == null ? null : String.valueOf(v);
    }

    /** 从 Map 行里按列名取字符串，为空时返回默认值 */
    public static String str(Map<String, Object> row, String col, String fallback) {
        String v = str(row, col);
        return isBlank(v) ? fallback : v;
    }

    /** 宽松布尔判定：「1 / Y / true / yes / 是」都算真 */
    public static boolean isTrue(String v) {
        if (isBlank(v)) {
            return false;
        }
        String t = v.trim().toLowerCase();
        for (String s : TRUE_VALUES) {
            if (s.equals(t)) {
                return true;
            }
        }
        return false;
    }

    /** 去掉连字符的 UUID，用作各表主键 */
    public static String uuid() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /** 取异常的可读原因：优先 message，为空时退回类名 */
    public static String reason(Throwable e) {
        if (e == null) {
            return "";
        }
        String msg = e.getMessage();
        return isBlank(msg) ? e.getClass().getSimpleName() : msg;
    }

    /** 过滤空值并去重，用于批量 IN 查询 */
    public static List<String> clean(java.util.Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .filter(StringUtil::isNotBlank)
                .distinct()
                .toList();
    }

    /** 相等比较，null 安全 */
    public static boolean equals(String a, String b) {
        return Objects.equals(a, b);
    }

    /** 校验日期字符串是否符合给定格式（原工程 validateDate，严格模式） */
    public static boolean validateDate(String dateStr, String formatStr) {
        if (isBlank(dateStr) || isBlank(formatStr)) {
            return false;
        }
        DateFormat df = new SimpleDateFormat(formatStr);
        // 严格的解析：1996-13-3 不会被宽容成 1997-1-3
        df.setLenient(false);
        try {
            df.parse(dateStr);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    /** 校验 yyyyMMdd 格式的日期（原工程 validateDateyyyyMMdd） */
    public static boolean validateDateyyyyMMdd(String dateStr) {
        if (isBlank(dateStr) || dateStr.trim().length() != 8) {
            return false;
        }
        return validateDate(dateStr, "yyyyMMdd");
    }
}
