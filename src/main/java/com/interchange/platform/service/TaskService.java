package com.interchange.platform.service;

import com.interchange.platform.common.BizException;
import com.interchange.platform.common.Utils;
import com.interchange.platform.config.DataSourceRegistry;
import com.interchange.platform.entity.InterfaceTask;
import com.interchange.platform.job.QuartzTaskJob;
import com.interchange.platform.repository.InterfaceTaskRepository;
import com.interchange.platform.repository.PartnerRepository;
import com.interchange.platform.repository.TaskLogRepository;
import com.interchange.platform.service.handler.TaskHandlerRegistry;
import jakarta.persistence.criteria.Predicate;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务管理：增删改查 + Quartz 动态调度。
 *
 * <p>调度策略：数据库是任务定义的唯一真相，Quartz 只负责触发。
 * 应用启动时把启用状态的任务全部重新注册到 Quartz，因此即使调度器重启也不会丢任务。
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final String JOB_GROUP = "INTERCHANGE_TASK";

    private final InterfaceTaskRepository taskRepository;
    private final PartnerRepository partnerRepository;
    private final TaskLogRepository taskLogRepository;
    private final DataSourceRegistry dataSourceRegistry;
    private final Scheduler scheduler;
    private final DataFetcher dataFetcher;
    private final PushService pushService;
    private final TaskHandlerRegistry handlerRegistry;

    public TaskService(InterfaceTaskRepository taskRepository,
                       PartnerRepository partnerRepository,
                       TaskLogRepository taskLogRepository,
                       DataSourceRegistry dataSourceRegistry,
                       Scheduler scheduler,
                       DataFetcher dataFetcher,
                       PushService pushService,
                       TaskHandlerRegistry handlerRegistry) {
        this.taskRepository = taskRepository;
        this.partnerRepository = partnerRepository;
        this.taskLogRepository = taskLogRepository;
        this.dataSourceRegistry = dataSourceRegistry;
        this.scheduler = scheduler;
        this.dataFetcher = dataFetcher;
        this.pushService = pushService;
        this.handlerRegistry = handlerRegistry;
    }

    /* ===================== 查询 ===================== */

    public Page<InterfaceTask> page(String keyword, Long partnerId, Boolean enabled,
                                    int page, int size) {
        Specification<InterfaceTask> spec = (root, query, cb) -> {
            List<Predicate> list = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                list.add(cb.or(cb.like(root.get("taskCode"), like),
                        cb.like(root.get("taskName"), like)));
            }
            if (partnerId != null) {
                list.add(cb.equal(root.get("partnerId"), partnerId));
            }
            if (enabled != null) {
                list.add(cb.equal(root.get("enabled"), enabled));
            }
            return list.isEmpty() ? cb.conjunction() : cb.and(list.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id")));
    }

    public InterfaceTask get(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "任务不存在: " + id));
    }

    public InterfaceTask getByCode(String taskCode) {
        return taskRepository.findByTaskCode(taskCode)
                .orElseThrow(() -> new BizException(404, "任务不存在: " + taskCode));
    }

    /* ===================== 保存 ===================== */

    @Transactional
    public InterfaceTask save(InterfaceTask form) {
        validate(form);

        InterfaceTask entity;
        boolean isNew = form.getId() == null;
        if (isNew) {
            entity = new InterfaceTask();
        } else {
            entity = get(form.getId());
        }

        entity.setTaskCode(form.getTaskCode().trim());
        entity.setTaskName(form.getTaskName().trim());
        entity.setPartnerId(form.getPartnerId());
        entity.setSourceType(form.getSourceType());
        entity.setDatasourceKey(form.getDatasourceKey());
        entity.setSqlText(form.getSqlText());
        entity.setPullUrl(form.getPullUrl());
        entity.setPullMethod(form.getPullMethod());
        entity.setPullHeaders(form.getPullHeaders());
        entity.setFixedPayload(form.getFixedPayload());
        entity.setTargetPath(form.getTargetPath());
        entity.setHttpMethod(form.getHttpMethod());
        entity.setContentType(form.getContentType());
        entity.setPushMode(form.getPushMode());
        entity.setHeadersJson(form.getHeadersJson());
        entity.setHandlerBean(blankToNull(form.getHandlerBean()));
        entity.setCronExpression(form.getCronExpression());
        entity.setTimeoutMs(form.getTimeoutMs());
        entity.setEnabled(form.getEnabled() == null || form.getEnabled());
        entity.setRemark(form.getRemark());

        InterfaceTask saved = taskRepository.save(entity);
        syncSchedule(saved);
        return saved;
    }

    private void validate(InterfaceTask form) {
        if (form.getTaskCode() == null || form.getTaskCode().isBlank()) {
            throw new BizException(400, "任务编码不能为空");
        }
        if (!form.getTaskCode().trim().matches("^[A-Za-z0-9_\\-]{2,64}$")) {
            throw new BizException(400, "任务编码只允许字母、数字、下划线、中划线，长度 2~64");
        }
        if (form.getTaskName() == null || form.getTaskName().isBlank()) {
            throw new BizException(400, "任务名称不能为空");
        }
        if (form.getId() == null && taskRepository.existsByTaskCode(form.getTaskCode().trim())) {
            throw new BizException(400, "任务编码已存在: " + form.getTaskCode());
        }
        String cron = form.getCronExpression();
        if (form.getEnabled() == null || form.getEnabled()) {
            Utils.assertCron(cron);
        } else if (cron != null && !cron.isBlank()) {
            Utils.assertCron(cron);
        }
        if (form.getPartnerId() != null && partnerRepository.findById(form.getPartnerId()).isEmpty()) {
            throw new BizException(400, "所选第三方系统不存在");
        }

        String sourceType = form.getSourceType() == null ? "SQL" : form.getSourceType().toUpperCase();
        switch (sourceType) {
            case "SQL" -> Utils.assertReadonlySql(form.getSqlText());
            case "HTTP_PULL" -> {
                if (form.getPullUrl() == null || form.getPullUrl().isBlank()) {
                    throw new BizException(400, "HTTP_PULL 模式必须配置拉取地址");
                }
            }
            case "FIXED" -> {
                if (form.getFixedPayload() == null || form.getFixedPayload().isBlank()) {
                    throw new BizException(400, "FIXED 模式必须配置固定报文");
                }
            }
            default -> throw new BizException(400, "不支持的数据来源类型: " + form.getSourceType());
        }

        if (form.getTargetPath() == null || form.getTargetPath().isBlank()) {
            throw new BizException(400, "目标路径不能为空");
        }
        // 处理器填了就必须存在，否则保存完才发现拼错，任务每次执行都静默走默认逻辑
        String handler = blankToNull(form.getHandlerBean());
        if (handler != null && !handlerRegistry.exists(handler)) {
            throw new BizException(400, "自定义处理器不存在: " + handler
                    + "，可用值：" + handlerRegistry.codes());
        }
        // 校验请求头 JSON 合法性
        DataFetcher.parseHeaders(form.getHeadersJson());
        if ("HTTP_PULL".equals(sourceType)) {
            DataFetcher.parseHeaders(form.getPullHeaders());
        }
    }

    /* ===================== 启停 / 删除 ===================== */

    @Transactional
    public void toggle(Long id, boolean enabled) {
        InterfaceTask task = get(id);
        if (enabled) {
            Utils.assertCron(task.getCronExpression());
        }
        task.setEnabled(enabled);
        taskRepository.save(task);
        syncSchedule(task);
    }

    @Transactional
    public void delete(Long id) {
        InterfaceTask task = get(id);
        try {
            scheduler.deleteJob(jobKey(task.getTaskCode()));
        } catch (SchedulerException e) {
            log.warn("删除调度失败: {}", e.getMessage());
        }
        taskLogRepository.deleteByTaskCode(task.getTaskCode());
        taskRepository.delete(task);
        log.info("任务[{}]已删除（含历史日志）", task.getTaskCode());
    }

    /* ===================== Quartz 调度 ===================== */

    /** 按任务当前状态重建调度 */
    public void syncSchedule(InterfaceTask task) {
        String code = task.getTaskCode();
        try {
            scheduler.deleteJob(jobKey(code));
            if (Boolean.TRUE.equals(task.getEnabled()) && Utils.isValidCron(task.getCronExpression())) {
                JobDetail jobDetail = JobBuilder.newJob(QuartzTaskJob.class)
                        .withIdentity(jobKey(code))
                        .usingJobData("taskId", task.getId())
                        .usingJobData("taskCode", code)
                        .withDescription(task.getTaskName())
                        .build();
                Trigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity(TriggerKey.triggerKey("trigger-" + code, JOB_GROUP))
                        .withSchedule(CronScheduleBuilder.cronSchedule(task.getCronExpression())
                                .withMisfireHandlingInstructionDoNothing())
                        .build();
                scheduler.scheduleJob(jobDetail, trigger);
                log.info("任务[{}]已注册调度: cron={}", code, task.getCronExpression());
            }
        } catch (SchedulerException e) {
            throw new BizException("注册调度失败: " + e.getMessage());
        }
    }

    /** 应用启动时重建全部调度 */
    public void reloadAllSchedules() {
        List<InterfaceTask> tasks = taskRepository.findByEnabledOrderByIdAsc(true);
        int count = 0;
        for (InterfaceTask task : tasks) {
            if (Utils.isValidCron(task.getCronExpression())) {
                syncSchedule(task);
                count++;
            } else {
                log.warn("任务[{}] cron 非法，跳过调度注册: {}", task.getTaskCode(), task.getCronExpression());
            }
        }
        log.info("调度器初始化完成，已注册 {} 个启用任务", count);
    }

    public void runNow(Long id, String operator) {
        InterfaceTask task = get(id);
        log.info("用户[{}]手工触发任务[{}]", operator, task.getTaskCode());
        // 异步推送，立刻返回，执行结果到日志页查看
        pushService.executeAsync(id, "MANUAL");
    }

    /** 任务下次执行时间预览 */
    public List<String> previewNextFireTimes(String cron, int count) {
        return Utils.nextFireTimes(cron, count);
    }

    /** 数据预览：按任务配置取前 N 条数据，用于配置页“试跑” */
    public Map<String, Object> previewData(InterfaceTask form, int limit) {
        InterfaceTask temp = new InterfaceTask();
        temp.setTaskCode(form.getTaskCode() == null ? "PREVIEW" : form.getTaskCode());
        temp.setTaskName("预览");
        temp.setSourceType(form.getSourceType());
        temp.setDatasourceKey(form.getDatasourceKey());
        temp.setSqlText(form.getSqlText());
        temp.setPullUrl(form.getPullUrl());
        temp.setPullMethod(form.getPullMethod());
        temp.setPullHeaders(form.getPullHeaders());
        temp.setFixedPayload(form.getFixedPayload());
        temp.setTimeoutMs(form.getTimeoutMs());

        DataFetcher.FetchResult result = dataFetcher.fetch(temp);
        List<Map<String, Object>> rows = DataFetcher.asRowList(result.getData());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", result.getTotal());
        out.put("costMs", result.getCostMs());
        out.put("data", rows.size() > limit ? rows.subList(0, limit) : rows);
        out.put("rawText", rows.isEmpty() ? result.getRawText() : null);
        out.put("truncated", rows.size() > limit);
        return out;
    }

    public List<Map<String, String>> datasourceOptions() {
        return dataSourceRegistry.listOptions();
    }

    /** 页面上可选的自定义处理器：标识 + 说明 */
    public List<Map<String, String>> handlerOptions() {
        List<Map<String, String>> options = new ArrayList<>();
        handlerRegistry.descriptions().forEach((code, desc) -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("code", code);
            item.put("desc", desc == null || desc.isBlank() ? "无说明" : desc);
            options.add(item);
        });
        return options;
    }

    private static String blankToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private JobKey jobKey(String taskCode) {
        return JobKey.jobKey("job-" + taskCode, JOB_GROUP);
    }
}
