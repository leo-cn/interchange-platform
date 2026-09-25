package com.interchange.platform.service;

import com.interchange.platform.entity.InterfaceTask;
import com.interchange.platform.entity.TaskLog;
import com.interchange.platform.repository.InterfaceTaskRepository;
import com.interchange.platform.repository.PartnerRepository;
import com.interchange.platform.repository.ReceiveLogRepository;
import com.interchange.platform.repository.TaskLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页看板数据。
 *
 * <p>日志表默认「每个任务 / 每个接口只留最后一次」时，"今日执行了多少次"是没有意义的
 * （表里根本没有历史行），所以统计口径改成**按任务 / 接口的最新一次结果**来数：
 * 多少个任务最后一次是成功、多少个是失败、今天跑过多少个。
 * 切回全量保留模式（app.log.keep-latest-only=false）后，自动退回按次数统计。
 */
@Service
public class DashboardService {

    private final InterfaceTaskRepository taskRepository;
    private final PartnerRepository partnerRepository;
    private final TaskLogRepository taskLogRepository;
    private final ReceiveLogRepository receiveLogRepository;
    private final LogStore logStore;

    public DashboardService(InterfaceTaskRepository taskRepository,
                            PartnerRepository partnerRepository,
                            TaskLogRepository taskLogRepository,
                            ReceiveLogRepository receiveLogRepository,
                            LogStore logStore) {
        this.taskRepository = taskRepository;
        this.partnerRepository = partnerRepository;
        this.taskLogRepository = taskLogRepository;
        this.receiveLogRepository = receiveLogRepository;
        this.logStore = logStore;
    }

    public Map<String, Object> stats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("taskTotal", taskRepository.count());
        map.put("taskEnabled", taskRepository.countByEnabled(true));
        map.put("partnerTotal", partnerRepository.count());
        map.put("partnerEnabled", partnerRepository.countByStatus(1));
        map.put("keepLatestOnly", logStore.keepLatestOnly());

        if (logStore.keepLatestOnly()) {
            // 口径：每个任务的最后一次执行
            long total = 0, success = 0, fail = 0, partial = 0, today = 0, maxCost = 0;
            for (Object[] row : taskLogRepository.latestPerTask()) {
                String status = (String) row[1];
                LocalDateTime at = row[2] == null ? null : (LocalDateTime) row[2];
                long cost = row[3] == null ? 0 : ((Number) row[3]).longValue();
                total++;
                if ("SUCCESS".equals(status)) {
                    success++;
                } else if ("FAIL".equals(status)) {
                    fail++;
                } else if ("PARTIAL".equals(status)) {
                    partial++;
                }
                if (at != null && !at.isBefore(todayStart)) {
                    today++;
                }
                if (cost > maxCost) {
                    maxCost = cost;
                }
            }
            map.put("pushTotal", total);
            map.put("pushSuccess", success);
            map.put("pushFail", fail);
            map.put("pushPartial", partial);
            map.put("pushToday", today);
            map.put("pushMaxCost", maxCost);
            map.put("pushSuccessRate", total == 0 ? "-" :
                    Math.round(success * 1000.0 / total) / 10.0 + "%");

            long recvTotal = 0, recvFail = 0, recvToday = 0;
            for (Object[] row : receiveLogRepository.latestPerApi()) {
                String status = (String) row[1];
                LocalDateTime at = row[2] == null ? null : (LocalDateTime) row[2];
                recvTotal++;
                if ("FAIL".equals(status)) {
                    recvFail++;
                }
                if (at != null && !at.isBefore(todayStart)) {
                    recvToday++;
                }
            }
            map.put("receiveTotal", recvTotal);
            map.put("receiveFail", recvFail);
            map.put("receiveToday", recvToday);
        } else {
            // 口径：今日执行次数（保留全部历史时才有意义）
            long todayTotal = taskLogRepository.countSince(todayStart);
            long todaySuccess = taskLogRepository.countSinceAndStatus(todayStart, "SUCCESS");
            long todayFail = taskLogRepository.countSinceAndStatus(todayStart, "FAIL");
            long todayPartial = taskLogRepository.countSinceAndStatus(todayStart, "PARTIAL");
            Long maxCost = taskLogRepository.maxCostSince(todayStart);
            map.put("pushTotal", todayTotal);
            map.put("pushSuccess", todaySuccess);
            map.put("pushFail", todayFail);
            map.put("pushPartial", todayPartial);
            map.put("pushToday", todayTotal);
            map.put("pushMaxCost", maxCost == null ? 0 : maxCost);
            map.put("pushSuccessRate", todayTotal == 0 ? "-" :
                    Math.round(todaySuccess * 1000.0 / todayTotal) / 10.0 + "%");
            map.put("receiveTotal", receiveLogRepository.countSince(todayStart));
            map.put("receiveFail", receiveLogRepository.countSinceAndStatus(todayStart, "FAIL"));
            map.put("receiveToday", receiveLogRepository.countSince(todayStart));
        }

        return map;
    }

    /** 最近执行记录（只留最新一条模式下，就是各任务最后一次执行） */
    public List<TaskLog> recentLogs() {
        return taskLogRepository.findTop20ByOrderByIdDesc();
    }

    /** 启用中的任务列表（首页展示调度概览） */
    public List<InterfaceTask> enabledTasks() {
        return taskRepository.findByEnabledOrderByIdAsc(true);
    }
}
