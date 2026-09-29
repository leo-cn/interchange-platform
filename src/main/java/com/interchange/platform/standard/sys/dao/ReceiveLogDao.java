package com.interchange.platform.standard.sys.dao;

import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.sys.entity.ReceiveLog;
import com.interchange.platform.standard.utils.StringUtil;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 接口接收日志的存取。 */
@Repository
public class ReceiveLogDao extends BaseDao {

    public Optional<ReceiveLog> findById(Long id) {
        return find(ReceiveLog.class, id);
    }

    public ReceiveLog get(Long id, String notFoundMessage) {
        return load(ReceiveLog.class, id, notFoundMessage);
    }

    /* ---------- 看板统计 ---------- */

    public long countSince(LocalDateTime from) {
        return count("select count(l) from ReceiveLog l where l.receiveTime >= ?1", from);
    }

    public long countSinceAndStatus(LocalDateTime from, String status) {
        return count("select count(l) from ReceiveLog l where l.receiveTime >= ?1 and l.status = ?2",
                from, status);
    }

    public List<ReceiveLog> findTop20() {
        return page("from ReceiveLog order by id desc", 0, 20);
    }

    /** 某个接口最新的一条（覆盖式保存时取"要被覆盖的那一行"） */
    public ReceiveLog findLatestByApiCode(String apiCode) {
        return one("from ReceiveLog where apiCode = ?1 order by id desc", apiCode);
    }

    /** 每个接口的最后一次调用（只取统计用字段，不加载报文大字段） */
    @SuppressWarnings("unchecked")
    public List<Object[]> latestPerApi() {
        return (List<Object[]>) (List<?>) list("select l.apiCode, l.status, l.receiveTime "
                + "from ReceiveLog l where l.id in "
                + "(select max(x.id) from ReceiveLog x group by x.apiCode)");
    }

    /* ---------- 只保留最新一条 ---------- */

    public long countByApiCode(String apiCode) {
        return count("select count(l) from ReceiveLog l where l.apiCode = ?1", apiCode);
    }

    public int deleteByApiCodeAndIdNot(String apiCode, Long id) {
        return nativeUpdate("delete from receive_log where api_code = ?1 and id <> ?2", apiCode, id);
    }

    @SuppressWarnings("unchecked")
    public List<Long> latestIds() {
        List<Object> raw = list("select max(x.id) from ReceiveLog x group by x.apiCode");
        return raw.stream().map(o -> o == null ? null : ((Number) o).longValue()).toList();
    }

    /** 历史上出现过的所有接口编码，去重（启动时把没登记过的接口补进清单） */
    @SuppressWarnings("unchecked")
    public List<String> findDistinctApiCodes() {
        List<Object> raw = list("select distinct l.apiCode from ReceiveLog l where l.apiCode is not null");
        return raw.stream().map(String::valueOf).toList();
    }

    public int deleteOthers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return nativeUpdate(
                "delete from receive_log where id not in (" + BaseDao.placeholders(ids.size()) + ")",
                ids.toArray());
    }

    public ReceiveLog save(ReceiveLog entity) {
        return super.save(entity);
    }

    /* ---------- 条件查询（页面 / 导出共用） ---------- */

    public List<ReceiveLog> search(String apiCode, String status, String keyword,
                                   LocalDateTime start, LocalDateTime end, int from, int size) {
        List<Object> args = new ArrayList<>();
        String where = where(apiCode, status, keyword, start, end, args);
        return page("from ReceiveLog " + where + " order by id desc", from, size, args.toArray());
    }

    public long searchCount(String apiCode, String status, String keyword,
                            LocalDateTime start, LocalDateTime end) {
        List<Object> args = new ArrayList<>();
        String where = where(apiCode, status, keyword, start, end, args);
        return count("select count(l) from ReceiveLog l " + where, args.toArray());
    }

    private static String where(String apiCode, String status, String keyword,
                                LocalDateTime start, LocalDateTime end, List<Object> args) {
        StringBuilder sb = new StringBuilder("where 1=1");
        if (StringUtil.isNotBlank(apiCode)) {
            sb.append(" and apiCode = ?").append(args.size() + 1);
            args.add(apiCode.trim());
        }
        if (StringUtil.isNotBlank(status)) {
            sb.append(" and status = ?").append(args.size() + 1);
            args.add(status.trim());
        }
        if (start != null) {
            sb.append(" and receiveTime >= ?").append(args.size() + 1);
            args.add(start);
        }
        if (end != null) {
            sb.append(" and receiveTime <= ?").append(args.size() + 1);
            args.add(end);
        }
        if (StringUtil.isNotBlank(keyword)) {
            String like = "%" + keyword.trim() + "%";
            sb.append(" and (traceId like ?").append(args.size() + 1)
                    .append(" or caller like ?").append(args.size() + 2)
                    .append(" or errorMsg like ?").append(args.size() + 3)
                    .append(" or requestBody like ?").append(args.size() + 4)
                    .append(")");
            for (int i = 0; i < 4; i++) {
                args.add(like);
            }
        }
        return sb.toString();
    }

}
