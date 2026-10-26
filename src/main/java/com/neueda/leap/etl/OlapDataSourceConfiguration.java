package com.neueda.leap.etl;

import com.neueda.leap.config.OlapDataSourceConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Compatibility entry point: trading and ETL share one source and transaction setup. */
@Configuration(proxyBeanMethods = false)
@Import(OlapDataSourceConfig.class)
public class OlapDataSourceConfiguration { }
