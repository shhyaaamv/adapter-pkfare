package com.verteil.thirdparties.adapter.pkfare.config;

import com.verteil.configurator.core.ConfigurationEvaluator;
import com.verteil.configurator.core.ConfigurationTree;
import com.verteil.configurator.core.ImmutableConfigurationTree;
import com.verteil.configurator.core.data.ConfigurationDataStream;
import com.verteil.configurator.core.data.sql.SqlConfigurationDataStream;
import com.verteil.configurator.vdc.VdcConfiguratorService;
import com.verteil.thirdparties.adapter.pkfare.config.sync.ClusterConfigurationReloader;
import com.verteil.thirdparties.adapter.pkfare.config.sync.ReloadBroadcaster;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import javax.sql.DataSource;
import java.util.List;

import static com.verteil.configurator.vdc.VdcConfiguratorService.ARG_API_SERVICE;

/**
 * In-process configurator over {@code agency_adapter_tbls.pkfare_configs}: one SQL stream, one tree with the
 * {@code service} column as its only dimension, an evaluator and the {@link VdcConfiguratorService} that
 * connector-common's {@code FetchConfigurationsCmd} reads from.
 */
@Configuration
public class ConfiguratorConfigs {

    static final String PKFARE_CONFIG_TREE = "pkfareConfigTree";

    /**
     * Columns other than {@code id, config_group, name, value} are tree dimensions, matched to
     * {@code setArguments} by position (their names don't matter). {@code service} is aliased to
     * {@code api_service} only to mirror the shared {@code internal_service_configs} table.
     */
    static final String PKFARE_CONFIGS_QUERY =
            "select id, config_group, name, service as api_service, value from agency_adapter_tbls.pkfare_configs";

    @Bean
    public SqlConfigurationDataStream pkfareConfigDataStream(@Qualifier("agencyAdapterDataSource") DataSource dataSource) {
        var dataStream = new SqlConfigurationDataStream(PKFARE_CONFIGS_QUERY);
        dataStream.setDataSource(dataSource);
        return dataStream;
    }

    /** Prototype, so {@link EmbeddedVdcConfiguratorService#reload()} can build a fresh tree. */
    @Bean(PKFARE_CONFIG_TREE)
    @Scope("prototype")
    public ConfigurationTree<String> pkfareConfigTree(ConfigurationDataStream<String> pkfareConfigDataStream) {
        return buildPkfareConfigTree(pkfareConfigDataStream);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public ConfigurationEvaluator<String> configurationEvaluator(List<ConfigurationTree<String>> configurationTrees) {
        return new ConfigurationEvaluator<>(configurationTrees.toArray(ConfigurationTree[]::new));
    }

    /** Typed as the implementation: it is both the {@link VdcConfiguratorService} and the {@link ConfigurationReloader}. */
    @Bean
    @SuppressWarnings("unchecked")
    public EmbeddedVdcConfiguratorService vdcConfiguratorService(ConfigurationEvaluator<String> configurationEvaluator,
                                                         ApplicationContext applicationContext) {
        return new EmbeddedVdcConfiguratorService(configurationEvaluator,
                treeName -> applicationContext.getBean(treeName, ConfigurationTree.class));
    }

    /** {@code POST /reload}: this pod, then the others when Kafka is configured ({@code config.sync.KafkaClusterSyncConfigs}). */
    @Bean
    public ClusterConfigurationReloader clusterConfigurationReloader(ConfigurationReloader configurationReloader,
                                                                     ObjectProvider<ReloadBroadcaster> reloadBroadcaster) {
        return new ClusterConfigurationReloader(configurationReloader, reloadBroadcaster.getIfAvailable());
    }

    /** A NULL {@code service} is the wildcard: a row for the requested service wins over it. */
    static ConfigurationTree<String> buildPkfareConfigTree(ConfigurationDataStream<String> dataStream) {
        return new ImmutableConfigurationTree.Builder<>(PKFARE_CONFIG_TREE, dataStream)
                .setArguments(new String[]{ARG_API_SERVICE})
                .build();
    }
}
