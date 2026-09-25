package com.interchange.platform.service;

import com.interchange.platform.common.BizException;
import com.interchange.platform.config.AppProps;
import com.interchange.platform.config.DataSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 业务系统用户表读取（账号主数据）。
 *
 * <p>平台与业务系统共用一份账号：登录名、姓名、口令、启停状态都以业务库为准，
 * 平台只在自己的 sys_user_ext 上挂角色 / 令牌 / 平台口令。这样不存在"两份用户数据不一致"。
 *
 * <p>业务库不是 Hibernate 数据源（Hibernate 只管平台库），所以这里走裸 JdbcTemplate，
 * 由 {@link DataSourceRegistry} 按 {@code app.user.datasource} 懒加载。
 *
 * <p>字段全部大写是业务库的惯例（ID / LOGIN_NAME / USERNAME / PASSWORD / SALT / STATUS）。
 * 注意 MySQL 列名大小写不敏感，平台的 username（登录名）对应的是业务库的
 * <strong>LOGIN_NAME</strong> 而不是 USERNAME（USERNAME 存的是中文姓名），别写反。
 */
@Service
public class BizUserDirectory {

    private static final Logger log = LoggerFactory.getLogger(BizUserDirectory.class);

    /** 表名来自配置，拼进 SQL 前先做白名单校验，避免配置被改成奇奇怪怪的东西 */
    private static final Pattern SAFE_TABLE =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?$");

    private static final String COLUMNS = "ID, LOGIN_NAME, USERNAME, PASSWORD, SALT, STATUS, CORP_ID";

    private final DataSourceRegistry registry;
    private final AppProps appProps;

    public BizUserDirectory(DataSourceRegistry registry, AppProps appProps) {
        this.registry = registry;
        this.appProps = appProps;
    }

    /** 业务系统里的一个账号 */
    public record BizUser(String id,
                          String loginName,
                          String username,
                          String passwordHash,
                          String salt,
                          int status,
                          String corpId) {

        /** 展示名：优先中文姓名 */
        public String displayName() {
            return (username == null || username.isBlank()) ? loginName : username;
        }
    }

    /** 按登录名查账号 */
    public Optional<BizUser> findByLoginName(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            return Optional.empty();
        }
        try {
            return template().query(sql("where LOGIN_NAME = ?"), rs -> {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(read(rs));
            }, loginName.trim());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("读取业务系统用户失败: loginName={}, table={}", loginName, table(), e);
            throw new BizException(503, "账号主数据库暂不可用，请联系管理员");
        }
    }

    /** 按业务系统主键查账号 */
    public Optional<BizUser> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return template().query(sql("where ID = ?"), rs -> {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(read(rs));
            }, id.trim());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("读取业务系统用户失败: id={}, table={}", id, table(), e);
            throw new BizException(503, "账号主数据库暂不可用，请联系管理员");
        }
    }

    /** 业务系统里的全部账号（按登录名排序） */
    public List<BizUser> listAll() {
        try {
            return template().query(sql("order by LOGIN_NAME"),
                    (rs, rowNum) -> read(rs));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("读取业务系统用户列表失败: table={}", table(), e);
            throw new BizException(503, "账号主数据库暂不可用，请联系管理员");
        }
    }

    /** 业务系统里这个账号是不是启用状态 */
    public boolean isActive(BizUser user) {
        return user != null && user.status() == 1;
    }

    private JdbcTemplate template() {
        String key = appProps.getUser().getDatasource();
        return registry.template(key == null || key.isBlank() ? "t6" : key);
    }

    private String table() {
        String table = appProps.getUser().getTable();
        String name = (table == null || table.isBlank()) ? "sys_user" : table.trim();
        if (!SAFE_TABLE.matcher(name).matches()) {
            throw new BizException(500, "app.user.table 配置不合法: " + name);
        }
        return name;
    }

    private String sql(String where) {
        return "select " + COLUMNS + " from " + table() + " " + where;
    }

    private BizUser read(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BizUser(
                rs.getString("ID"),
                rs.getString("LOGIN_NAME"),
                rs.getString("USERNAME"),
                rs.getString("PASSWORD"),
                rs.getString("SALT") == null ? "" : rs.getString("SALT"),
                toInt(rs.getObject("STATUS")),
                rs.getString("CORP_ID"));
    }

    /** 业务库 STATUS 可能是 decimal / int / varchar，统一转成 int，异常按停用处理 */
    private static int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return new BigDecimal(value.toString().trim()).intValue();
        } catch (Exception e) {
            return 0;
        }
    }
}
