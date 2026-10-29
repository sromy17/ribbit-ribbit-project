package com.neueda.leap.config;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.JpaTransactionManager;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.jspecify.annotations.NonNull;

@Configuration
public class OlapDataSourceConfig {
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties oltpDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = {"dataSource", "oltpDataSource"})
    @Primary
    public DataSource oltpDataSource() {
        return oltpDataSourceProperties().initializeDataSourceBuilder().build();
    }

    @Bean
    @ConfigurationProperties("app.datasource.olap")
    public DataSourceProperties olapDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "olapDataSource")
    public DataSource olapDataSource() {
        return olapDataSourceProperties().initializeDataSourceBuilder().build();
    }

    @Bean(name = "olapJdbcTemplate")
    public JdbcTemplate olapJdbcTemplate(@Qualifier("olapDataSource") @NonNull DataSource olapDataSource) {
        return new JdbcTemplate(olapDataSource);
    }

    @Bean(name = "oltpJdbcTemplate")
    public JdbcTemplate oltpJdbcTemplate(@Qualifier("dataSource") @NonNull DataSource oltpDataSource) {
        return new JdbcTemplate(oltpDataSource);
    }

    @Bean(name = "transactionManager")
    @Primary
    public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean(name = "olapTransactionManager")
    public DataSourceTransactionManager olapTransactionManager(
        @Qualifier("olapDataSource") @NonNull DataSource olapDataSource
    ) {
        return new DataSourceTransactionManager(olapDataSource);
    }
}
