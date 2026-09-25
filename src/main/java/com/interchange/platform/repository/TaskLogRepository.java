package com.interchange.platform.repository;

import com.interchange.platform.entity.TaskLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskLogRepository extends JpaRepository<TaskLog, Long>,
        JpaSpecificationExecutor<TaskLog> {

    long countByStatusAndStartTimeAfter(String status, LocalDateTime after);

    /* ---------- 首页看板统计 ---------- */

    @Query("select count(l) from TaskLog l where l.startTime >= :from")
    long countSince(@Param("from") LocalDateTime from);

    @Query("select count(l) from TaskLog l where l.startTime >= :from and l.status = :status")
    long countSinceAndStatus(@Param("from") LocalDateTime from, @Param("status") String status);

    @Query("select max(l.costMs) from TaskLog l where l.startTime >= :from and l.status = 'SUCCESS'")
    Long maxCostSince(@Param("from") LocalDateTime from);

    /** 近 N 次执行记录（首页走势/最近执行列表） */
    List<TaskLog> findTop20ByOrderByIdDesc();

    /** 按任务编码倒序取最近若干条，用于任务详情页 */
    List<TaskLog> findTop10ByTaskCodeOrderByIdDesc(String taskCode);

    /** 某个任务最新的一条（覆盖式保存时用它取"要被覆盖的那一行"） */
    TaskLog findFirstByTaskCodeOrderByIdDesc(String taskCode);

    /**
     * 每个任务的最后一次执行（只取统计用字段，不加载报文大字段）。
     * 覆盖式保留模式下，这张表每个任务本来就只有一条，这里只是把口径写死，
     * 切回全量保留模式时也能得到"每个任务最后一次"的正确结果。
     */
    @Query("select l.taskCode, l.status, l.startTime, l.costMs from TaskLog l "
            + "where l.id in (select max(x.id) from TaskLog x group by x.taskCode)")
    List<Object[]> latestPerTask();

    void deleteByTaskCode(String taskCode);

    /* ---------- 只保留最新一条 ---------- */

    long countByTaskCode(String taskCode);

    int deleteByTaskCodeAndIdNot(String taskCode, Long id);

    /** 每个任务最新那一条的主键 */
    @Query("select max(x.id) from TaskLog x group by x.taskCode")
    List<Long> latestIds();

    /** 删掉不在保留名单里的历史行 */
    @Modifying
    @Query("delete from TaskLog l where l.id not in :ids")
    int deleteOthers(@Param("ids") List<Long> ids);
}
