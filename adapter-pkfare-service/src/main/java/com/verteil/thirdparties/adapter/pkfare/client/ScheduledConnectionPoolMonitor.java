package com.verteil.thirdparties.adapter.pkfare.client;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@AllArgsConstructor
public class ScheduledConnectionPoolMonitor {

    private final long idleTimeSeconds;
    private final PoolingHttpClientConnectionManager connectionManager;

    @Scheduled(fixedDelayString = "${connector.monitor.task.interval}")
    public void evictExpiredAndIdleConnections() {
        log.trace("Adapter PKFARE connection pool monitor executed");
        connectionManager.closeExpired();
        connectionManager.closeIdle(TimeValue.ofSeconds(idleTimeSeconds));
    }
}
