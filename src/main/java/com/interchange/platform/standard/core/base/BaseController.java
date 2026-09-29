package com.interchange.platform.standard.core.base;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 标准接口控制器的基类：统一包回执。
 *
 * <p>标准接口（原系统那 9 个查询接口）的响应固定是 {@code {status, message, date}}：
 * status = 2 成功、1 失败（取自原系统的 StandardConfig，别按常识改成 0/1 或 true/false），
 * 成功时 {@code date} 放数据、失败时 {@code date} 为 null。
 *
 * <p>写法参照 dyg-t6 的 {@code com.bytter.core.base.BaseController}：
 * 控制器负责包回执，业务方法只返回数据，入参不合法就抛 BusinessException。
 */
public abstract class BaseController {

    /** status：成功 */
    protected static final String STATUS_OK = "2";
    /** status：失败 */
    protected static final String STATUS_FAIL = "1";

    /** 成功回执：{@code {status:2, message:查询成功, date:数据}} */
    protected Map<String, Object> success(Object data) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", STATUS_OK);
        r.put("message", "查询成功");
        r.put("date", data);
        return r;
    }

    /** 失败回执：{@code {status:1, message:失败原因, date:null}} */
    protected Map<String, Object> fail(String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", STATUS_FAIL);
        r.put("message", message);
        r.put("date", null);
        return r;
    }
}
