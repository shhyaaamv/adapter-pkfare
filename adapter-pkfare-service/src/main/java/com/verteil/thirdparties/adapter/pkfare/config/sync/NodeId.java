package com.verteil.thirdparties.adapter.pkfare.config.sync;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;

/**
 * This JVM's id on the reload topic: {@code <hostname>#<uuid>} (the pod name in Kubernetes), or just the UUID when
 * the hostname is unknown. The UUID keeps it unique across restarts of the same pod. It is vdc-configurator-lib's
 * {@code NodeId} format.
 */
@Slf4j
final class NodeId {

    static final String VALUE = generate();

    private NodeId() {
    }

    private static String generate() {
        String uuid = UUID.randomUUID().toString();
        String value = hostName().map(host -> host + "#" + uuid).orElse(uuid);
        log.info("Configuration reload node id: {}", value);
        return value;
    }

    private static Optional<String> hostName() {
        try {
            return Optional.of(InetAddress.getLocalHost())
                    .filter(address -> !address.isLoopbackAddress())
                    .map(InetAddress::getHostName);
        } catch (UnknownHostException e) {
            log.warn("Hostname unknown; the configuration reload node id is a UUID only", e);
            return Optional.empty();
        }
    }
}
