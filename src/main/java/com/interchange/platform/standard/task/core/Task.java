package com.interchange.platform.standard.task.core;

import com.interchange.platform.standard.anotation.TaskInfo;
import com.interchange.platform.standard.sys.vo.ResultDTO;

import java.util.Map;

/** 任务扩展点：实现类可干预一次调用的 3 个环节，发送由引擎完成。 */
public interface Task {

    /** 设置请求头，可放签名、时间戳等动态值；同名的系统级/任务级请求头会被覆盖 */
    default void setHeaders(TaskContext ctx, Map<String, String> headers, String body) {
    }

    /** 设置请求体；返回 null 用引擎默认信封 */
    default String setBody(TaskContext ctx, Object data) {
        return null;
    }

    /** 判定成败，成功时可回写业务表；返回 null 用默认规则（HTTP 2xx 即成功） */
    default ResultDTO<?> handleResponse(TaskContext ctx, int httpStatus, String responseBody) {
        return null;
    }
}
