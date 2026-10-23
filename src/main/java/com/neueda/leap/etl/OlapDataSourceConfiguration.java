package com.neueda.leap.etl;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.lang.NonNull;

@Configuration
public class OlapDataSourceConfiguration {
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties tradingDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    DataSource dataSource(
        @Qualifier("tradingDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "olapDataSource")
    DataSource olapDataSource(
        @Qualifier("olapDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "olapDataSourceProperties")
    @ConfigurationProperties("olap.datasource")
    DataSourceProperties olapDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "olapJdbcTemplate")
    JdbcTemplate olapJdbcTemplate(
        @Qualifier("olapDataSource") @NonNull DataSource olapDataSource
    ) {
        return new JdbcTemplate(olapDataSource);
    }

    @Bean(name = "oltpJdbcTemplate")
    JdbcTemplate oltpJdbcTemplate(
        @Qualifier("oltpDataSource") @NonNull DataSource oltpDataSource
    ) {
        return new JdbcTemplate(oltpDataSource);
    }

    @Bean(name = "oltpDataSourceProperties")
    @ConfigurationProperties("olap.source")
    DataSourceProperties oltpDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "oltpDataSource")
    DataSource oltpDataSource(
        @Qualifier("oltpDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "olapTransactionManager")
    PlatformTransactionManager olapTransactionManager(
        @Qualifier("olapDataSource") @NonNull DataSource olapDataSource
    ) {
        return new DataSourceTransactionManager(olapDataSource);
    }

    @Bean(name = "transactionManager")
    @Primary
    PlatformTransactionManager transactionManager(@NonNull EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
