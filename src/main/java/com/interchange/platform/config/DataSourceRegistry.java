package com.interchange.platform.config;

import com.interchange.platform.common.BizException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源注册表。
 *
 * <p>SQL 取数任务可以在多个库之间选择数据源：
 * <ul>
 *   <li>{@code main} —— 平台自身库（默认）；</li>
 *   <li>其他 key —— application.yml 中 {@code app.extra-datasources} 配置的业务库（项目系统库等）。</li>
 * </ul>
 * 额外数据源按需初始化（懒加载），并在应用关闭时统一释放。
 */
@Component
public class DataSourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(DataSourceRegistry.class);
    public static final String MAIN = "main";

    private final DataSource primaryDataSource;
    private final AppProps appProps;

    /** 已创建的数据源（含 main） */
    private final Map<String, JdbcTemplate> templates = new ConcurrentHashMap<>();
    /** 需要在关闭时释放的连接池（不含 Spring 托管的主数据源） */
    private final Map<String, HikariDataSource> managedPools = new ConcurrentHashMap<>();

    public DataSourceRegistry(DataSource primaryDataSource, AppProps appProps) {
        this.primaryDataSource = primaryDataSource;
        this.appProps = appProps;
        this.templates.put(MAIN, new JdbcTemplate(primaryDataSource));
    }

    /** 数据源下拉选项 */
    public List<Map<String, String>> listOptions() {
        List<Map<String, String>> options = new ArrayList<>();
        options.add(option(MAIN, "平台自身库 (main)"));
        if (appProps.getExtraDatasources() != null) {
            for (AppProps.ExtraDataSource ds : appProps.getExtraDatasources()) {
                if (ds.getKey() != null && !ds.getKey().isBlank()) {
                    String name = ds.getName() == null || ds.getName().isBlank() ? ds.getKey() : ds.getName();
                    options.add(option(ds.getKey(), name + " (" + ds.getKey() + ")"));
                }
            }
        }
        return options;
    }

    public boolean exists(String key) {
        return MAIN.equals(key) || findExtra(key) != null;
    }

    /** 获取指定 key 的 JdbcTemplate，非 main 时懒加载连接池 */
    public JdbcTemplate template(String key) {
        String dsKey = (key == null || key.isBlank()) ? MAIN : key;
        JdbcTemplate template = templates.get(dsKey);
        if (template != null) {
            return template;
        }
        AppProps.ExtraDataSource cfg = findExtra(dsKey);
        if (cfg == null) {
            throw new BizException(400, "数据源不存在: " + dsKey + "，请在 application.yml 的 app.extra-datasources 中配置");
        }
        synchronized (this) {
            JdbcTemplate exists = templates.get(dsKey);
            if (exists != null) {
                return exists;
            }
            HikariConfig hikari = new HikariConfig();
            hikari.setPoolName("interchange-" + dsKey);
            hikari.setJdbcUrl(cfg.getUrl());
            hikari.setUsername(cfg.getUsername());
            hikari.setPassword(cfg.getPassword());
            if (cfg.getDriver() != null && !cfg.getDriver().isBlank()) {
                hikari.setDriverClassName(cfg.getDriver());
            }
            hikari.setMaximumPoolSize(5);
            hikari.setMinimumIdle(1);
            hikari.setConnectionTimeout(10000);
            HikariDataSource pool = new HikariDataSource(hikari);
            managedPools.put(dsKey, pool);
            JdbcTemplate created = new JdbcTemplate(pool);
            created.setQueryTimeout(60);
            templates.put(dsKey, created);
            log.info("已初始化额外数据源: key={}, url={}", dsKey, cfg.getUrl());
            return created;
        }
    }

    private AppProps.ExtraDataSource findExtra(String key) {
        if (appProps.getExtraDatasources() == null) {
            return null;
        }
        return appProps.getExtraDatasources().stream()
                .filter(ds -> key.equals(ds.getKey()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, String> option(String key, String name) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key", key);
        map.put("name", name);
        return map;
    }

    @PreDestroy
    public void destroy() {
        managedPools.forEach((key, pool) -> {
            try {
                pool.close();
                log.info("已释放数据源: {}", key);
            } catch (Exception e) {
                log.warn("释放数据源失败: {}", key, e);
            }
        });
    }
}
