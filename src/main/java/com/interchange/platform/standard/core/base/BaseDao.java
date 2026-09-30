package com.interchange.platform.standard.core.base;

import com.interchange.platform.standard.exception.BizException;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hibernate 通用数据访问层
 */
@Repository
@Transactional
public class BaseDao {

    @Resource
    private EntityManagerFactory entityManagerFactory;

    /**
     * 当前事务的 Session。提交/回滚、关闭都由 Spring 管，调用方不要 flush / close。
     */
    public Session session() {
        EntityManagerHolder holder = (EntityManagerHolder)
                TransactionSynchronizationManager.getResource(entityManagerFactory);
        Session session = holder == null ? null : holder.getEntityManager().unwrap(Session.class);
        if (session == null) {
            throw new BizException("当前线程没有绑定事务，DAO 只能在 @Transactional 方法内调用");
        }
        return session;
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

    /**
     * 位置参数的 HQL 增删改，返回影响行数（需在事务内）。
     *
     * <p>占位符写 {@code ?1}/{@code ?2}，下标从 <b>1</b> 起（JPA 约定，见 {@link #bind}）。
     */
    public int updateByHql(String hql, Object... args) {
        org.hibernate.query.MutationQuery query = session().createMutationQuery(hql);
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                query.setParameter(i + 1, args[i]);
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

    /** 原生查询（命名参数）返回 Map，列名统一小写 */
    public List<Map<String, Object>> nativeRows(String sql, Map<String, ?> params) {
        List<Object> raw = bindNamed(session().createNativeQuery(sql), params).list();
        return raw.stream().map(BaseDao::toRow).toList();
    }

    /**
     * 原生 SQL 查询并转成 VO：SQL 里给每列起别名，别名与 VO 属性名一致
     * （大小写不敏感：Oracle 返回大写、MySQL 原样，都能对上）。
     *
     * <p>写法参照 dyg-erp 的 {@code DygBaseDao.listBySQLAliasToBean}：
     * 先把结果取成「别名 → 值」的 Map，再按属性名拷进 Bean。
     */
    public <B> List<B> listBySQLAliasToBean(String sql, Map<String, ?> params, Class<B> clazz) {
        List<Map<String, Object>> rows = nativeRows(sql, params);
        List<B> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            list.add(toBean(row, clazz));
        }
        return list;
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
     */
    protected static <Q extends org.hibernate.query.CommonQueryContract> Q bindNamed(
            Q query, Map<String, ?> params) {
        if (params != null) {
            params.forEach(query::setParameter);
        }
        return query;
    }

    /**
     * 按位置绑定参数：第 1 个参数对应 {@code ?1}，下标从 <b>1</b> 起。
     *
     * <p>Hibernate 6 里 HQL 与原生 SQL 都是这个约定（原生 SQL 的裸 {@code ?} 也按出现顺序
     * 编号 1..n，这是迁移指南里 "JDBC-style parameter declarations in native queries,
     * we have also moved to using one-based instead of zero-based" 那条）。
     * 所以 {@link #placeholders(int)} 生成的 {@code ?,?,?} 也直接配 1 起下标。
     *
     * <p>{@link org.hibernate.query.NativeQuery}、{@code MutationQuery}、HQL {@code Query} 都可传。
     */
    protected static <Q extends org.hibernate.query.CommonQueryContract> Q bind(Q query, Object... args) {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                query.setParameter(i + 1, args[i]);
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

    /** IN 子句占位符 {@code ?,?,?}，配 {@link #bind} 的 1 起下标使用 */
    public static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    /**
     * Map 行（列名已转小写）→ VO：按属性名把值 set 进去。
     * SQL 里有、VO 里没有的列直接忽略；单个字段类型对不上也跳过，不阻断整条查询。
     */
    private static <B> B toBean(Map<String, Object> row, Class<B> clazz) {
        B bean;
        try {
            bean = clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new BizException(500, "结果类没有无参构造，无法转换: " + clazz.getName());
        }
        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            Object value = row.get(f.getName().toLowerCase());
            if (value == null) {
                continue;
            }
            try {
                f.setAccessible(true);
                f.set(bean, convertValue(value, f.getType()));
            } catch (Exception ignored) {
                // 单个字段转不了就留空
            }
        }
        return bean;
    }

    /** 宽松取值转换：字符串目标统一 String.valueOf，数字目标按 Number 转 */
    private static Object convertValue(Object value, Class<?> type) {
        if (type.isInstance(value)) {
            return value;
        }
        if (type == String.class) {
            return String.valueOf(value);
        }
        if (value instanceof Number n) {
            if (type == int.class || type == Integer.class) {
                return n.intValue();
            }
            if (type == long.class || type == Long.class) {
                return n.longValue();
            }
        }
        return value;
    }
}
