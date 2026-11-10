package com.verteil.thirdparties.adapter.pkfare.config.sync;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReloadMessageTest {

    /** Exactly what vdc-configurator-lib's {@code KafkaInternalPublisher.publishReloadTreeCmd} writes. */
    private static final String LIBRARY_RELOAD_TREE = "{\"nodeId\":\"pod-1#9b2d\",\"event\":\"#<RELOAD_TREE>pkfareConfigTree\"}";

    @Test
    void writesTheLibraryEnvelope() {
        assertThat(ReloadMessage.reloadTree("pod-1#9b2d", "pkfareConfigTree").toJson()).isEqualTo(LIBRARY_RELOAD_TREE);
    }

    @Test
    void readsTheLibraryReloadTreeCommand() {
        ReloadMessage message = ReloadMessage.fromJson(LIBRARY_RELOAD_TREE);

        assertThat(message.nodeId()).isEqualTo("pod-1#9b2d");
        assertThat(message.treeName()).contains("pkfareConfigTree");
    }

    @Test
    void readsTheTreeOfAChangeEvent() {
        // the library's publish(ConfigurationChangeEvent): the event is the change event's JSON, escaped
        String changeEvent = "{\"nodeId\":\"pod-2#77aa\",\"event\":\"{\\\"id\\\":\\\"12\\\",\\\"group\\\":\\\"PKFARE_API\\\","
                + "\\\"name\\\":\\\"HOST\\\",\\\"treeName\\\":\\\"pkfareConfigTree\\\",\\\"action\\\":\\\"UPDATE\\\"}\"}";

        assertThat(ReloadMessage.fromJson(changeEvent).treeName()).contains("pkfareConfigTree");
    }

    @Test
    void hasNoTreeForOtherEvents() {
        assertThat(new ReloadMessage("pod-1", "#<RELOAD_ARG_ENHANCERS>").treeName()).isEmpty();
        assertThat(new ReloadMessage("pod-1", "#<RELOAD_TREE>").treeName()).isEmpty();
        assertThat(new ReloadMessage("pod-1", "{\"treeName\":42}").treeName()).isEmpty();
        assertThat(new ReloadMessage("pod-1", null).treeName()).isEmpty();
    }

    @Test
    void ignoresUnknownEnvelopeFields() {
        assertThat(ReloadMessage.fromJson("{\"nodeId\":\"pod-1\",\"event\":\"#<RELOAD_TREE>t\",\"extra\":1}").treeName())
                .contains("t");
    }

    @Test
    void rejectsAValueThatIsNotJson() {
        assertThatThrownBy(() -> ReloadMessage.fromJson("reload please"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
