/*
package com.neueda.leap.trading.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class OLAPDataSourceConfig {

    @Bean(name = "olapDataSource")
    @ConfigurationProperties(prefix = "olap.datasource")
    public DataSource olapDataSource() {
        return new HikariDataSource();
    }
}
*/
// OLAP datasource config disabled - using single database for now
