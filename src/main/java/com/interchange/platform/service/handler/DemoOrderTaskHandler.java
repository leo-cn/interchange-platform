package com.interchange.platform.service.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.interchange.platform.common.Utils;
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
 *
 * <ol>
 *   <li>字段转换 / 剔除敏感字段 —— {@link #transform}</li>
 *   <li>自定义报文结构 —— {@link #buildBody}</li>
 *   <li>签名请求头 —— {@link #beforeSend}</li>
 *   <li>按响应体业务码判成败 —— {@link #judge} / {@link #failureReason}</li>
 *   <li>推送后回写业务表 —— {@link #afterSend}</li>
 * </ol>
 *
 * <p>用法：在任务编辑页「自定义处理器」里填 <code>demoOrder</code>。
 * 没有任务配置它时，这个类完全不参与任何执行——老任务零影响。
 *
 * <p>真实项目里建议按第三方系统各写一个处理器类（例如 SapOrderHandler、
 * WmsStockHandler），不要往这一个类里堆 if-else。
 */
@Component
public class DemoOrderTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(DemoOrderTaskHandler.class);

    /** 任何 new ObjectMapper() 都要带上 JavaTimeModule，否则序列化 LocalDateTime 直接抛异常 */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /** 演示用的签名密钥；真实场景从配置或第三方系统表里取，不要硬编码 */
    private static final String SIGN_SECRET = "demo-sign-secret";

    @Override
    public String code() {
        return "demoOrder";
    }

    @Override
    public String description() {
        return "示例：字段转换 + 自定义信封 + 签名 + 业务码判成败 + 回写";
    }

    /** ① 取数后：字段映射、字典转换、剔除敏感字段 */
    @Override
    public Object transform(TaskContext ctx, Object data) {
        if (!(data instanceof List<?> list)) {
            return data;
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

    /** ② 自定义信封：第三方要 {header:{...}, payload:[...]} 这种结构时用 */
    @Override
    public String buildBody(TaskContext ctx, Object payload) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("bizType", "ORDER");
        header.put("source", "interchange-platform");
        header.put("traceId", ctx.getTraceId());
        header.put("sendTime", Utils.format(LocalDateTime.now()));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("header", header);
        envelope.put("payload", payload);
        return Utils.toJson(envelope);
    }

    /** ③ 发送前：签名 + 时间戳，报文此时已确定，签的就是它 */
    @Override
    public void beforeSend(TaskContext ctx, Map<String, String> headers, String body) {
        String ts = String.valueOf(System.currentTimeMillis());
        headers.put("X-Timestamp", ts);
        headers.put("X-Sign", md5(body + ts + SIGN_SECRET));
    }

    /**
     * ④ 判定成败：HTTP 200 也可能是业务失败（第三方回 {"code":500,"message":"..."}）。
     * 返回 null 表示交回引擎默认规则（2xx 即成功）。
     */
    @Override
    public Boolean judge(TaskContext ctx, int httpStatus, String responseBody) {
        if (httpStatus < 200 || httpStatus >= 300) {
            return false;
        }
        Map<String, Object> resp = parse(responseBody);
        if (resp == null || resp.get("code") == null) {
            return null;
        }
        String code = String.valueOf(resp.get("code"));
        return "0".equals(code) || "200".equals(code) || "SUCCESS".equalsIgnoreCase(code);
    }

    /** ④ 判定失败时给出人话原因，会写进任务日志的 errorMsg */
    @Override
    public String failureReason(TaskContext ctx, int httpStatus, String responseBody) {
        Map<String, Object> resp = parse(responseBody);
        if (resp == null) {
            return null;
        }
        return "第三方业务失败：code=" + resp.get("code") + "，message=" + resp.get("message");
    }

    /**
     * ⑤ 推送后：回写业务表、保存第三方返回的凭据。
     * 这里抛异常只记 WARN，不会把已送达的这次推送判成失败。
     */
    @Override
    public void afterSend(TaskContext ctx, SendResult result) {
        if (!result.isSuccess()) {
            log.info("处理器[{}] 第 {} 条推送失败，跳过回写：{}",
                    code(), result.getIndex(), result.getErrorMsg());
            return;
        }
        // 示例只打印日志，避免一挂上处理器就把业务表改了。
        // 真实回写这样写（注入数据源后按自己的业务库来）：
        //
        //   private final DataSourceRegistry dataSourceRegistry;   // 构造注入
        //   JdbcTemplate t = new JdbcTemplate(dataSourceRegistry.template("biz").getDataSource());
        //   t.update("update biz_order set push_flag = 1, push_time = now() where order_no = ?", orderNo);
        //
        // 需要拿到 orderNo：在 transform 里把主键放进 ctx.attr("bizKeys", List.of(...))，
        // 这里用 result.getIndex() 按下标取即可（整批模式 index 恒为 1）。
        log.info("处理器[{}] 第 {}/{} 条推送成功（httpStatus={}，耗时 {}ms），此处可执行回写业务表",
                code(), result.getIndex(), result.getTotal(), result.getHttpStatus(), result.getCostMs());
    }

    private static Map<String, Object> parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trim = text.trim();
        if (!trim.startsWith("{")) {
            return null;
        }
        try {
            return MAPPER.readValue(trim, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return null;
        }
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
