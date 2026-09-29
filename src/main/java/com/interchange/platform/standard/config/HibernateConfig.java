package com.interchange.platform.standard.config;

import org.hibernate.SessionFactory;
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
 * 持久化配置：只建 Hibernate 的 EntityManagerFactory / SessionFactory，不引入 Spring Data JPA。
 *
 * <p>实体注解统一用 jakarta.persistence（{@code @Entity} / {@code @Id} / {@code @Column}），
 * 仓储层直接用 Hibernate 的 {@code Session}，各 DAO 自己写 HQL / 原生 SQL，
 * 不生成 Repository 代理，也不用 Criteria API 拼查询。
 *
 * <p>实体扫描范围：
 * <ul>
 *   <li>{@code com.interchange.platform.standard.sys.entity} —— 平台自身的元数据表；</li>
 *   <li>{@code com.interchange.platform.dyg.entity} —— 标准接口的只读视图，
 *       以及 MDM 接收要写入的资金系统基线表（{@code BtBankAcc} / {@code SysCorp} 等）。</li>
 * </ul>
 *
 * <p><b>重要</b>：{@code dyg.entity} 里有一部分映射的是 dyg-erp 的资金系统基线表，
 * {@code ddl-auto} 必须保持 {@code none}，否则 Hibernate 会去改别人的表结构。
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

    /** 供 DAO 直接注入，省得层层 unwrap */
    @Bean
    public SessionFactory sessionFactory(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return entityManagerFactory.getObject().unwrap(SessionFactory.class);
    }

    private Map<String, Object> hibernateProperties(String ddlAuto) {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", ddlAuto);
        props.put("hibernate.jdbc.batch_size", 50);
        props.put("hibernate.format_sql", true);
        props.put("hibernate.show_sql", false);
        // 方言由 Hibernate 依 DataSource 自动探测，换库不用改配置
        return props;
    }
}
