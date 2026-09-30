package com.interchange.platform.dyg.task;

import com.interchange.platform.dyg.constant.MdmConstant;
import com.interchange.platform.standard.sys.vo.ResultDTO;
import com.interchange.platform.standard.task.core.TaskContext;
import com.interchange.platform.standard.task.core.Task;
import com.interchange.platform.standard.anotation.TaskInfo;
import com.interchange.platform.standard.utils.JsonUtil;
import com.interchange.platform.standard.utils.Utils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@TaskInfo(code = "mdmPush", desc = "MDM 主数据推送")
public class MdmPushTask implements Task {

    /** dyg-erp 收裸 JSON 数组，不用默认信封 */
    @Override
    public String setBody(TaskContext ctx, Object data) {
        return JsonUtil.ObjToJson(data == null ? List.of() : data);
    }

    /** MDM 回执外层恒为 S，真正的成败看 responseData 里每条的 status */
    @Override
    public ResultDTO<?> handleResponse(TaskContext ctx, int httpStatus, String responseBody) {
        if (httpStatus < 200 || httpStatus >= 300) {
            return ResultDTO.fail(null);     // 交回引擎，用 "HTTP xxx: 响应摘要"
        }
        Map<String, Object> res = JsonUtil.toMap(responseBody);
        if (res == null) {
            // 空响应体按 HTTP 状态码兜底；有内容却不是约定 JSON（多半是登录页/网关页）判失败，
            // 否则"假成功"：一条主数据都没接收，任务日志却记成功
            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }
            return ResultDTO.fail("接收方响应不是约定的 JSON（疑为登录页/网关页），原文见响应报文");
        }
        if (!MdmConstant.S.equals(String.valueOf(res.get("status")))) {
            return ResultDTO.fail("接收方返回 status=" + res.get("status")
                    + "，message=" + res.get("message"));
        }
        Object items = res.get("responseData");
        if (!(items instanceof List<?> list)) {
            return null;                     // 无明细，按 HTTP 状态码兜底
        }
        List<String> fails = list.stream()
                .filter(o -> o instanceof Map<?, ?> m
                        && MdmConstant.E.equals(String.valueOf(m.get("status"))))
                .limit(5)
                .map(o -> ((Map<?, ?>) o).get("mdId") + ": " + ((Map<?, ?>) o).get("message"))
                .toList();
        return fails.isEmpty()
                ? ResultDTO.ok()
                : ResultDTO.fail("接收方返回失败明细：" + String.join("；", fails)
                        + (list.size() > 5 ? " …" : ""));
    }
}
