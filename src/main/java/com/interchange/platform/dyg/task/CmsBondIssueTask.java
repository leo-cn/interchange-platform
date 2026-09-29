package com.interchange.platform.dyg.task;

import com.interchange.platform.dyg.constant.OaConstant;
import com.interchange.platform.standard.anotation.TaskInfo;
import com.interchange.platform.standard.sys.vo.ResultDTO;
import com.interchange.platform.standard.task.core.Task;
import com.interchange.platform.standard.task.core.TaskContext;
import com.interchange.platform.standard.utils.JsonUtil;
import com.interchange.platform.standard.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedCaseInsensitiveMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 债券业务审批单推送：把资金系统的债券审批单推给集团 OA 走审批流。
 *
 */
@Component
@TaskInfo(code = "cmsBondIssue", desc = "债券业务审批单推送：报文转 OA 审批单 + 按 resultCode 判成败")
public class CmsBondIssueTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(CmsBondIssueTask.class);

    /**
     * 请求头：OA 要求每次请求带一个 {@code requestId}（原 {@code postOaRequest} 里生成的），
     */
    @Override
    public void setHeaders(TaskContext ctx, Map<String, String> headers, String body) {
        headers.put("requestId", ctx.getTraceId());
    }

    /**
     * 报文：把取数结果里的一行（一张债券审批单）转成 OA 审批单结构。
     */
    @Override
    public String setBody(TaskContext ctx, Object data) {
        if (!(data instanceof Map<?, ?> row)) {
            log.warn("债券审批单推送：取数结果不是一行数据，本条按默认信封发送");
            return null;
        }
        // 列名大小写由数据库驱动决定（Oracle 大写、MySQL 按 SQL 写法、PG/H2 小写），
        // 用大小写不敏感的 Map，SQL 别名怎么写都能取到值
        Map<String, Object> bill = new LinkedCaseInsensitiveMap<>(16);
        row.forEach((k, v) -> bill.put(String.valueOf(k), v));

        String billCode = StringUtil.str(bill, "billCode");
        // 记到上下文：推送成功后回执环节要用（TaskContext 就是给跨环节传值用的）
        ctx.attr(OaConstant.ATTR_BILL_CODE, billCode);

        // 单据来源信息：srcBillCode 是资金系统单号，data 是业务字段（ERP 里是 OaApproveBaseVO 子类）
        Map<String, Object> srcBillInfo = new LinkedHashMap<>();
        srcBillInfo.put("srcBillCode", billCode);
        srcBillInfo.put("data", bill);

        // 目标系统：ERP 里只发集团 OA，模板编码原先从字典取
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("system", OaConstant.OA_SYSTEM_JT);
        target.put("templateCode", OaConstant.TEMPLATE_CMS_BOND);

        // 单据信息
        Map<String, Object> billInfo = new LinkedHashMap<>();
        billInfo.put("subject", OaConstant.TITLE_PREFIX + billCode);
        billInfo.put("billCreatorCode", StringUtil.str(bill, "applyUser"));
        billInfo.put("fields", fields(bill));
        billInfo.put("sub", List.of());
        billInfo.put("postscript", StringUtil.str(bill, "postscript"));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("source", OaConstant.OA_SOURCE);
        request.put("srcBillInfo", srcBillInfo);
        request.put("target", List.of(target));
        request.put("billInfo", billInfo);
        return JsonUtil.ObjToJson(request);
    }

    /**
     * 回执：OA 返回 {@code {resultCode, resultMsg}}，只有 resultCode 等于成功码才算成功
     */
    @Override
    public ResultDTO<?> handleResponse(TaskContext ctx, int httpStatus, String responseBody) {
        if (httpStatus < 200 || httpStatus >= 300) {
            return ResultDTO.fail(null);          // 交回引擎，失败文案用 "HTTP xxx: 响应摘要"
        }
        Map<String, Object> res = JsonUtil.toMap(responseBody);
        if (res == null) {
            return null;                          // 不是约定的 JSON，按 HTTP 状态码兜底
        }
        String resultCode = StringUtil.str(res, "resultCode");
        String resultMsg = StringUtil.str(res, "resultMsg");
        if (!OaConstant.RESULT_SUCCESS.equals(resultCode)) {
            log.warn("债券业务审批单推送失败：srcBillCode={}, resultCode={}, resultMsg={}",
                    ctx.attr(OaConstant.ATTR_BILL_CODE), resultCode, resultMsg);
            return ResultDTO.fail("OA 返回失败：resultCode=" + resultCode + "，resultMsg=" + resultMsg);
        }
        log.info("债券业务审批单推送成功：srcBillCode={}, resultMsg={}",
                ctx.attr(OaConstant.ATTR_BILL_CODE), resultMsg);
        return ResultDTO.ok();
    }

    /**
     * OA 表单字段：ERP 里由 按目标系统把业务字段映射成 OA 字段，
     */
    private static List<Map<String, Object>> fields(Map<String, Object> bill) {
        List<Map<String, Object>> fields = new ArrayList<>(bill.size());
        bill.forEach((name, value) -> {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("fieldName", name);
            field.put("data", value == null ? List.of() : List.of(value));
            field.put("system", List.of(OaConstant.OA_SYSTEM_JT));
            fields.add(field);
        });
        return fields;
    }
}
