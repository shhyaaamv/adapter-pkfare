package com.verteil.thirdparties.adapter.pkfare.controller;

import com.verteil.thirdparties.adapter.pkfare.config.sync.ClusterConfigurationReloader;
import com.verteil.thirdparties.adapter.pkfare.config.sync.ClusterConfigurationReloader.ClusterReloadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * {@code pkfare_configs} is read once at startup and kept in memory; {@code POST /reload} re-reads it on the pod the
 * call reaches and, when Kafka is configured, publishes the reload so every other pod re-reads it too.
 */
@RestController
@RequiredArgsConstructor
public class ConfigurationController {
    private final ClusterConfigurationReloader clusterConfigurationReloader;

    /**
     * 200 when every tree reloaded here and the broadcast was sent (or is off); 500 when a tree failed here (it keeps
     * serving its previous values) or the broadcast couldn't be published.
     */
    @PostMapping(path = "reload", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ClusterReloadResult> reload() {
        ClusterReloadResult result = clusterConfigurationReloader.reload();
        return ResponseEntity.status(result.isSuccess() ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
