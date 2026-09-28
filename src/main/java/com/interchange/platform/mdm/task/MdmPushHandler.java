package com.interchange.platform.mdm.task;

import com.interchange.platform.mdm.constant.MdmConstant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interchange.platform.common.Utils;
import com.interchange.platform.service.task.SendResult;
import com.interchange.platform.service.task.TaskContext;
import com.interchange.platform.service.task.TaskHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 推送侧处理器（任务「自定义处理器」填 {@code mdmPush}）。
 *
 * <p>作用：把 SQL 取出来的结果<b>原样序列化成 JSON 数组</b>发出去。
 * dyг-erp 的 {@code /rest/mdm/*Receive} 直接 POST 数组，不吃本平台的默认信封
 * （{@code {taskCode,traceId,total,data:[...]}}），所以这里必须换成裸数组。
 *
 * <p>因此 SQL 的别名必须<b>与 MDM 报文字段一致</b>（小驼峰），例如：
 * <pre>
 *   select md_id as "mdId", code as "code", name as "name" from mdm_bank_type
 * </pre>
 *
 * <p>带嵌套子表（客商的 bdBankaccbas、账户的 bdBankaccsub）的场景，单条 SQL 拼不出嵌套结构，
 * 请在数据源侧用能返回嵌套 JSON 的方式取数（视图、JSON 函数），或改用「逐条」模式分别推。
 *
 * <p>成败判定：dyг-erp 的回执外层 status 恒为 S，真正的结果在 responseData[].status，
 * 所以这里逐条扫一遍明细，只要有一条 E 就判本次失败，避免"看起来成功实则脏数据"。
 */
@Component
public class MdmPushHandler implements TaskHandler {

    public static final String CODE = "mdmPush";

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String description() {
        return "MDM 主数据推送：报文为裸 JSON 数组（适配 dyg-erp /rest/mdm/*Receive），并按 responseData 明细判成败";
    }

    /** 报文体：把数据序列化成 JSON 数组，不用默认信封 */
    @Override
    public String buildBody(TaskContext ctx, Object payload) {
        if (payload instanceof List<?> list) {
            return Utils.toJson(list);
        }
        if (payload instanceof Map) {
            // 单条数据也包成数组，接口签名要求是数组
            return Utils.toJson(List.of(payload));
        }
        return null;
    }

    /**
     * 按 MDM 回执的明细分项判定成败。
     *
     * @return true/false 自定义判定；null 表示用默认规则（HTTP 2xx 即成功）
     */
    @Override
    public Boolean judge(TaskContext ctx, int httpStatus, String responseBody) {
        if (httpStatus < 200 || httpStatus >= 300 || responseBody == null || responseBody.isBlank()) {
            return false;
        }
        Map<String, Object> res = parseMap(responseBody);
        if (res == null) {
            // 回执不是约定的 JSON，按 HTTP 状态码兜底
            return null;
        }
        if (res == null || !MdmConstant.S.equals(String.valueOf(res.get("status")))) {
            return false;
        }
        Object items = res.get("responseData");
        if (items instanceof List<?> list) {
            long fail = list.stream().filter(this::isFailItem).count();
            return fail == 0;
        }
        return null;
    }

    @Override
    public String failureReason(TaskContext ctx, int httpStatus, String responseBody) {
        Map<String, Object> res = parseMap(responseBody);
        if (res == null) {
            return null;
        }
        Object items = res.get("responseData");
        if (items instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("接收方返回失败明细：");
            int shown = 0;
            for (Object o : list) {
                if (!isFailItem(o) || !(o instanceof Map<?, ?> m)) {
                    continue;
                }
                if (shown > 0) {
                    sb.append("；");
                }
                sb.append(m.get("mdId")).append(": ").append(m.get("message"));
                if (++shown >= 5) {
                    sb.append(" …（仅列前 5 条）");
                    break;
                }
            }
            return sb.toString();
        }
        return String.valueOf(res.get("message"));
    }

    /** 推送成功后记一笔（便于在接口日志里看到真实落到对方的条数） */
    @Override
    public void afterSend(TaskContext ctx, SendResult result) {
        // 回写/标记逻辑按项目需要在此扩展；此处不落库，避免重复推送语义发生变化
    }

    private boolean isFailItem(Object o) {
        return o instanceof Map<?, ?> m && MdmConstant.E.equals(String.valueOf(m.get("status")));
    }

    /** 解析回执；不是合法 JSON 或不是对象结构时返回 null */
    private Map<String, Object> parseMap(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            Object node = mapper.readValue(text.trim(), Map.class);
            return node instanceof Map ? cast(node) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }
}
