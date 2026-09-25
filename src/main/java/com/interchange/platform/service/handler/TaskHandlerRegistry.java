package com.interchange.platform.service.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 任务处理器注册表：把容器里所有 {@link TaskHandler} 收集起来，按标识查找。
 *
 * <p>任务上填的 handler_bean 支持两种写法，都忽略大小写：
 * <ul>
 *   <li>处理器的 {@link TaskHandler#code()}（推荐，重命名类不影响配置）</li>
 *   <li>Spring Bean 名（即类名首字母小写）</li>
 * </ul>
 */
@Component
public class TaskHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(TaskHandlerRegistry.class);

    private final Map<String, TaskHandler> byKey = new LinkedHashMap<>();
    private final Map<String, String> descriptionByCode = new LinkedHashMap<>();

    public TaskHandlerRegistry(ApplicationContext context) {
        Map<String, TaskHandler> beans = context.getBeansOfType(TaskHandler.class);
        beans.forEach((beanName, handler) -> {
            byKey.put(norm(beanName), handler);
            String code = handler.code();
            if (code != null && !code.isBlank()) {
                byKey.put(norm(code), handler);
                descriptionByCode.put(code.trim(), handler.description());
            }
        });
        if (beans.isEmpty()) {
            log.info("未注册任何任务处理器，所有任务走默认推送逻辑");
        } else {
            log.info("已注册 {} 个任务处理器: {}", beans.size(), descriptionByCode.keySet());
        }
    }

    /** 按标识取处理器；为空或找不到返回 null（走默认逻辑） */
    public TaskHandler find(String handlerBean) {
        if (handlerBean == null || handlerBean.isBlank()) {
            return null;
        }
        return byKey.get(norm(handlerBean));
    }

    public boolean exists(String handlerBean) {
        return find(handlerBean) != null;
    }

    /** 页面上可选的处理器标识列表 */
    public List<String> codes() {
        return new ArrayList<>(descriptionByCode.keySet());
    }

    /** 页面上展示用：标识 → 说明 */
    public Map<String, String> descriptions() {
        return new LinkedHashMap<>(descriptionByCode);
    }

    private static String norm(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
