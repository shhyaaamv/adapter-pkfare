package com.verteil.thirdparties.adapter.pkfare.config.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * The record value on the reload topic. It uses the same envelope as vdc-configurator-lib's
 * {@code KafkaInternalPublisher}/{@code KafkaInternalSubscriber}, which other connectors (e.g. connector-c6-v4) use:
 * {@code {"nodeId":"<host>#<uuid>","event":"#<RELOAD_TREE>pkfareConfigTree"}}. A tool that drives those connectors'
 * reloads can therefore drive this adapter's too.
 *
 * @param nodeId the publishing pod ({@link NodeId}), so a pod can skip its own messages
 * @param event  {@code #<RELOAD_TREE><treeName>}, or a configuration change event: a JSON object with a
 *               {@code treeName}
 */
record ReloadMessage(String nodeId, String event) {

    static final String CMD_RELOAD_TREE = "#<RELOAD_TREE>";
    private static final String TREE_NAME = "treeName";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    static ReloadMessage reloadTree(String nodeId, String treeName) {
        return new ReloadMessage(nodeId, CMD_RELOAD_TREE + treeName);
    }

    /** @throws IllegalArgumentException when the value isn't such a JSON object */
    static ReloadMessage fromJson(String json) {
        try {
            return MAPPER.readValue(json, ReloadMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Not a reload message", e);
        }
    }

    String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Can't serialize " + this, e);
        }
    }

    /**
     * The tree to reload: the name after {@link #CMD_RELOAD_TREE}, or a change event's {@code treeName}. Empty for
     * any other event (e.g. vdc-configurator's {@code #<RELOAD_ARG_ENHANCERS>}: the adapter has no enhancers).
     */
    Optional<String> treeName() {
        return Optional.ofNullable(event)
                .flatMap(value -> value.startsWith(CMD_RELOAD_TREE)
                        ? Optional.of(value.substring(CMD_RELOAD_TREE.length()))
                        : changeEventTreeName(value))
                .filter(name -> !name.isBlank());
    }

    private static Optional<String> changeEventTreeName(String value) {
        try {
            return Optional.ofNullable(MAPPER.readTree(value))
                    .map(node -> node.get(TREE_NAME))
                    .filter(JsonNode::isTextual)
                    .map(JsonNode::asText);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
