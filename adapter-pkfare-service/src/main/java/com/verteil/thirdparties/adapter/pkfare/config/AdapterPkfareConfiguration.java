package com.verteil.thirdparties.adapter.pkfare.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verteil.chain.Catalog;
import com.verteil.chain.Chain;
import com.verteil.chain.impl.ImmutableCatalog;
import com.verteil.configurator.vdc.client.VdcConfiguratorServiceClient;
import com.verteil.thirdparties.adapter.pkfare.client.ScheduledConnectionPoolMonitor;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import com.verteil.thirdparties.common.config.ConnectorRedisConfig;
import com.verteil.thirdparties.connector.common.chain.FetchConfigurationsCmd;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.apache.hc.core5.util.Timeout.ofMilliseconds;

@Configuration
@EnableScheduling
@Import(ConnectorRedisConfig.class)
public class AdapterPkfareConfiguration {

    @Bean
    public Catalog<PkfareAdapterContext> catalog(Map<String, Chain<PkfareAdapterContext>> chains) {
        return ImmutableCatalog.<PkfareAdapterContext>newBuilder().addAll(chains).build();
    }

    @Bean
    @Primary
    public VdcConfiguratorServiceClient vdcConfiguratorServiceClient(@Value("${client.address.vdcConfigurator}") String vdcConfiguratorAddress) {
        return new VdcConfiguratorServiceClient(vdcConfiguratorAddress);
    }

    @Bean
    public FetchConfigurationsCmd<PkfareAdapterContext> fetchConfigurationsCmd(VdcConfiguratorServiceClient vdcConfiguratorServiceClient) {
        return new FetchConfigurationsCmd<>(vdcConfiguratorServiceClient);
    }

    @Bean(name = "pkfareAsyncThreadPoolTaskExecutor")
    @Primary
    public Executor getAsyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** VDC NDC 16.1 JSON, used for Entry-Gate requests/responses. PKFARE JSON uses the primary mapper. */
    @Bean(name = "ndcObjectMapper")
    public ObjectMapper ndcObjectMapper() {
        return JsonMappers.ndc();
    }

    @Bean
    public PoolingHttpClientConnectionManager connectionManager(
            @Value("${connector.http.poolMax:300}") int poolMax,
            @Value("${connector.http.connectTimeoutMillis:5000}") int connectTimeoutMillis,
            @Value("${connector.http.responseTimeoutMillis:180000}") int responseTimeoutMillis
    ) {
        var connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(poolMax);
        connectionManager.setDefaultMaxPerRoute(poolMax);
        connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(ofMilliseconds(connectTimeoutMillis))
                .setSocketTimeout(ofMilliseconds(responseTimeoutMillis))
                .build()
        );
        return connectionManager;
    }

    @Bean
    public ScheduledConnectionPoolMonitor scheduledConnectionPoolMonitor(
            PoolingHttpClientConnectionManager connectionManager,
            @Value("${connector.monitor.idleTime:30}") long idleTimeSeconds
    ) {
        return new ScheduledConnectionPoolMonitor(idleTimeSeconds, connectionManager);
    }

    /** Pooled client for Entry-Gate. Response decompression (gzip/deflate) is on by default. */
    @Bean
    public CloseableHttpClient pkfareHttpClient(
            PoolingHttpClientConnectionManager connectionManager,
            @Value("${connector.http.poolTimeoutMillis:1000}") int poolTimeoutMillis,
            @Value("${connector.http.responseTimeoutMillis:180000}") int responseTimeoutMillis
    ) {
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(ofMilliseconds(poolTimeoutMillis))
                        .setResponseTimeout(ofMilliseconds(responseTimeoutMillis))
                        .build()
                )
                .build();
    }
}
