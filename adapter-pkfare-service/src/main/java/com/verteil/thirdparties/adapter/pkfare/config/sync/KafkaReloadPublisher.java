package com.verteil.thirdparties.adapter.pkfare.config.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.DisposableBean;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes one {@code #<RELOAD_TREE><treeName>} message per tree (keyed by the tree name) to the reload topic,
 * which every pod consumes ({@link KafkaReloadSubscriber}). Unlike vdc-configurator-lib's fire-and-forget
 * publisher it waits for the acknowledgements, so {@code POST /reload} can say whether the other pods were told.
 */
@Slf4j
@RequiredArgsConstructor
public class KafkaReloadPublisher implements ReloadBroadcaster, DisposableBean {

    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

    private final Producer<String, String> producer;
    private final String topic;
    private final String nodeId;
    private final Duration sendTimeout;

    @Override
    public void broadcastReload(Collection<String> treeNames) {
        List<Future<RecordMetadata>> sent = treeNames.stream().map(this::send).toList();
        sent.forEach(this::awaitAcknowledgement);
        log.info("Published a reload of {} to {}", treeNames, topic);
    }

    private Future<RecordMetadata> send(String treeName) {
        return producer.send(new ProducerRecord<>(topic, treeName, ReloadMessage.reloadTree(nodeId, treeName).toJson()));
    }

    private void awaitAcknowledgement(Future<RecordMetadata> sent) {
        try {
            sent.get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing a reload to " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Publishing a reload to " + topic + " failed", e);
        }
    }

    @Override
    public void destroy() {
        producer.close(CLOSE_TIMEOUT);
    }
}
