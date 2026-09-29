package com.interchange.platform.standard.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 工具。
 *
 * <p>方法命名与 dyg-erp 的 {@code com.byttersoft.util.json.JsonUtil} 对齐
 * （{@code ObjToJson} / {@code objArrayToJson} / {@code jsonToObj} / {@code jsonToObjArray}）。
 * 原工程基于 {@code net.sf.json}，本平台统一用 Jackson（Spring Boot 自带），
 * 因此只是**名字对齐、实现换成 Jackson**。
 *
 * <p>业务类里不要再自己 {@code new ObjectMapper()}。
 */
public class JsonUtil {

    private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);

    /**
     * 平台唯一 ObjectMapper。
     *
     * <p>必须注册 {@code JavaTimeModule}：MySQL Connector/J 8、Oracle 等驱动会把
     * DATE / DATETIME 列映射成 {@code java.time.LocalDateTime} 之类的 Java 8 时间类型，
     * 而原生 ObjectMapper 不认识它们，会直接抛 InvalidDefinitionException，
     * 导致整个报文退化成 {@code {key=value}} 这种非法 JSON。
     *
     * <p>同时关掉 WRITE_DATES_AS_TIMESTAMPS，让时间以 ISO 字符串输出，便于对方解析。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    protected JsonUtil() {
    }

    /** 平台统一 ObjectMapper（只读使用，不要改它的配置） */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /* ===================== 序列化（与 ERP 同名） ===================== */

    /**
     * 对象转 JSON 字符串（原工程 ObjToJson）。
     *
     * <p>两段兜底，尽量不产出非法 JSON：
     * <ol>
     *   <li>直接序列化；</li>
     *   <li>失败则把驱动私有类型（BLOB/CLOB、厂商自定义类型等）先转成字符串再序列化；</li>
     *   <li>仍失败才退回 {@code String.valueOf}（此时一定会在日志里留下 WARN）。</li>
     * </ol>
     */
    public static String ObjToJson(Object obj) {
        if (obj == null) {
            return "";
        }
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

    /** 数组转 JSON 数组字符串（原工程 objArrayToJson） */
    public static String objArrayToJson(Object[] obj) {
        if (obj == null || obj.length == 0) {
            return "[]";
        }
        return ObjToJson(java.util.Arrays.asList(obj));
    }

    /* ===================== 反序列化（与 ERP 同名） ===================== */

    /** JSON 字符串填充到既有对象上（原工程 jsonToObj），失败抛异常 */
    public static void jsonToObj(Object obj, String jsonStr) {
        if (obj == null || StringUtil.isBlank(jsonStr)) {
            return;
        }
        try {
            MAPPER.readerForUpdating(obj).readValue(jsonStr);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败: " + e.getMessage(), e);
        }
    }

    /** JSON 字符串转指定类型对象（原工程 jsonToObjArray 的单对象版本） */
    public static <T> T jsonToObj(String jsonStr, Class<T> type) {
        try {
            return MAPPER.readValue(jsonStr, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败: " + e.getMessage(), e);
        }
    }

    /** JSON 数组字符串转 List（原工程 jsonToObjArray） */
    public static <T> List<T> jsonToObjArray(Class<T> clazz, String jsonArray) {
        try {
            return MAPPER.readValue(jsonArray,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败: " + e.getMessage(), e);
        }
    }

    /* ===================== 平台扩展 ===================== */

    /**
     * JSON 解析成 Map；文本为空、不是对象结构、或解析失败时返回 null。
     * 用于「必须是 JSON 对象，否则没有信息可用」的判定场景。
     */
    public static Map<String, Object> toMap(String text) {
        if (StringUtil.isBlank(text)) {
            return null;
        }
        String t = text.trim();
        if (!(t.startsWith("{") && t.endsWith("}"))) {
            return null;
        }
        try {
            return MAPPER.readValue(t, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * JSON 对象解析成「字段名 → 字符串值」的 Map，取值统一转成字符串（null 保持 null）。
     * 文本为空、不是对象结构、或解析失败时返回 null —— 判定规则与 {@link #toMap} 一致。
     *
     * <p>用于入参就是一整段 JSON、字段值全是字符串语义的接口（如标准查询接口）。
     */
    public static Map<String, String> toStringMap(String text) {
        Map<String, Object> raw = toMap(text);
        if (raw == null) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>();
        raw.forEach((k, v) -> map.put(k, v == null ? null : String.valueOf(v)));
        return map;
    }

    /**
     * JSON 解析成 JsonNode，解析不了时原样返回文本。
     *
     * <p>用于把多个报文拼成数组时保持结构化：逐条推送时若把各条报文当字符串塞进数组，
     * 结果会是"字符串数组"（整体仍是合法 JSON，但内部无法按字段取值）；
     * 解析成节点后再组装，才能得到真正的对象数组。
     */
    public static Object toNode(String text) {
        if (StringUtil.isBlank(text)) {
            return text;
        }
        String t = text.trim();
        boolean looksLikeJson = (t.startsWith("{") && t.endsWith("}"))
                || (t.startsWith("[") && t.endsWith("]"));
        if (!looksLikeJson) {
            return text;
        }
        try {
            return MAPPER.readTree(t);
        } catch (Exception e) {
            return text;
        }
    }

    /** JSON 美化，失败时原样返回 */
    public static String pretty(String text) {
        if (StringUtil.isBlank(text)) {
            return text;
        }
        String t = text.trim();
        if (!(t.startsWith("{") || t.startsWith("["))) {
            return text;
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(MAPPER.readValue(t, Object.class));
        } catch (Exception e) {
            return text;
        }
    }

    /* ===================== 内部 ===================== */

    /** 把 Jackson 不认识的类型（BLOB/CLOB/厂商私有类型）转成可序列化的形式 */
    private static Object sanitize(Object value) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), sanitize(v)));
            return out;
        }
        if (value instanceof Iterable<?> it) {
            List<Object> out = new ArrayList<>();
            it.forEach(v -> out.add(sanitize(v)));
            return out;
        }
        if (value instanceof byte[] bytes) {
            return java.util.Base64.getEncoder().encodeToString(bytes);
        }
        if (value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            List<Object> out = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                out.add(sanitize(java.lang.reflect.Array.get(value, i)));
            }
            return out;
        }
        return String.valueOf(value);
    }
}
