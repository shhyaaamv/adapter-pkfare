package com.verteil.thirdparties.adapter.pkfare.config.sync;

import com.verteil.thirdparties.adapter.pkfare.config.ConfigurationReloader;
import com.verteil.thirdparties.adapter.pkfare.config.ConfigurationReloader.ReloadResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@code POST /reload}: re-reads the configuration on the pod the call reaches, then tells the other pods to do the
 * same through the {@link ReloadBroadcaster} (Kafka). Without a broadcaster (no {@code kafka.bootstrapAddress})
 * only this pod reloads.
 */
@Slf4j
public class ClusterConfigurationReloader {

    private final ConfigurationReloader localReloader;
    /** Null when cluster sync is off. */
    private final ReloadBroadcaster broadcaster;

    public ClusterConfigurationReloader(ConfigurationReloader localReloader, ReloadBroadcaster broadcaster) {
        this.localReloader = localReloader;
        this.broadcaster = broadcaster;
        if (broadcaster == null) {
            log.warn("Configuration reload broadcast is off (no kafka.bootstrapAddress): POST /reload reloads one pod");
        }
    }

    /**
     * Every tree is broadcast, including those that failed here: the other pods read the same table and may well
     * succeed, and the failure is reported either way.
     */
    public ClusterReloadResult reload() {
        ReloadResult local = localReloader.reload();
        return new ClusterReloadResult(local.reloaded(), local.failed(), broadcast(treeNames(local)));
    }

    private static List<String> treeNames(ReloadResult result) {
        return Stream.concat(result.reloaded().stream(), result.failed().stream()).sorted().toList();
    }

    private Broadcast broadcast(List<String> treeNames) {
        return Optional.ofNullable(broadcaster)
                .map(present -> send(present, treeNames))
                .orElse(Broadcast.DISABLED);
    }

    private static Broadcast send(ReloadBroadcaster broadcaster, List<String> treeNames) {
        try {
            broadcaster.broadcastReload(treeNames);
            return Broadcast.SENT;
        } catch (RuntimeException e) {
            log.error("Broadcasting the reload of {} failed; only this pod reloaded", treeNames, e);
            return Broadcast.FAILED;
        }
    }

    /** Whether the other pods were told to reload. */
    public enum Broadcast {
        SENT,
        /** Cluster sync is off: only this pod reloaded. */
        DISABLED,
        /** Only this pod reloaded: the message couldn't be published. */
        FAILED
    }

    /**
     * @param reloaded  trees reloaded on this pod
     * @param failed    trees that failed on this pod (they keep their previous values)
     * @param broadcast whether the other pods were told; how each of them fared is in its own log
     */
    public record ClusterReloadResult(List<String> reloaded, List<String> failed, Broadcast broadcast) {
        /** Every tree reloaded here, and the other pods were told (or there is no cluster sync). */
        public boolean isSuccess() {
            return failed.isEmpty() && broadcast != Broadcast.FAILED;
        }
    }
}
