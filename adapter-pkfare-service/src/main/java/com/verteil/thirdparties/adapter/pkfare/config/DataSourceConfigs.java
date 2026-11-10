package com.verteil.thirdparties.adapter.pkfare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * The adapter's only DataSource. It connects as a user restricted to {@code agency_adapter_tbls}, and the only SQL
 * issued through it is the configurator's SELECT on {@code pkfare_configs} (see {@link ConfiguratorConfigs}).
 */
@Configuration
public class DataSourceConfigs {

    /** Non pooling: the database is read only when the configuration tree is (re)loaded. */
    @Bean
    @ConfigurationProperties("agency-adapter.datasource")
    DataSource agencyAdapterDataSource() {
        return DataSourceBuilder.create().type(DriverManagerDataSource.class).build();
    }
}
