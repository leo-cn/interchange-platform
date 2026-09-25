package com.interchange.platform.service.handler;

import java.util.Map;

/**
 * 任务处理器：单个任务的“特殊逻辑”挂靠点。
 *
 * <p>接口方法<b>全部是 default 空实现</b>，只需重写真正特殊的那一两个方法：
 * <ul>
 *   <li>{@code null} 返回值一律表示“沿用引擎默认行为”；</li>
 *   <li>不配置或配置为空格时，引擎行为与没有这套机制时<b>完全一致</b>。</li>
 * </ul>
 *
 * <pre>
 *   取数前 → beforeFetch      改取数条件（增量、按批次）
 *   取数后 → transform        字段映射 / 字典转换 / 过滤
 *   组装时 → buildBody        自定义报文结构
 *   发送前 → beforeSend       加签名、动态请求头
 *   判定   → judge            按响应体业务码判成败（HTTP 200 也可能是业务失败）
 *   发送后 → afterSend        回写业务表 / 通知（抛异常不会影响本次推送的成败判定）
 * </pre>
 *
 * <p>用法：写一个 Spring Bean 实现本接口，在任务配置的「自定义处理器」里填
 * {@link #code()}（填 Spring Bean 名也认）。
 */
public interface TaskHandler {

    /** 处理器标识，对应 interface_task.handler_bean */
    String code();

    /** 处理器用途说明，页面上鼠标悬停时展示 */
    default String description() {
        return "";
    }

    /**
     * 取数前调用。典型用法：给 SQL 补增量条件、按批次号过滤。
     *
     * <pre>
     *     ctx.setSqlOverride("select ... from biz_order where gmt_create &gt; '2026-09-01'");
     * </pre>
     *
     * 只支持改写 SQL 模式的取数语句；HTTP_PULL / FIXED 模式忽略。
     */
    default void beforeFetch(TaskContext ctx) {
    }

    /**
     * 取数后、组装报文前调用。返回值会替换掉原始数据。
     * 典型用法：字段名映射、枚举转中文、剔除敏感字段、时间格式化。
     *
     * @param data 原始数据：List&lt;Map&gt;（SQL / 集合型）或 Map / String
     * @return 转换后的数据；返回 null 表示保持原样
     */
    default Object transform(TaskContext ctx, Object data) {
        return data;
    }

    /**
     * 组装报文。返回值非 null 时用它作为请求体，否则用引擎默认信封。
     *
     * @param payload 整批模式为全部数据（List&lt;Map&gt;），逐条模式为单条数据（Map）
     * @return 报文字符串；返回 null 表示用默认信封
     */
    default String buildBody(TaskContext ctx, Object payload) {
        return null;
    }

    /**
     * 发送前调用，可以往 headers 里塞签名、时间戳等动态值。
     * 此时报文体已确定，签名通常就是签它。
     *
     * <p>注意：headers 里放的 key 会覆盖第三方系统级与任务级同名请求头；
     * X-Trace-Id 由引擎在之后写入，不会被覆盖。
     */
    default void beforeSend(TaskContext ctx, Map<String, String> headers, String body) {
    }

    /**
     * 判定本次调用是否成功。
     *
     * @return true/false 自定义判定；返回 null 表示用默认规则（HTTP 2xx 即成功）
     */
    default Boolean judge(TaskContext ctx, int httpStatus, String responseBody) {
        return null;
    }

    /**
     * judge 判定为失败时的人话原因。留空则引擎回退为 "HTTP xxx: 响应摘要"。
     */
    default String failureReason(TaskContext ctx, int httpStatus, String responseBody) {
        return null;
    }

    /**
     * 单次调用结束后调用（整批模式调 1 次，逐条模式每条调 1 次）。
     * 典型用法：成功后把业务表标记为已推送、保存第三方返回的凭据。
     *
     * <p>这里抛出的异常<b>只会记一条 WARN 日志，不会把本次推送判成失败</b>——
     * 数据已经送到第三方了，回写出错不该让调度器再推一遍造成重复。
     */
    default void afterSend(TaskContext ctx, SendResult result) {
    }
}
