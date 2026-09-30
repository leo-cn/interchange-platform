package com.interchange.platform.standard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 持久化配置：只建 Hibernate 的 EntityManagerFactory，不引入 Spring Data JPA。
 *
 * <p><b>不要再往这里加 SessionFactory bean</b>：JPA 的 EMF 里 unwrap 出来的 SessionFactory
 * 同时实现了 {@code EntityManagerFactory}，多一个这样的 bean，按类型注入 EMF 的地方
 * （包括 Spring Boot 的 open-in-view）就会报 "required a single bean, but 2 were found"。
 * 需要 Session 走 {@code BaseDao.session()}，需要 withOptions/openSession 走
 * {@code session().getSessionFactory()} 或自行 unwrap。
 */
@Configuration
@EnableTransactionManagement
public class HibernateConfig {

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource,
            @Value("${spring.hibernate.ddl-auto:none}") String ddlAuto) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(
                "com.interchange.platform.standard.sys.entity",
                "com.interchange.platform.dyg.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(hibernateProperties(ddlAuto));
        return factory;
    }

    /**
     * 会话与事务交给 Spring 管理，事务提交时 flush。
     * 不要再手动 {@code session.beginTransaction()}，两端事务会打架。
     */
    @Bean
    public PlatformTransactionManager transactionManager(
            LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        JpaTransactionManager manager = new JpaTransactionManager();
        manager.setEntityManagerFactory(entityManagerFactory.getObject());
        return manager;
    }

    private Map<String, Object> hibernateProperties(String ddlAuto) {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", ddlAuto);
        props.put("hibernate.jdbc.batch_size", 50);
        props.put("hibernate.format_sql", true);
        props.put("hibernate.show_sql", false);
        // 时区：四套 profile 原先都写在 spring.jpa.properties.hibernate.jdbc.time_zone，
        // 自建 EMF 后 Spring Boot 的 JPA 自动配置不再生效，那条配置是死的，挪到这里。
        props.put("hibernate.jdbc.time_zone", "Asia/Shanghai");
        // 方言由 Hibernate 依 DataSource 自动探测，换库不用改配置
        return props;
    }
}
