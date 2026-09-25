package com.interchange.platform.service;

import com.interchange.platform.config.AppProps;
import com.interchange.platform.entity.ReceiveLog;
import com.interchange.platform.entity.TaskLog;
import com.interchange.platform.repository.ReceiveLogRepository;
import com.interchange.platform.repository.TaskLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日志表（task_log / receive_log）的统一落库入口。
 *
 * <p>日志表存的是完整报文，高频任务一天就能落几千条，库会很快膨胀。
 * 默认策略是「每个任务 / 每个接口只保留最后一次执行结果」：新结果覆盖旧的那一行，
 * 行数恒等于任务数 / 接口数。成功失败都覆盖 —— 表里始终是"最后一次跑成什么样"。
 *
 * <p>要保留完整历史明细时把 {@code app.log.keep-latest-only} 设成 false，
 * 行为就退回成"每次执行新增一条"。
 *
 * <p>历史明细不会因为只留一条就丢：接口日志文件
 * {@code logs/interfaces/{接口}/{接口}.log} 里按次记录了全过程（跨天 gzip 保留 30 天），
 * 只是不能像数据库那样按字段查询和导出。
 */
@Service
public class LogStore {

    private static final Logger log = LoggerFactory.getLogger(LogStore.class);

    private final AppProps appProps;
    private final TaskLogRepository taskLogRepository;
    private final ReceiveLogRepository receiveLogRepository;

    public LogStore(AppProps appProps,
                    TaskLogRepository taskLogRepository,
                    ReceiveLogRepository receiveLogRepository) {
        this.appProps = appProps;
        this.taskLogRepository = taskLogRepository;
        this.receiveLogRepository = receiveLogRepository;
    }

    public boolean keepLatestOnly() {
        return appProps.getLog().isKeepLatestOnly();
    }

    /**
     * 保存一条推送执行日志。
     * 覆盖模式下复用同一 task_code 的那一行，保存后把该维度的其它行删掉（并发时可能有残留）。
     */
    @Transactional
    public TaskLog saveTaskLog(TaskLog fresh) {
        if (!keepLatestOnly()) {
            return taskLogRepository.save(fresh);
        }
        TaskLog target = fresh;
        String code = fresh.getTaskCode();
        if (code != null && !code.isBlank()) {
            TaskLog exist = taskLogRepository.findFirstByTaskCodeOrderByIdDesc(code);
            if (exist != null) {
                // 整行覆盖：新一次执行就是最新状态，不留上次的报文和计数。
                // create_time 保留首次写入时间（同一行记录），本次执行时间看 start_time。
                LocalDateTime createdAt = exist.getCreateTime();
                BeanUtils.copyProperties(fresh, exist, "id", "createTime");
                if (exist.getCreateTime() == null) {
                    exist.setCreateTime(createdAt == null ? LocalDateTime.now() : createdAt);
                }
                target = exist;
            }
        }
        TaskLog saved = taskLogRepository.save(target);
        trimTaskLog(code, saved.getId());
        return saved;
    }

    /** 同一维度的其它行删掉：并发执行可能瞬间留下两条，这里保证收敛回一条 */
    private void trimTaskLog(String code, Long keepId) {
        if (code == null || code.isBlank() || keepId == null) {
            return;
        }
        try {
            int removed = taskLogRepository.deleteByTaskCodeAndIdNot(code, keepId);
            if (removed > 0) {
                log.info("日志表收敛：任务 {} 清理旧记录 {} 条", code, removed);
            }
        } catch (Exception e) {
            log.warn("日志表收敛失败 taskCode={}：{}", code, e.getMessage());
        }
    }

    /** 保存一条接收日志，规则同上（按 api_code 维度覆盖） */
    @Transactional
    public ReceiveLog saveReceiveLog(ReceiveLog fresh) {
        if (!keepLatestOnly()) {
            return receiveLogRepository.save(fresh);
        }
        ReceiveLog target = fresh;
        String code = fresh.getApiCode();
        if (code != null && !code.isBlank()) {
            ReceiveLog exist = receiveLogRepository.findFirstByApiCodeOrderByIdDesc(code);
            if (exist != null) {
                BeanUtils.copyProperties(fresh, exist, "id");
                target = exist;
            }
        }
        ReceiveLog saved = receiveLogRepository.save(target);
        if (code != null && !code.isBlank() && saved.getId() != null) {
            try {
                int removed = receiveLogRepository.deleteByApiCodeAndIdNot(code, saved.getId());
                if (removed > 0) {
                    log.info("日志表收敛：接口 {} 清理旧记录 {} 条", code, removed);
                }
            } catch (Exception e) {
                log.warn("日志表收敛失败 apiCode={}：{}", code, e.getMessage());
            }
        }
        return saved;
    }

    /**
     * 收敛清理：每个任务 / 每个接口只留最新一条，删掉其余历史行。
     * 启动时跑一次，用于处理「从全量模式切过来」「历史已攒了很多」的情况。
     *
     * @return 删除的行数（推送 + 接收）
     */
    @Transactional
    public int pruneToLatest() {
        if (!keepLatestOnly()) {
            return 0;
        }
        int removed = 0;
        try {
            List<Long> keepTask = taskLogRepository.latestIds();
            if (!keepTask.isEmpty()) {
                removed += taskLogRepository.deleteOthers(keepTask);
            }
            List<Long> keepReceive = receiveLogRepository.latestIds();
            if (!keepReceive.isEmpty()) {
                removed += receiveLogRepository.deleteOthers(keepReceive);
            }
            if (removed > 0) {
                log.info("日志表已按「只留最新一条」收敛，清理历史记录 {} 条", removed);
            }
        } catch (Exception e) {
            log.warn("日志表收敛清理失败（不影响启动）：{}", e.getMessage());
        }
        return removed;
    }
}
