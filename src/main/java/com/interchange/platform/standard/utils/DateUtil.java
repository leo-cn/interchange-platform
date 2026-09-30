package com.interchange.platform.standard.utils;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 日期时间工具。
 *
 * <p>方法命名与 dyg-erp 的 {@code com.byttersoft.framework.util.DateUtil} /
 * {@code com.byttersoft.framework.util.StringUtil} 对齐（{@code getNow} /
 * {@code getTimeNow} / {@code getDateByString} / {@code getDateStr} /
 * {@code addDay} / {@code addMonth} / {@code dayDiffs} / {@code getMonthFirstDay} 等）。
 *
 * <p>平台侧额外补了 {@code parseToTimestamp} —— 对接系统下发的日期格式不统一，
 * 需要一个「逐个格式试」的宽松解析入口，原工程没有这个方法。
 */
public class DateUtil {

    /** yyyy-MM-dd HH:mm:ss */
    public static final String FORMAT_DATETIME = "yyyy-MM-dd HH:mm:ss";
    /** yyyy-MM-dd */
    public static final String FORMAT_DATE = "yyyy-MM-dd";
    /** yyyyMMdd */
    public static final String FORMAT_YYYYMMDD = "yyyyMMdd";
    /** yyyyMMddHHmmss */
    public static final String FORMAT_STAMP = "yyyyMMddHHmmss";

    /* ---------- 兜底解析格式 ---------- */

    /**
     * 宽松解析时依次尝试的格式，**带时间的排在前面**。
     * 若把 yyyy-MM-dd 放前面，{@code 2024-01-02 10:00:00} 会被截断成当天零点。
     */
    private static final List<String> FALLBACK_PATTERNS = Arrays.asList(
            FORMAT_DATETIME,
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            FORMAT_STAMP,
            FORMAT_DATE,
            "yyyy/MM/dd",
            FORMAT_YYYYMMDD
    );

    protected DateUtil() {
    }

    /* ===================== 当前时间（与 ERP 同名同语义） ===================== */

    /** 当前日期（java.sql.Date） */
    public static java.sql.Date getNow() {
        return new java.sql.Date(System.currentTimeMillis());
    }

    /** 当前时间戳（java.sql.Timestamp） */
    public static Timestamp getTimeNow() {
        return new Timestamp(System.currentTimeMillis());
    }

    /** 当前时间（java.util.Date） */
    public static Date getCurDate() {
        return new Date();
    }

    /** 今天零点 */
    public static LocalDateTime getTodayStart() {
        return LocalDate.now().atStartOfDay();
    }

    /* ===================== 转换（与 ERP 同名同语义） ===================== */

    /** 字符串按指定格式转 Date，解析失败返回 null */
    public static Date getDateByString(String dateStr, String format) {
        if (StringUtil.isBlank(dateStr) || StringUtil.isBlank(format)) {
            return null;
        }
        try {
            return new SimpleDateFormat(format).parse(dateStr.trim());
        } catch (ParseException e) {
            return null;
        }
    }

    /** 字符串按指定格式转 Timestamp，解析失败返回 null */
    public static Timestamp getTimestampByString(String dateStr, String format) {
        Date d = getDateByString(dateStr, format);
        return d == null ? null : new Timestamp(d.getTime());
    }

    /** Date 按格式转字符串（原工程 formatDate） */
    public static String formatDate(Date date, String format) {
        if (date == null || StringUtil.isBlank(format)) {
            return null;
        }
        return new SimpleDateFormat(format).format(date);
    }

    /** yyyy-MM-dd HH:mm:ss */
    public static String formatDate(Date date) {
        return formatDate(date, FORMAT_DATETIME);
    }

    /** yyyy-MM-dd HH:mm:ss（LocalDateTime 版，省得调用点自己 toTimestamp） */
    public static String formatDateTime(LocalDateTime time) {
        return time == null ? null : formatDate(toTimestamp(time));
    }

    /** Date 转 yyyy-MM-dd 字符串（原工程 getDateYmdStr） */
    public static String getDateYmdStr(Date date) {
        return formatDate(date, FORMAT_DATE);
    }

    /** Date 转 yyyyMMdd 字符串（原工程 getDateStrYYYYMMDD） */
    public static String getDateStrYYYYMMDD(Date date) {
        return formatDate(date, FORMAT_YYYYMMDD);
    }

    /** Timestamp 转字符串（原工程 formatTimestamp） */
    public static String formatTimestamp(Timestamp time) {
        return time == null ? null : formatDate(time, FORMAT_DATETIME);
    }

    /** 字符串转 java.sql.Date，格式 yyyy-MM-dd（原工程 formatDate(String)） */
    public static java.sql.Date formatDate(String dateStr) {
        Date d = getDateByString(dateStr, FORMAT_DATE);
        return d == null ? null : new java.sql.Date(d.getTime());
    }

    /* ===================== 计算（与 ERP 同名同语义） ===================== */

    /** 加/减天数（原工程 addDay） */
    public static Date addDay(Date date, int days) {
        if (date == null) {
            return null;
        }
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTime(date);
        c.add(java.util.Calendar.DAY_OF_MONTH, days);
        return c.getTime();
    }

    /** 加/减月份（原工程 addMonth） */
    public static Date addMonth(Date date, int months) {
        if (date == null) {
            return null;
        }
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTime(date);
        c.add(java.util.Calendar.MONTH, months);
        return c.getTime();
    }

    /** 两日期相差天数（原工程 dayDiffs） */
    public static int dayDiffs(Date from, Date to) {
        if (from == null || to == null) {
            return 0;
        }
        long diff = to.getTime() - from.getTime();
        return (int) (diff / (24L * 60 * 60 * 1000));
    }

    /** 本月第一天（原工程 getMonthFirstDay） */
    public static String getMonthFirstDay() {
        return LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ofPattern(FORMAT_DATE));
    }

    /** 本月最后一天（原工程 getMonthLastDay） */
    public static String getMonthLastDay() {
        LocalDate today = LocalDate.now();
        return today.withDayOfMonth(today.lengthOfMonth()).format(DateTimeFormatter.ofPattern(FORMAT_DATE));
    }

    /* ===================== 平台扩展 ===================== */

    /** 当前时间戳，写法更直观的别名 */
    public static Timestamp now() {
        return getTimeNow();
    }

    /** 当前时间（LocalDateTime） */
    public static LocalDateTime nowLocal() {
        return LocalDateTime.now();
    }

    /**
     * 宽松解析日期时间字符串，**逐个格式尝试**，解析不出来返回 null（不抛异常）。
     *
     * <p>对接系统（尤其 MDM）下发的日期写法不统一：有 {@code yyyy-MM-dd HH:mm:ss}、
     * 有 {@code yyyy-MM-dd}，也有带 {@code T} 的 ISO 形式甚至带毫秒。
     * 用这一入口比在业务代码里猜一个格式稳。原工程没有这个方法。
     */
    public static Timestamp parseToTimestamp(String text) {
        LocalDateTime dt = parseToLocalDateTime(text);
        return dt == null ? null : Timestamp.valueOf(dt);
    }

    /** 宽松解析成 LocalDateTime，失败返回 null */
    public static LocalDateTime parseToLocalDateTime(String text) {
        if (StringUtil.isBlank(text)) {
            return null;
        }
        String t = text.trim();
        for (String pattern : FALLBACK_PATTERNS) {
            try {
                if (pattern.length() == FORMAT_DATE.length()
                        || pattern.length() == FORMAT_YYYYMMDD.length()) {
                    // 只到日期的格式补成当天零点
                    return LocalDate.parse(t, DateTimeFormatter.ofPattern(pattern)).atStartOfDay();
                }
                return LocalDateTime.parse(t, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
                // 换下一个格式继续试
            }
        }
        return null;
    }

    /** LocalDateTime 转 Timestamp */
    public static Timestamp toTimestamp(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time);
    }

    /** Timestamp 转 LocalDateTime */
    public static LocalDateTime toLocalDateTime(Timestamp time) {
        return time == null ? null : time.toLocalDateTime();
    }

    /** Date 转 LocalDateTime */
    public static LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /** 校验 yyyy-MM-dd 格式（沿用原工程 validateDate，严格模式） */
    public static boolean isValidDate(String text) {
        if (StringUtil.isBlank(text)) {
            return true;
        }
        return StringUtil.validateDate(text.trim(), FORMAT_DATE);
    }
}
