package com.interchange.platform.standard.task.core;

import com.interchange.platform.standard.sys.entity.InterfaceTask;
import com.interchange.platform.standard.sys.dao.InterfaceTaskDao;
import com.interchange.platform.standard.sys.service.taskService.PushService;
import jakarta.annotation.Resource;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 定时任务调度入口：Quartz 触发的唯一落点。
 *
 * <p>不写业务：只按 jobDataMap 里的 taskId 取出任务、判断启用状态，
 * 然后交给 {@link PushService#execute(Long, String)} 执行（triggerType=CRON）。
 */
@DisallowConcurrentExecution
public class TaskDispatchJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatchJob.class);

    @Resource
    private InterfaceTaskDao taskDao;

    @Resource
    private PushService pushService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long taskId = context.getMergedJobDataMap().getLong("taskId");
        String taskCode = context.getMergedJobDataMap().getString("taskCode");
        try {
            InterfaceTask task = taskDao.findById(taskId).orElse(null);
            if (task == null) {
                log.warn("任务[{}]已不存在，跳过本次调度", taskCode);
                return;
            }
            if (!Boolean.TRUE.equals(task.getEnabled())) {
                log.info("任务[{}]已停用，跳过本次调度", taskCode);
                return;
            }
            pushService.execute(taskId, "CRON");
        } catch (Exception e) {
            log.error("定时任务[{}]执行异常: {}", taskCode, e.getMessage(), e);
            // 抛出会让 Quartz 按 misfire 策略处理；这里选择吞掉，避免影响其他任务
        }
    }
}
