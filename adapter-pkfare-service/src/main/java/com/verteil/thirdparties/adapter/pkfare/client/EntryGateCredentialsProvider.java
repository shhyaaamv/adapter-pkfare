package com.verteil.thirdparties.adapter.pkfare.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.verteil.thirdparties.adapter.pkfare.client.model.EntryGateCredentials;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.time.Duration;
import java.time.Instant;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.CREDENTIALS_REFRESH_INTERVAL;
import static org.apache.commons.lang3.StringUtils.isAnyBlank;

/**
 * Entry-Gate OAuth2 client credentials from the AWS secret {@code aws.secretManager.name}, a JSON object with
 * {@code username} and {@code password}. Read at startup, so a missing or malformed secret stops the pod from starting.
 * <p>
 * A rotated secret is picked up without a restart: when Entry-Gate rejects the credentials, {@link EntryGateAuthClient}
 * calls {@link #refresh()}, which re-reads the secret at most once per {@code CREDENTIALS_REFRESH_INTERVAL}.
 */
@Slf4j
@Component
public class EntryGateCredentialsProvider {

    private final SecretsManagerClient secretsManagerClient;
    private final String secretName;
    private volatile EntryGateCredentials credentials;
    private Instant lastRefresh = Instant.MIN;

    public EntryGateCredentialsProvider(SecretsManagerClient secretsManagerClient,
                                        @Value("${aws.secretManager.name}") String secretName) {
        this.secretsManagerClient = secretsManagerClient;
        this.secretName = secretName;
        this.credentials = load();
        log.info("Loaded Entry-Gate credentials of user [{}] from AWS secret {}", credentials.username(), secretName);
    }

    public EntryGateCredentials get() {
        return credentials;
    }

    /**
     * Re-reads the secret, unless it was re-read less than {@code CREDENTIALS_REFRESH_INTERVAL} ago.
     *
     * @return {@code true} when the secret now holds different credentials, i.e. a retry is worth it
     */
    public synchronized boolean refresh() {
        Instant now = Instant.now();
        if (Duration.between(lastRefresh, now).compareTo(CREDENTIALS_REFRESH_INTERVAL) < 0) {
            return false;
        }
        lastRefresh = now;
        try {
            EntryGateCredentials fresh = load();
            boolean changed = !fresh.equals(credentials);
            credentials = fresh;
            log.info("Re-read AWS secret {}: credentials {}", secretName, changed ? "changed, user [" + fresh.username() + "]" : "unchanged");
            return changed;
        } catch (RuntimeException e) {
            log.error("Unable to re-read AWS secret {}, keeping the current credentials", secretName, e);
            return false;
        }
    }

    private EntryGateCredentials load() {
        String secret = secretsManagerClient.getSecretValue(GetSecretValueRequest.builder().secretId(secretName).build())
                .secretString();
        EntryGateCredentials loaded = read(secret);
        if (loaded == null || isAnyBlank(loaded.username(), loaded.password())) {
            throw new IllegalStateException("AWS secret " + secretName + " has no username or password");
        }
        return loaded;
    }

    private EntryGateCredentials read(String secret) {
        try {
            return JsonMappers.pkfare().readValue(secret, EntryGateCredentials.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("AWS secret " + secretName + " is not a JSON object with username and password", e);
        }
    }
}
