package com.interchange.platform.standard.core.base;

import com.interchange.platform.standard.exception.BizException;
import jakarta.annotation.Resource;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hibernate 通用数据访问层（写法参考 dyg-erp 的 DygBaseDao）。
 *
 * <p>对外只暴露 {@link Session} 与几个便捷方法：查询直接写 HQL / 原生 SQL，
 * 不用 Criteria API，也不生成 Spring Data 的代理接口。
 *
 * <p>约定：写操作必须在 {@code @Transactional} 方法内调用，否则不会落库。
 */
@Repository
public class BaseDao {

    @Resource
    protected SessionFactory sessionFactory;

    /** 当前会话：事务内是事务绑定的那个 Session，事务外新开一个 */
    public Session session() {
        return sessionFactory.getCurrentSession();
    }

    /* ===================== 单条 ===================== */

    public <T> T get(Class<T> type, Object id) {
        return session().get(type, id);
    }

    public <T> Optional<T> find(Class<T> type, Object id) {
        return Optional.ofNullable(get(type, id));
    }

    /** 取不到直接抛 404，省掉每个调用点写 orElseThrow */
    public <T> T load(Class<T> type, Object id, String notFoundMessage) {
        T entity = get(type, id);
        if (entity == null) {
            throw new BizException(404, notFoundMessage);
        }
        return entity;
    }

    /* ===================== 写入 ===================== */

    /** 新增或更新（游离对象走 merge） */
    public <T> T save(T entity) {
        return session().merge(entity);
    }

    public void remove(Object entity) {
        session().remove(session().contains(entity) ? entity : session().merge(entity));
    }

    /* ===================== HQL ===================== */

    /** HQL 列表；无参重载避免 varargs 歧义 */
    public <T> List<T> list(String hql) {
        return this.<T>query(hql).list();
    }

    public <T> List<T> list(String hql, Object... args) {
        return bind(this.<T>query(hql), args).list();
    }

    /** HQL 分页，{@code from} 从 0 开始 */
    public <T> List<T> page(String hql, int from, int size, Object... args) {
        Query<T> query = bind(this.<T>query(hql), args);
        query.setFirstResult(Math.max(from, 0));
        query.setMaxResults(size);
        return query.list();
    }

    public <T> T one(String hql, Object... args) {
        List<T> rows = list(hql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long count(String hql, Object... args) {
        Object v = one(hql, args);
        return v == null ? 0L : ((Number) v).longValue();
    }

    /* ===================== HQL（命名参数，推荐） ===================== */

    /**
     * 命名参数版 HQL 列表。写法参照 dyg-erp：
     * {@code listBy("from BtBankAcc where mdId in (:ids)", Map.of("ids", ids))}。
     *
     * <p>比位置参数好在：参数多时不会错位，条件增删只动一处。
     * 方法名与 {@code list(hql, Object...)} 区分开，避免 varargs 与 Map 重载歧义。
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> listBy(String hql, Map<String, ?> params) {
        return (List<T>) bindNamed(session().createQuery(hql), params).list();
    }

    public <T> T oneBy(String hql, Map<String, ?> params) {
        List<T> rows = listBy(hql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long countBy(String hql, Map<String, ?> params) {
        Object v = oneBy(hql, params);
        return v == null ? 0L : ((Number) v).longValue();
    }

    /** 命名参数的 HQL 增删改，返回影响行数（需在事务内） */
    public int updateByHql(String hql, Map<String, ?> params) {
        return bindNamed(session().createMutationQuery(hql), params).executeUpdate();
    }

    /** 位置参数的 HQL 增删改，返回影响行数（需在事务内） */
    public int updateByHql(String hql, Object... args) {
        org.hibernate.query.MutationQuery query = session().createMutationQuery(hql);
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                query.setParameter(i, args[i]);
            }
        }
        return query.executeUpdate();
    }

    /* ===================== 原生 SQL ===================== */

    /** 原生查询映射成实体 */
    public <T> List<T> nativeList(String sql, Class<T> type, Object... args) {
        return bind(session().createNativeQuery(sql, type), args).list();
    }

    /**
     * 原生查询返回 Map，列名统一小写。
     * Oracle 返回大写、MySQL 保持原样，不统一换库就取不到值。
     */
    public List<Map<String, Object>> nativeRows(String sql, Object... args) {
        List<Object> raw = bind(session().createNativeQuery(sql), args).list();
        return raw.stream().map(BaseDao::toRow).toList();
    }

    /** 原生增删改，返回影响行数（需在事务内） */
    public int nativeUpdate(String sql, Object... args) {
        return bind(session().createNativeQuery(sql), args).executeUpdate();
    }

    /** 原生单值，无结果返回 null */
    public String nativeString(String sql, Object... args) {
        List<Object> rows = bind(session().createNativeQuery(sql).setMaxResults(1), args).list();
        return rows.isEmpty() || rows.get(0) == null ? null : String.valueOf(rows.get(0));
    }

    /* ===================== 工具 ===================== */

    @SuppressWarnings("unchecked")
    private <T> Query<T> query(String hql) {
        return (Query<T>) session().createQuery(hql);
    }

    /**
     * 按名字绑定参数。上界用 {@code QueryProducer} 的公共接口
     * {@link org.hibernate.query.CommonQuery}，这样 HQL 的 {@code Query}、
     * 原生 {@code NativeQuery}、以及增删改的 {@code MutationQuery} 都能传。
     */
    protected static <Q extends org.hibernate.query.CommonQueryContract> Q bindNamed(
            Q query, Map<String, ?> params) {
        if (params != null) {
            params.forEach(query::setParameter);
        }
        return query;
    }

    /**
     * 按位置绑定参数（Hibernate 6 的下标从 0 开始）。
     * {@link org.hibernate.query.NativeQuery} 与 {@code MutationQuery} 都可传。
     */
    protected static <Q extends org.hibernate.query.CommonQueryContract> Q bind(Q query, Object... args) {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                query.setParameter(i, args[i]);
            }
        }
        return query;
    }

    /** Hibernate 可能返回 Map 或 Object[]，统一成列名小写的 Map；子类写原生 SQL 时可直接用 */
    protected static Map<String, Object> toRow(Object row) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        if (row instanceof Map<?, ?> m) {
            m.forEach((k, v) -> map.put(k == null ? null : String.valueOf(k).toLowerCase(), v));
        } else if (row instanceof Object[] arr) {
            for (int i = 0; i < arr.length; i++) {
                map.put("c" + i, arr[i]);
            }
        }
        return map;
    }

    /** IN 子句占位符 */
    public static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    /** 过滤空值并去重（批量 IN 用） */
    public static List<String> clean(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(v -> v != null && !v.isBlank()).distinct().toList();
    }
}
