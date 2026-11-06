package com.verteil.thirdparties.adapter.pkfare.config.sync;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class KafkaReloadPublisherTest {

    private static final String TOPIC = "adapter-pkfare-configurator-internal";
    private static final String NODE = "pod-1#uuid";

    @Test
    void publishesOneReloadTreeCommandPerTreeKeyedByTreeName() {
        var producer = new MockProducer<>(true, new StringSerializer(), new StringSerializer());
        var publisher = new KafkaReloadPublisher(producer, TOPIC, NODE, Duration.ofSeconds(1));

        publisher.broadcastReload(List.of("pkfareConfigTree", "otherTree"));

        assertThat(producer.history()).extracting(ProducerRecord::topic, ProducerRecord::key, ProducerRecord::value)
                .containsExactly(
                        tuple(TOPIC, "pkfareConfigTree",
                                "{\"nodeId\":\"pod-1#uuid\",\"event\":\"#<RELOAD_TREE>pkfareConfigTree\"}"),
                        tuple(TOPIC, "otherTree",
                                "{\"nodeId\":\"pod-1#uuid\",\"event\":\"#<RELOAD_TREE>otherTree\"}"));
    }

    @Test
    void failsWhenTheBrokerRejectsTheMessage() {
        var producer = new MockProducer<>(false, new StringSerializer(), new StringSerializer());
        var publisher = new KafkaReloadPublisher(producer, TOPIC, NODE, Duration.ofSeconds(5));
        Thread.ofVirtual().start(() -> {
            while (!producer.errorNext(new IllegalStateException("broker down"))) {
                Thread.onSpinWait();
            }
        });

        assertThatThrownBy(() -> publisher.broadcastReload(List.of("pkfareConfigTree")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TOPIC)
                .hasRootCauseMessage("broker down");
    }

    @Test
    void failsWhenTheMessageIsNotAcknowledgedInTime() {
        var producer = new MockProducer<>(false, new StringSerializer(), new StringSerializer());
        var publisher = new KafkaReloadPublisher(producer, TOPIC, NODE, Duration.ofMillis(50));

        assertThatThrownBy(() -> publisher.broadcastReload(List.of("pkfareConfigTree")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TOPIC);
    }

    @Test
    void closesTheProducer() {
        var producer = new MockProducer<>(true, new StringSerializer(), new StringSerializer());

        new KafkaReloadPublisher(producer, TOPIC, NODE, Duration.ofSeconds(1)).destroy();

        assertThat(producer.closed()).isTrue();
    }
}
