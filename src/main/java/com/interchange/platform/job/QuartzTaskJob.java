package com.interchange.platform.job;

import com.interchange.platform.entity.InterfaceTask;
import com.interchange.platform.repository.InterfaceTaskRepository;
import com.interchange.platform.service.PushService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz 任务执行入口。
 *
 * <p>只做三件事：取任务 → 校验启用状态 → 交给推送引擎。
 * 加 {@link DisallowConcurrentExecution} 防止同一任务上一轮没跑完又触发下一轮。
 */
@DisallowConcurrentExecution
public class QuartzTaskJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(QuartzTaskJob.class);

    @Autowired
    private InterfaceTaskRepository taskRepository;

    @Autowired
    private PushService pushService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long taskId = context.getMergedJobDataMap().getLong("taskId");
        String taskCode = context.getMergedJobDataMap().getString("taskCode");
        try {
            InterfaceTask task = taskRepository.findById(taskId).orElse(null);
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
