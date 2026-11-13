package com.verteil.thirdparties.adapter.pkfare.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verteil.chain.Catalog;
import com.verteil.chain.Chain;
import com.verteil.chain.impl.ImmutableCatalog;
import com.verteil.thirdparties.adapter.pkfare.client.ScheduledConnectionPoolMonitor;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ENTRY_GATE_CIRCUIT_BREAKER;
import static org.apache.hc.core5.util.Timeout.ofMilliseconds;

@Slf4j
@Configuration
@EnableScheduling
@EnableConfigurationProperties({PkfareProperties.class, VerteilProperties.class})
public class AdapterPkfareConfiguration {

    @Bean
    public Catalog<PkfareAdapterContext> catalog(Map<String, Chain<PkfareAdapterContext>> chains) {
        return ImmutableCatalog.<PkfareAdapterContext>newBuilder().addAll(chains).build();
    }

    @Bean(name = "pkfareAsyncThreadPoolTaskExecutor")
    @Primary
    public Executor getAsyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** See {@link VerteilProperties.CircuitBreakerSettings}. State and call counts are exported to Prometheus. */
    @Bean
    public CircuitBreaker entryGateCircuitBreaker(VerteilProperties verteilProperties, MeterRegistry meterRegistry) {
        VerteilProperties.CircuitBreakerSettings settings = verteilProperties.getCircuitBreaker();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .failureRateThreshold(settings.getFailureRateThreshold())
                .slowCallRateThreshold(settings.getSlowCallRateThreshold())
                .slowCallDurationThreshold(Duration.ofMillis(settings.getSlowCallDurationMillis()))
                .slidingWindowSize(settings.getSlidingWindowSize())
                .minimumNumberOfCalls(settings.getMinimumNumberOfCalls())
                .waitDurationInOpenState(Duration.ofMillis(settings.getWaitDurationInOpenStateMillis()))
                .permittedNumberOfCallsInHalfOpenState(settings.getPermittedNumberOfCallsInHalfOpenState())
                .build());
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        CircuitBreaker circuitBreaker = registry.circuitBreaker(ENTRY_GATE_CIRCUIT_BREAKER);
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn("Entry-Gate circuit breaker {}", event.getStateTransition()));
        return circuitBreaker;
    }

    /** VDC NDC 16.1 JSON, used for Entry-Gate requests/responses. PKFARE JSON uses the primary mapper. */
    @Bean(name = "ndcObjectMapper")
    public ObjectMapper ndcObjectMapper() {
        return JsonMappers.ndc();
    }

    @Bean
    public PoolingHttpClientConnectionManager connectionManager(
            @Value("${adapter.http.poolMax:300}") int poolMax,
            @Value("${adapter.http.connectTimeoutMillis:5000}") int connectTimeoutMillis,
            @Value("${adapter.http.responseTimeoutMillis:180000}") int responseTimeoutMillis
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
            @Value("${adapter.monitor.idleTime:30}") long idleTimeSeconds
    ) {
        return new ScheduledConnectionPoolMonitor(idleTimeSeconds, connectionManager);
    }

    /** Pooled client for Entry-Gate. Response decompression (gzip/deflate) is on by default. */
    @Bean
    public CloseableHttpClient pkfareHttpClient(
            PoolingHttpClientConnectionManager connectionManager,
            @Value("${adapter.http.poolTimeoutMillis:1000}") int poolTimeoutMillis,
            @Value("${adapter.http.responseTimeoutMillis:180000}") int responseTimeoutMillis
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
