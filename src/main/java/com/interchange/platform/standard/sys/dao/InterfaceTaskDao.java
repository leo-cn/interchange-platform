package com.interchange.platform.standard.sys.dao;

import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.sys.entity.InterfaceTask;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 定时推送任务定义的存取。 */
@Repository
public class InterfaceTaskDao extends BaseDao {

    public Optional<InterfaceTask> findById(Long id) {
        return find(InterfaceTask.class, id);
    }

    public InterfaceTask get(Long id, String notFoundMessage) {
        return load(InterfaceTask.class, id, notFoundMessage);
    }

    public Optional<InterfaceTask> findByTaskCode(String taskCode) {
        return Optional.ofNullable(one("from InterfaceTask where taskCode = ?1", taskCode));
    }

    public boolean existsByTaskCode(String taskCode) {
        return count("select count(t) from InterfaceTask t where t.taskCode = ?1", taskCode) > 0;
    }

    public List<InterfaceTask> findByEnabled(boolean enabled) {
        return list("from InterfaceTask where enabled = ?1 order by id asc", enabled);
    }

    public List<InterfaceTask> findAllDesc() {
        return list("from InterfaceTask order by id desc");
    }

    public List<InterfaceTask> findByPartnerId(Long partnerId) {
        return list("from InterfaceTask where partnerId = ?1", partnerId);
    }

    public long countAll() {
        return count("select count(t) from InterfaceTask t");
    }

    public long countByEnabled(boolean enabled) {
        return count("select count(t) from InterfaceTask t where t.enabled = ?1", enabled);
    }

    /** 条件分页：关键字匹配编码/名称，其余为等值过滤 */
    public List<InterfaceTask> search(String keyword, Long partnerId, Boolean enabled, int from, int size) {
        List<Object> args = new ArrayList<>();
        String where = where(keyword, partnerId, enabled, args);
        return page("from InterfaceTask " + where + " order by id desc", from, size, args.toArray());
    }

    public long searchCount(String keyword, Long partnerId, Boolean enabled) {
        List<Object> args = new ArrayList<>();
        String where = where(keyword, partnerId, enabled, args);
        return count("select count(t) from InterfaceTask t " + where, args.toArray());
    }

    /** 拼 where 子句，参数按出现顺序收集进 args */
    private static String where(String keyword, Long partnerId, Boolean enabled, List<Object> args) {
        StringBuilder sb = new StringBuilder("where 1=1");
        if (keyword != null && !keyword.isBlank()) {
            sb.append(" and (")
                    .append("taskCode like ?").append(args.size() + 1)
                    .append(" or taskName like ?").append(args.size() + 2)
                    .append(")");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (partnerId != null) {
            sb.append(" and partnerId = ?").append(args.size() + 1);
            args.add(partnerId);
        }
        if (enabled != null) {
            sb.append(" and enabled = ?").append(args.size() + 1);
            args.add(enabled);
        }
        return sb.toString();
    }

    public InterfaceTask save(InterfaceTask entity) {
        return super.save(entity);
    }

    public void delete(InterfaceTask entity) {
        remove(entity);
    }
}
