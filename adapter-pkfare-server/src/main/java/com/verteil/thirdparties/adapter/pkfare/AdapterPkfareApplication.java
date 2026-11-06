package com.verteil.thirdparties.adapter.pkfare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * The only database access is the configurator's SELECT on {@code agency_adapter_tbls.pkfare_configs} through
 * {@code DataSourceConfigs}. Boot's JDBC auto-configurations are excluded so nothing else uses that DataSource
 * (no SQL init scripts, no health-check queries), and the Mongo ones so the transitive spring-data-mongodb jar
 * never connects anywhere.
 */
@SpringBootApplication(excludeName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.jdbc.DataSourceHealthContributorAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.jdbc.DataSourcePoolMetricsAutoConfiguration",
        "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.mongo.MongoMetricsAutoConfiguration"})
@ComponentScan({
        "com.verteil.thirdparties.adapter.pkfare",
        "com.verteil.thirdparties.common.metric"})
public class AdapterPkfareApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdapterPkfareApplication.class, args);
    }
}
