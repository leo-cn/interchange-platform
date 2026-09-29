package com.interchange.platform.standard.task;

import com.interchange.platform.standard.sys.vo.ResultDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.interchange.platform.standard.task.core.TaskContext;
import com.interchange.platform.standard.task.core.Task;
import com.interchange.platform.standard.utils.JsonUtil;
import com.interchange.platform.standard.anotation.TaskInfo;
import com.interchange.platform.standard.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义处理器示例（code = demoOrder），一次性演示四类特殊处理：
 */
@Component
@TaskInfo(code = DemoOrderTask.CODE, desc = "示例：字段转换 + 自定义信封 + 签名 + 业务码判成败 + 回写")
public class DemoOrderTask implements Task {

    public static final String CODE = "demoOrder";

    private static final Logger log = LoggerFactory.getLogger(DemoOrderTask.class);

    /** 平台统一 ObjectMapper（已注册 JavaTimeModule） */
    private static final ObjectMapper MAPPER = JsonUtil.getMapper();

    /** 演示用的签名密钥；真实场景从配置或第三方系统表里取，不要硬编码 */
    private static final String SIGN_SECRET = "demo-sign-secret";

    /**
     * ② 请求体：先做字段转换（映射/脱敏/字典），再包成第三方要求的信封
     * {@code {header:{...}, payload:[...]}}。
     */
    @Override
    public String setBody(TaskContext ctx, Object data) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("bizType", "ORDER");
        header.put("source", "interchange-platform");
        header.put("traceId", ctx.getTraceId());
        header.put("sendTime", Utils.format(LocalDateTime.now()));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("header", header);
        envelope.put("payload", convert(data));
        return Utils.toJson(envelope);
    }

    /** ① 请求头：签名 + 时间戳，报文此时已确定，签的就是它 */
    @Override
    public void setHeaders(TaskContext ctx, Map<String, String> headers, String body) {
        String ts = String.valueOf(System.currentTimeMillis());
        headers.put("X-Timestamp", ts);
        headers.put("X-Sign", md5(body + ts + SIGN_SECRET));
    }

    /**
     * ③ 处理返回：判定成败 + 回写业务表。
     *
     * <p>HTTP 200 也可能是业务失败（第三方回 <code>{"code":500,"message":"..."}</code>），
     * 所以成败按 code 判定。返回 null 表示交回引擎默认规则（2xx 即成功）。
     */
    @Override
    public ResultDTO<?> handleResponse(TaskContext ctx, int httpStatus, String responseBody) {
        ResultDTO<?> judged = judgeBody(httpStatus, responseBody);
        if (ResultDTO.isOk(judged)) {
            writeBack(ctx);
        }
        return judged;
    }

    /** 字段映射、脱敏、字典转换 */
    private List<Map<String, Object>> convert(Object data) {
        if (!(data instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> origin)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            origin.forEach((k, v) -> row.put(String.valueOf(k), v));
            // 敏感字段不出去
            row.remove("password");
            row.remove("idCard");
            // 字典：状态码换成第三方认识的中文
            Object status = row.get("status");
            if (status != null) {
                row.put("statusText", "1".equals(String.valueOf(status)) ? "启用" : "停用");
            }
            out.add(row);
        }
        return out;
    }

    /** 按业务码判成败；返回 null 表示交回引擎默认规则 */
    private ResultDTO<?> judgeBody(int httpStatus, String responseBody) {
        if (httpStatus < 200 || httpStatus >= 300) {
            return ResultDTO.fail(null);
        }
        Map<String, Object> resp = Utils.toMap(responseBody);
        if (resp == null || resp.get("code") == null) {
            return null;
        }
        String code = String.valueOf(resp.get("code"));
        boolean ok = "0".equals(code) || "200".equals(code) || "SUCCESS".equalsIgnoreCase(code);
        return ok ? ResultDTO.ok()
                : ResultDTO.fail("第三方业务失败：code=" + code + "，message=" + resp.get("message"));
    }

    /**
     * 回写业务表、保存第三方返回的凭据。此处抛异常只记 WARN，不会把已送达的推送判成失败。
     *
     */
    private void writeBack(TaskContext ctx) {
        log.info("处理器[{}] 推送成功，此处可执行回写业务表（traceId={}）", CODE, ctx.getTraceId());
    }

    private static String md5(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 不可用: " + e.getMessage(), e);
        }
    }
}
