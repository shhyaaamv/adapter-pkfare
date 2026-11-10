package com.verteil.thirdparties.adapter.pkfare.config.sync;

import com.verteil.thirdparties.adapter.pkfare.config.ConfigurationReloader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.DisposableBean;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * Consumes the reload topic on a daemon thread and reloads the named trees on this pod. Each pod's consumer group is
 * unique ({@link KafkaClusterSyncConfigs}), so every pod gets every message: a broadcast, not a work queue. Messages
 * this pod published are skipped (it reloaded before publishing). Message format: {@link ReloadMessage}.
 *
 * <p>Consumption starts at the latest offset, so a message published between this pod's startup load and its
 * partition assignment would be missed. To close that gap the trees are re-read whenever partitions are assigned.
 */
@Slf4j
@RequiredArgsConstructor
public class KafkaReloadSubscriber implements DisposableBean {

    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
    private static final Duration POLL_ERROR_BACKOFF = Duration.ofSeconds(5);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);

    private final Consumer<String, String> consumer;
    private final String topic;
    private final String nodeId;
    private final ConfigurationReloader reloader;

    private volatile Thread pollThread;

    /** Bean init method. The consumer is used only on the poll thread (it isn't thread safe); stop with {@link #destroy()}. */
    public void start() {
        pollThread = Thread.ofPlatform().name("pkfare-config-reload-subscriber").daemon().start(this::pollLoop);
    }

    private void pollLoop() {
        consumer.subscribe(List.of(topic), new ReloadOnAssignment());
        log.info("Listening for configuration reloads on {}", topic);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                pollOnce();
            }
        } catch (WakeupException | InterruptException e) {
            log.debug("Configuration reload subscriber woken up to stop");
        } finally {
            consumer.close(STOP_TIMEOUT);
            log.info("Stopped listening for configuration reloads on {}", topic);
        }
    }

    private void pollOnce() {
        try {
            consumer.poll(POLL_TIMEOUT).forEach(consumerRecord -> handle(consumerRecord.value()));
        } catch (WakeupException | InterruptException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Polling {} for configuration reloads failed; retrying in {}", topic, POLL_ERROR_BACKOFF, e);
            backOff();
        }
    }

    private static void backOff() {
        try {
            Thread.sleep(POLL_ERROR_BACKOFF);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Never throws: a bad message is logged and skipped. */
    void handle(String value) {
        try {
            ReloadMessage message = ReloadMessage.fromJson(value);
            if (message.nodeId() == null) {
                log.warn("Skipping a reload message without a node id: {}", value);
                return;
            }
            if (nodeId.equals(message.nodeId())) {
                log.debug("Skipping this pod's own reload message: {}", value);
                return;
            }
            message.treeName().ifPresentOrElse(
                    treeName -> reload(treeName, message.nodeId()),
                    () -> log.info("Ignoring configurator event {} from {}", message.event(), message.nodeId()));
        } catch (RuntimeException e) {
            log.error("Skipping an unreadable reload message: {}", value, e);
        }
    }

    private void reload(String treeName, String publisher) {
        log.info("Reloading configuration tree {} as published by {}", treeName, publisher);
        reloader.reload(treeName);
    }

    /** Called on the poll thread. */
    void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        if (partitions.isEmpty()) {
            return;
        }
        log.info("Assigned {}; re-reading the configuration in case a reload was published before", partitions);
        reloader.reload();
    }

    @Override
    public void destroy() {
        consumer.wakeup();
        Thread thread = pollThread;
        if (thread == null) {
            return;
        }
        try {
            thread.join(STOP_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final class ReloadOnAssignment implements ConsumerRebalanceListener {
        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            // nothing to hand over: no offsets are committed and this group has one member
        }

        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            KafkaReloadSubscriber.this.onPartitionsAssigned(partitions);
        }
    }
}
