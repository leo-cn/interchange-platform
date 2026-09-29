package com.interchange.platform.standard.sys.dao;

import com.interchange.platform.standard.config.AppProps;
import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.utils.StringUtil;
import com.interchange.platform.standard.datasource.DataSourceRegistry;
import com.interchange.platform.standard.exception.BizException;
import com.interchange.platform.standard.sys.vo.BizUserRow;
import jakarta.annotation.Resource;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 业务系统账号表（账号主数据）的只读访问。
 *
 * <p>与平台自身的表不同，这张表<strong>表名不固定、且可能不在主库</strong>
 * （由 {@code app.user.table} / {@code app.user.datasource} 配置），
 * 因此不走实体映射，而是用原生 SQL + 显式列类型映射。
 */
@Repository
public class BizUserDao extends BaseDao {

    private static final Logger log = LoggerFactory.getLogger(BizUserDao.class);

    /** 表名来自配置，拼进 SQL 前先做白名单校验，避免配置被改成奇奇怪怪的东西 */
    private static final Pattern SAFE_TABLE =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?$");

    private static final String DEFAULT_TABLE = "sys_user";
    private static final String DEFAULT_DATASOURCE = "main";

    @Resource
    private AppProps appProps;
    @Resource
    private DataSourceRegistry dataSourceRegistry;

    /** 按登录名查账号 */
    public Optional<BizUserRow> findByLoginName(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            return Optional.empty();
        }
        return one("where LOGIN_NAME = :v", loginName.trim());
    }

    /** 按业务系统主键查账号 */
    public Optional<BizUserRow> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return one("where ID = :v", id.trim());
    }

    /** 业务系统里的全部账号（按登录名排序） */
    @SuppressWarnings("unchecked")
    public List<BizUserRow> listAll() {
        return run(sql("order by LOGIN_NAME"), q -> (List<BizUserRow>) q.list());
    }

    @SuppressWarnings("unchecked")
    private Optional<BizUserRow> one(String where, String value) {
        List<BizUserRow> rows = run(sql(where), q -> (List<BizUserRow>) q.setParameter("v", value).list());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 在配置的数据源上执行查询并映射成行对象。
     *
     * <p>目标库是额外数据源时，主库的事务 Session 用不上，临时开一个只读 Session；
     * 在主库上则直接借用当前会话，能跟着调用方的事务走。
     */
    private <T> T run(String sql, Function<NativeQuery<?>, T> action) {
        String key = datasourceKey();
        boolean onMain = DataSourceRegistry.MAIN.equals(key);
        Session owned = null;
        try {
            if (onMain) {
                return action.apply(mapped(session().createNativeQuery(sql)));
            }
            if (!dataSourceRegistry.exists(key)) {
                throw new BizException(400, "app.user.datasource 指向的数据源不存在: " + key);
            }
            // 额外数据源：主库的事务 Session 用不上，借目标库连接临时开一个
            owned = session().getSessionFactory().withOptions()
                    .connection(dataSourceRegistry.template(key).getDataSource().getConnection())
                    .openSession();
            return action.apply(mapped(owned.createNativeQuery(sql)));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("读取业务系统用户失败: table={}, datasource={}", table(), key, e);
            throw new BizException(503, "账号主数据库暂不可用，请联系管理员");
        } finally {
            if (owned != null) {
                owned.close();
            }
        }
    }

    /** 显式声明列与 Java 类型，再按别名回填到行对象，避免原生查询靠猜 */
    private static NativeQuery<?> mapped(NativeQuery<?> query) {
        return query
                .addScalar("id", String.class)
                .addScalar("loginName", String.class)
                .addScalar("username", String.class)
                .addScalar("password", String.class)
                .addScalar("salt", String.class)
                .addScalar("status", Integer.class)
                .addScalar("corpId", String.class)
                .setTupleTransformer(BizUserDao::toRow);
    }

    /** 按列顺序组装行对象（与上面的 addScalar 顺序一一对应） */
    private static BizUserRow toRow(Object[] tuple, String[] aliases) {
        BizUserRow row = new BizUserRow();
        for (int i = 0; i < aliases.length && i < tuple.length; i++) {
            Object v = tuple[i];
            switch (aliases[i]) {
                case "id" -> row.setId(v == null ? null : String.valueOf(v));
                case "loginName" -> row.setLoginName(v == null ? null : String.valueOf(v));
                case "username" -> row.setUsername(v == null ? null : String.valueOf(v));
                case "password" -> row.setPassword(v == null ? null : String.valueOf(v));
                case "salt" -> row.setSalt(v == null ? "" : String.valueOf(v));
                case "status" -> row.setStatus(StringUtil.toInt(v));
                case "corpId" -> row.setCorpId(v == null ? null : String.valueOf(v));
                default -> { }
            }
        }
        return row;
    }

    /** 列定义用别名对齐行对象的属性名 */
    private String sql(String tail) {
        return "select ID as id, LOGIN_NAME as loginName, USERNAME as username,"
                + " PASSWORD as password, SALT as salt, STATUS as status, CORP_ID as corpId"
                + " from " + table() + " " + tail;
    }

    private String table() {
        String configured = appProps.getUser().getTable();
        String name = (configured == null || configured.isBlank()) ? DEFAULT_TABLE : configured.trim();
        if (!SAFE_TABLE.matcher(name).matches()) {
            throw new BizException(500, "app.user.table 配置不合法: " + name);
        }
        return name;
    }

    private String datasourceKey() {
        String key = appProps.getUser().getDatasource();
        return (key == null || key.isBlank()) ? DEFAULT_DATASOURCE : key;
    }
}
