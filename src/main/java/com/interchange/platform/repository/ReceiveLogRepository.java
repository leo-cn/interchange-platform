package com.interchange.platform.repository;

import com.interchange.platform.entity.ReceiveLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReceiveLogRepository extends JpaRepository<ReceiveLog, Long>,
        JpaSpecificationExecutor<ReceiveLog> {

    @Query("select count(l) from ReceiveLog l where l.receiveTime >= :from")
    long countSince(@Param("from") LocalDateTime from);

    @Query("select count(l) from ReceiveLog l where l.receiveTime >= :from and l.status = :status")
    long countSinceAndStatus(@Param("from") LocalDateTime from, @Param("status") String status);

    List<ReceiveLog> findTop20ByOrderByIdDesc();

    /** 某个接口最新的一条（覆盖式保存时用它取"要被覆盖的那一行"） */
    ReceiveLog findFirstByApiCodeOrderByIdDesc(String apiCode);

    /** 每个接口的最后一次调用（只取统计用字段，不加载报文大字段） */
    @Query("select l.apiCode, l.status, l.receiveTime from ReceiveLog l "
            + "where l.id in (select max(x.id) from ReceiveLog x group by x.apiCode)")
    List<Object[]> latestPerApi();

    /* ---------- 只保留最新一条 ---------- */

    long countByApiCode(String apiCode);

    int deleteByApiCodeAndIdNot(String apiCode, Long id);

    /** 每个接口最新那一条的主键 */
    @Query("select max(x.id) from ReceiveLog x group by x.apiCode")
    List<Long> latestIds();

    /** 历史上出现过的所有接口编码，去重（用于启动时把没登记过的接口自动补进清单） */
    @Query("select distinct l.apiCode from ReceiveLog l where l.apiCode is not null")
    List<String> findDistinctApiCodes();

    /** 删掉不在保留名单里的历史行 */
    @Modifying
    @Query("delete from ReceiveLog l where l.id not in :ids")
    int deleteOthers(@Param("ids") List<Long> ids);
}
