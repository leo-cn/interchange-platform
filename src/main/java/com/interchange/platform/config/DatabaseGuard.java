package com.interchange.platform.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * 启动自检：把实际连上的数据库打印出来，并拦住"误用内存库"这种隐形坑。
 *
 * <p>为什么需要它：平台支持多数据库，配置由 Profile / 环境变量决定。
 * 一旦启动参数被传成空串（例如 {@code --spring.profiles.active= } 或
 * {@code --spring.datasource.url=}），Spring Boot 会静默退化成内嵌 H2 内存库 ——
 * 表面上服务照常启动，但数据重启即丢，排查起来非常费劲。
 * 这里在容器启动阶段就检测并直接报错，同时给出修复提示。
 */
@Component
public class DatabaseGuard {

    private static final Logger log = LoggerFactory.getLogger(DatabaseGuard.class);

    private final DataSource dataSource;
    private final AppProps appProps;
    private final Environment environment;

    public DatabaseGuard(DataSource dataSource, AppProps appProps, Environment environment) {
        this.dataSource = dataSource;
        this.appProps = appProps;
        this.environment = environment;
    }

    @PostConstruct
    public void check() {
        String[] profiles = environment.getActiveProfiles();
        String url;
        String product;
        String version;
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            url = meta.getURL();
            product = meta.getDatabaseProductName();
            version = meta.getDatabaseProductVersion();
        } catch (SQLException e) {
            throw new IllegalStateException("数据库连接失败，请检查 DB_URL / DB_USER / DB_PASSWORD 配置：" + e.getMessage(), e);
        }

        log.info("""

                ==========================================================
                  接口交换平台 启动自检
                  Profile  : {}
                  数据库   : {} {}
                  连接串   : {}
                ==========================================================""",
                profiles.length == 0 ? "(default，未指定 Profile)" : Arrays.toString(profiles),
                product, version, maskPassword(url));

        if (isMemoryDatabase(url) && !appProps.isAllowMemoryDb()) {
            throw new IllegalStateException("""

                    ==========================================================
                    X 检测到当前连接的是 H2 内存数据库：%s

                    这通常意味着启动参数被传成了空字符串，导致数据库配置丢失，例如：
                      java -jar interchange-platform.jar --spring.profiles.active=
                      java -jar interchange-platform.jar --spring.datasource.url=

                    内存库中的数据在进程退出后即全部丢失，为避免误用，平台已拒绝启动。

                    修复办法（任选其一）：
                      1) 使用 start.bat 启动，或直接 java -jar interchange-platform.jar
                         （不带参数时默认走 MySQL：127.0.0.1:3306/t6，账号 root/123）
                      2) 显式指定 Profile：--spring.profiles.active=mysql
                      3) 确实要用内存库做临时测试：加 --app.allow-memory-db=true
                    =========================================================="""
                    .formatted(url));
        }

        if (isMemoryDatabase(url)) {
            log.warn("当前使用 H2 内存库（app.allow-memory-db=true），数据在进程退出后将全部丢失，仅建议用于临时测试。");
        }
    }

    private boolean isMemoryDatabase(String url) {
        return url != null && url.toLowerCase().startsWith("jdbc:h2:mem:");
    }

    /** 只打印库名，避免口令等敏感信息落日志 */
    private String maskPassword(String url) {
        if (url == null) {
            return "(未获取到)";
        }
        return url.replaceAll("(?i)(password|pwd)=([^;&]*)", "$1=***");
    }
}
