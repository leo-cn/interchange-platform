package com.interchange.platform.standard.sys.dao;

import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.sys.entity.TaskLog;
import com.interchange.platform.standard.utils.StringUtil;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 推送执行日志的存取。 */
@Repository
public class TaskLogDao extends BaseDao {

    public Optional<TaskLog> findById(Long id) {
        return find(TaskLog.class, id);
    }

    public TaskLog get(Long id, String notFoundMessage) {
        return load(TaskLog.class, id, notFoundMessage);
    }

    /* ---------- 首页看板统计 ---------- */

    public long countSince(LocalDateTime from) {
        return count("select count(l) from TaskLog l where l.startTime >= ?1", from);
    }

    public long countSinceAndStatus(LocalDateTime from, String status) {
        return count("select count(l) from TaskLog l where l.startTime >= ?1 and l.status = ?2",
                from, status);
    }

    public long countByStatusAndStartTimeAfter(String status, LocalDateTime after) {
        return countSinceAndStatus(after, status);
    }

    /** 统计窗口内最慢的一次耗时 */
    public Long maxCostSince(LocalDateTime from) {
        Object v = one("select max(l.costMs) from TaskLog l "
                + "where l.startTime >= ?1 and l.status = 'SUCCESS'", from);
        return v == null ? null : ((Number) v).longValue();
    }

    /** 近 20 次执行记录 */
    public List<TaskLog> findTop20() {
        return page("from TaskLog order by id desc", 0, 20);
    }

    /** 某任务倒序取最近若干条，用于任务详情页 */
    public List<TaskLog> findTop10ByTaskCode(String taskCode) {
        return page("from TaskLog where taskCode = ?1 order by id desc", 0, 10, taskCode);
    }

    /** 某个任务最新的一条（覆盖式保存时取"要被覆盖的那一行"） */
    public TaskLog findLatestByTaskCode(String taskCode) {
        return one("from TaskLog where taskCode = ?1 order by id desc", taskCode);
    }

    /**
     * 每个任务的最后一次执行（只取统计用字段，不加载报文大字段）。
     * 覆盖式保留模式下每个任务本来就只有一条，这里只是把口径写死。
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> latestPerTask() {
        return (List<Object[]>) (List<?>) list("select l.taskCode, l.status, l.startTime, l.costMs "
                + "from TaskLog l where l.id in "
                + "(select max(x.id) from TaskLog x group by x.taskCode)");
    }

    /* ---------- 只保留最新一条 ---------- */

    public long countByTaskCode(String taskCode) {
        return count("select count(l) from TaskLog l where l.taskCode = ?1", taskCode);
    }

    public int deleteByTaskCode(String taskCode) {
        return nativeUpdate("delete from task_log where task_code = ?1", taskCode);
    }

    public int deleteByTaskCodeAndIdNot(String taskCode, Long id) {
        return nativeUpdate("delete from task_log where task_code = ?1 and id <> ?2", taskCode, id);
    }

    @SuppressWarnings("unchecked")
    public List<Long> latestIds() {
        List<Object> raw = list("select max(x.id) from TaskLog x group by x.taskCode");
        return raw.stream().map(o -> o == null ? null : ((Number) o).longValue()).toList();
    }

    /** 删掉不在保留名单里的历史行 */
    public int deleteOthers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return nativeUpdate("delete from task_log where id not in (" + BaseDao.placeholders(ids.size()) + ")",
                ids.toArray());
    }

    public TaskLog save(TaskLog entity) {
        return super.save(entity);
    }

    /* ---------- 条件查询（页面 / 导出共用） ---------- */

    public List<TaskLog> search(String taskCode, String taskName, String status, String triggerType,
                                String keyword, LocalDateTime start, LocalDateTime end, int from, int size) {
        List<Object> args = new ArrayList<>();
        String where = where(taskCode, taskName, status, triggerType, keyword, start, end, args);
        return page("from TaskLog " + where + " order by id desc", from, size, args.toArray());
    }

    public long searchCount(String taskCode, String taskName, String status, String triggerType,
                            String keyword, LocalDateTime start, LocalDateTime end) {
        List<Object> args = new ArrayList<>();
        String where = where(taskCode, taskName, status, triggerType, keyword, start, end, args);
        return count("select count(l) from TaskLog l " + where, args.toArray());
    }

    private static String where(String taskCode, String taskName, String status, String triggerType,
                                String keyword, LocalDateTime start, LocalDateTime end, List<Object> args) {
        StringBuilder sb = new StringBuilder("where 1=1");
        if (StringUtil.isNotBlank(taskCode)) {
            sb.append(" and taskCode = ?").append(args.size() + 1);
            args.add(taskCode.trim());
        }
        if (StringUtil.isNotBlank(taskName)) {
            sb.append(" and taskName like ?").append(args.size() + 1);
            args.add("%" + taskName.trim() + "%");
        }
        if (StringUtil.isNotBlank(status)) {
            sb.append(" and status = ?").append(args.size() + 1);
            args.add(status.trim());
        }
        if (StringUtil.isNotBlank(triggerType)) {
            sb.append(" and triggerType = ?").append(args.size() + 1);
            args.add(triggerType.trim());
        }
        if (start != null) {
            sb.append(" and startTime >= ?").append(args.size() + 1);
            args.add(start);
        }
        if (end != null) {
            sb.append(" and startTime <= ?").append(args.size() + 1);
            args.add(end);
        }
        if (StringUtil.isNotBlank(keyword)) {
            String like = "%" + keyword.trim() + "%";
            sb.append(" and (traceId like ?").append(args.size() + 1)
                    .append(" or errorMsg like ?").append(args.size() + 2)
                    .append(" or targetUrl like ?").append(args.size() + 3)
                    .append(" or requestBody like ?").append(args.size() + 4)
                    .append(" or responseBody like ?").append(args.size() + 5)
                    .append(")");
            for (int i = 0; i < 5; i++) {
                args.add(like);
            }
        }
        return sb.toString();
    }

}
