package com.interchange.platform.standard.task.core;

import com.interchange.platform.standard.anotation.TaskInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 任务注册表：把容器里所有 {@link Task} 实现按 Bean 名与 {@link TaskInfo#code()} 双键登记，
 * 供 {@code PushService} 在执行时查找、"任务编辑"页面下拉展示。
 */
@Component
public class TaskRegistry {

    private static final Logger log = LoggerFactory.getLogger(TaskRegistry.class);

    @Resource
    private ApplicationContext context;

    private final Map<String, Task> byKey = new LinkedHashMap<>();
    private final Map<String, String> descriptionByCode = new LinkedHashMap<>();

    @PostConstruct
    void initTasks() {
        Map<String, Task> beans = context.getBeansOfType(Task.class);
        beans.forEach((beanName, task) -> {
            byKey.put(norm(beanName), task);
            // 同 ReceiveDispatchService：任务若被 AOP 代理（带 @Transactional 等），
            // 注解只在业务类上，代理子类读不到，必须回到业务类
            Class<?> targetClass = AopProxyUtils.ultimateTargetClass(task);
            TaskInfo info = AnnotationUtils.findAnnotation(targetClass, TaskInfo.class);
            if (info == null) {
                log.warn("任务 {} 缺少 @TaskInfo 注解，只能用 Bean 名 {} 引用",
                        targetClass.getSimpleName(), beanName);
                return;
            }
            byKey.put(norm(info.code()), task);
            descriptionByCode.put(info.code().trim(), info.desc());
        });
        if (beans.isEmpty()) {
            log.info("未注册任何任务，所有任务走默认推送逻辑");
        } else {
            log.info("已注册 {} 个任务: {}", beans.size(), descriptionByCode.keySet());
        }
    }

    /** 按标识取任务；为空或找不到返回 null（走默认逻辑） */
    public Task find(String handlerBean) {
        if (handlerBean == null || handlerBean.isBlank()) {
            return null;
        }
        return byKey.get(norm(handlerBean));
    }

    public boolean exists(String handlerBean) {
        return find(handlerBean) != null;
    }

    /** 页面上可选的任务标识列表 */
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
