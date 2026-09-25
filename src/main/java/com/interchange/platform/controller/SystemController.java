package com.interchange.platform.controller;

import com.interchange.platform.common.R;
import com.interchange.platform.common.Utils;
import com.interchange.platform.config.DataSourceRegistry;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台自身信息：健康检查 + 当前数据库配置回显（便于确认四种数据库切换是否生效）。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private static final Logger log = LoggerFactory.getLogger(SystemController.class);

    private final DataSource dataSource;
    private final DataSourceRegistry dataSourceRegistry;
    private final Environment environment;
    private final EntityManagerFactory entityManagerFactory;

    public SystemController(DataSource dataSource,
                            DataSourceRegistry dataSourceRegistry,
                            Environment environment,
                            EntityManagerFactory entityManagerFactory) {
        this.dataSource = dataSource;
        this.dataSourceRegistry = dataSourceRegistry;
        this.environment = environment;
        this.entityManagerFactory = entityManagerFactory;
    }

    /** 探活接口（无需登录） */
    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean dbOk;
        String dbError = null;
        try {
            Integer one = new JdbcTemplate(dataSource).queryForObject("select 1", Integer.class);
            dbOk = one != null && one == 1;
        } catch (Exception e) {
            dbOk = false;
            dbError = e.getMessage();
        }
        data.put("status", dbOk ? "UP" : "DEGRADED");
        data.put("application", "interchange-platform");
        data.put("database", dbOk ? "connected" : "error");
        if (dbError != null) {
            data.put("databaseError", dbError);
        }
        data.put("time", Utils.format(LocalDateTime.now()));
        return R.ok(data);
    }

    /** 当前数据库与运行环境信息 */
    @GetMapping("/info")
    public R<Map<String, Object>> info() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profiles", String.join(",", environment.getActiveProfiles()));
        data.put("datasourceUrl", maskUrl(environment.getProperty("spring.datasource.url")));
        data.put("driver", environment.getProperty("spring.datasource.driver-class-name"));
        // 方言不再由配置写死，这里回显 Hibernate 实际识别到的方言
        data.put("dialect", actualDialect());
        data.put("ddlAuto", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        try (Connection conn = dataSource.getConnection()) {
            data.put("productName", conn.getMetaData().getDatabaseProductName());
            data.put("productVersion", conn.getMetaData().getDatabaseProductVersion());
        } catch (Exception e) {
            data.put("productName", "unknown: " + e.getMessage());
        }
        data.put("availableDatasources", dataSourceRegistry.listOptions());
        return R.ok(data);
    }

    /** 隐藏连接串中的账号密码 */
    private String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("(?i)(password=)[^;&]*", "$1******")
                .replaceAll("(?i)(user=)[^;&]*", "$1******");
    }

    /** Hibernate 实际生效的方言（随连接自动识别，非配置项） */
    private String actualDialect() {
        try {
            SessionFactoryImplementor sfi = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
            return sfi.getJdbcServices().getDialect().getClass().getName();
        } catch (Exception e) {
            log.warn("读取 Hibernate 实际方言失败，回退到配置项：{}", e.toString());
        }
        try {
            SessionFactory sf = entityManagerFactory.unwrap(SessionFactory.class);
            if (sf instanceof SessionFactoryImplementor sfi) {
                return sfi.getJdbcServices().getDialect().getClass().getName();
            }
        } catch (Exception e) {
            log.warn("读取 Hibernate 实际方言失败（二次尝试）：{}", e.toString());
        }
        // 兜底：返回显式配置的方言（如果配置了的话）
        return environment.getProperty("spring.jpa.properties.hibernate.dialect");
    }
}
