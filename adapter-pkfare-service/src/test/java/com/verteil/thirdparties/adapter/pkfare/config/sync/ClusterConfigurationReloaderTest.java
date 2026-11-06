package com.verteil.thirdparties.adapter.pkfare.config.sync;

import com.verteil.thirdparties.adapter.pkfare.config.ConfigurationReloader.ReloadResult;
import com.verteil.thirdparties.adapter.pkfare.config.sync.ClusterConfigurationReloader.Broadcast;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.verteil.thirdparties.adapter.pkfare.config.sync.RecordingReloader.ALL;
import static com.verteil.thirdparties.adapter.pkfare.config.sync.RecordingReloader.TREE;
import static org.assertj.core.api.Assertions.assertThat;

class ClusterConfigurationReloaderTest {

    private final List<Collection<String>> broadcasts = new ArrayList<>();
    private final ReloadBroadcaster recordingBroadcaster = broadcasts::add;

    @Test
    void reloadsThisPodThenBroadcasts() {
        var reloader = new RecordingReloader();

        var result = new ClusterConfigurationReloader(reloader, recordingBroadcaster).reload();

        assertThat(reloader.calls).containsExactly(ALL);
        assertThat(broadcasts).containsExactly(List.of(TREE));
        assertThat(result.reloaded()).containsExactly(TREE);
        assertThat(result.broadcast()).isEqualTo(Broadcast.SENT);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void broadcastsTreesThatFailedHereToo() {
        var reloader = new RecordingReloader(new ReloadResult(List.of("b"), List.of("a")));

        var result = new ClusterConfigurationReloader(reloader, recordingBroadcaster).reload();

        assertThat(broadcasts).containsExactly(List.of("a", "b"));
        assertThat(result.failed()).containsExactly("a");
        assertThat(result.broadcast()).isEqualTo(Broadcast.SENT);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void reloadsThisPodOnlyWithoutABroadcaster() {
        var result = new ClusterConfigurationReloader(new RecordingReloader(), null).reload();

        assertThat(result.broadcast()).isEqualTo(Broadcast.DISABLED);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void reportsABroadcastThatFailed() {
        ReloadBroadcaster failing = _ -> {
            throw new IllegalStateException("kafka down");
        };
        var reloader = new RecordingReloader();

        var result = new ClusterConfigurationReloader(reloader, failing).reload();

        assertThat(reloader.calls).as("this pod still reloaded").containsExactly(ALL);
        assertThat(result.reloaded()).containsExactly(TREE);
        assertThat(result.broadcast()).isEqualTo(Broadcast.FAILED);
        assertThat(result.isSuccess()).isFalse();
    }
}
