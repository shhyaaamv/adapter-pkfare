package com.verteil.thirdparties.adapter.pkfare.config.sync;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.verteil.thirdparties.adapter.pkfare.config.sync.RecordingReloader.ALL;
import static com.verteil.thirdparties.adapter.pkfare.config.sync.RecordingReloader.TREE;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaReloadSubscriberTest {

    private static final String TOPIC = "adapter-pkfare-configurator-internal";
    private static final String THIS_POD = "pod-1#uuid-1";
    private static final String OTHER_POD = "pod-2#uuid-2";

    private final RecordingReloader reloader = new RecordingReloader();
    private final MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    private final KafkaReloadSubscriber subscriber = new KafkaReloadSubscriber(consumer, TOPIC, THIS_POD, reloader);

    @Test
    void reloadsTheTreeAnotherPodPublished() {
        subscriber.handle(ReloadMessage.reloadTree(OTHER_POD, TREE).toJson());

        assertThat(reloader.calls).containsExactly(TREE);
    }

    @Test
    void skipsItsOwnMessages() {
        subscriber.handle(ReloadMessage.reloadTree(THIS_POD, TREE).toJson());

        assertThat(reloader.calls).isEmpty();
    }

    @Test
    void skipsMessagesWithoutATreeOrNodeOrThatAreUnreadable() {
        subscriber.handle(new ReloadMessage(OTHER_POD, "#<RELOAD_ARG_ENHANCERS>").toJson());
        subscriber.handle(new ReloadMessage(null, "#<RELOAD_TREE>" + TREE).toJson());
        subscriber.handle("not json");
        subscriber.handle(null);

        assertThat(reloader.calls).isEmpty();
    }

    @Test
    void reloadsEverythingOnAssignmentOnly() {
        subscriber.onPartitionsAssigned(List.of());
        assertThat(reloader.calls).isEmpty();

        subscriber.onPartitionsAssigned(List.of(new TopicPartition(TOPIC, 0)));
        assertThat(reloader.calls).containsExactly(ALL);
    }

    @Test
    void pollsTheTopicUntilDestroyed() throws InterruptedException {
        var partition = new TopicPartition(TOPIC, 0);
        consumer.schedulePollTask(() -> {
            consumer.rebalance(List.of(partition));
            consumer.updateBeginningOffsets(Map.of(partition, 0L));
        });
        consumer.schedulePollTask(() -> {
            consumer.addRecord(record(partition, 0, ReloadMessage.reloadTree(THIS_POD, TREE).toJson()));
            consumer.addRecord(record(partition, 1, ReloadMessage.reloadTree(OTHER_POD, TREE).toJson()));
        });

        subscriber.start();

        assertThat(reloader.calls.poll(5, TimeUnit.SECONDS)).as("reload on assignment").isEqualTo(ALL);
        assertThat(reloader.calls.poll(5, TimeUnit.SECONDS)).as("the other pod's message").isEqualTo(TREE);

        subscriber.destroy();

        assertThat(consumer.closed()).isTrue();
        assertThat(reloader.calls).as("its own message skipped").isEmpty();
    }

    private static ConsumerRecord<String, String> record(TopicPartition partition, long offset, String value) {
        return new ConsumerRecord<>(partition.topic(), partition.partition(), offset, TREE, value);
    }
}
