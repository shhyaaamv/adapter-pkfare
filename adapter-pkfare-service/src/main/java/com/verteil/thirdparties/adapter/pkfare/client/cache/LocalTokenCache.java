package com.verteil.thirdparties.adapter.pkfare.client.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.hash.Hashing;
import com.verteil.thirdparties.adapter.pkfare.client.EntryGateAuthClient;
import com.verteil.thirdparties.adapter.pkfare.client.EntryGateCredentialsProvider;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import com.verteil.thirdparties.adapter.pkfare.config.VerteilProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VERTEIL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.REDIS_CACHE_KEY_SEPARATOR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.REDIS_CACHE_KEY_SESSION;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.TOKEN_EXPIRY_BUFFER_SECONDS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.TOKEN_WAIT_TIMEOUT_MSG;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAVAILABLE_REASON_CODE;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Single-flight, in-process cache for the Entry-Gate access token, backed by a cluster-wide Redis
 * entry shared across all pods. Keyed by a hash of Entry-Gate host + username.
 * <p>
 * Caffeine's {@code LoadingCache.get()} guarantees only one thread per pod enters the loader for a key;
 * the Redis lock in {@link PkfareRedisCacheDao} then ensures only one pod in the fleet calls the auth
 * server at a time. A local entry expires {@code TOKEN_EXPIRY_BUFFER_SECONDS} before the token itself, so
 * a token that is about to lapse is never handed out.
 */
@Slf4j
@Component
public class LocalTokenCache {

    private final PkfareRedisCacheDao sessionCacheDao;
    private final EntryGateAuthClient authClient;
    private final VerteilProperties verteilProperties;
    private final EntryGateCredentialsProvider credentialsProvider;
    private final ObjectMapper objectMapper = JsonMappers.pkfare();

    private final LoadingCache<String, EntryGateSession> localCache;

    public LocalTokenCache(PkfareRedisCacheDao sessionCacheDao, EntryGateAuthClient authClient, Executor executor,
                           VerteilProperties verteilProperties, EntryGateCredentialsProvider credentialsProvider) {
        this.sessionCacheDao = sessionCacheDao;
        this.authClient = authClient;
        this.verteilProperties = verteilProperties;
        this.credentialsProvider = credentialsProvider;
        this.localCache = Caffeine.newBuilder()
                .expireAfter(new SessionExpiry())
                .executor(executor)
                .build(this::fetchAuthoritativeSession);
    }

    public String getAccessToken() {
        return localCache.get(authKey()).accessToken();
    }

    /**
     * Drops the token locally, e.g. after Entry-Gate rejected it with HTTP 401.
     * The shared Redis copy is left alone; it is re-validated on the next load.
     */
    public void invalidate() {
        localCache.invalidate(authKey());
    }

    /**
     * Local entry lives until the token can no longer be served (expiry minus buffer). Reads never extend it.
     */
    private static final class SessionExpiry implements Expiry<String, EntryGateSession> {

        @Override
        public long expireAfterCreate(String key, EntryGateSession session, long currentTime) {
            return nanosUntilUnservable(session);
        }

        @Override
        public long expireAfterUpdate(String key, EntryGateSession session, long currentTime, long currentDuration) {
            return nanosUntilUnservable(session);
        }

        @Override
        public long expireAfterRead(String key, EntryGateSession session, long currentTime, long currentDuration) {
            return currentDuration;
        }

        private long nanosUntilUnservable(EntryGateSession session) {
            long secondsLeft = session.expiresAt() - Instant.now().getEpochSecond() - TOKEN_EXPIRY_BUFFER_SECONDS;
            return TimeUnit.SECONDS.toNanos(Math.max(0, secondsLeft));
        }
    }

    private EntryGateSession fetchAuthoritativeSession(String authKey) {
        long now = nowEpochSeconds();
        EntryGateSession shared = readSessionFromCache(authKey);
        if (isValid(shared, now)) {
            log.debug("Found valid shared Entry-Gate session in Redis for key [{}], expiresAt={}", authKey, shared.expiresAt());
            return shared;
        }
        return fetchSessionUnderClusterLock(authKey);
    }

    private EntryGateSession fetchSessionUnderClusterLock(String authKey) {
        String lockValue = UUID.randomUUID().toString();
        if (!sessionCacheDao.acquireLock(authKey, lockValue, verteilProperties.getToken().getLockLeaseMillis())) {
            log.info("Cluster lock contention for key [{}]. Awaiting token publication from another pod.", authKey);
            return awaitSessionFromCache(authKey, verteilProperties.getToken().getLockWaitMillis(), verteilProperties.getToken().getLockPollMillis());
        }
        try {
            // Another pod may have published a token between our read and the lock being granted.
            EntryGateSession shared = readSessionFromCache(authKey);
            if (isValid(shared, nowEpochSeconds())) {
                return shared;
            }
            return login(authKey);
        } finally {
            sessionCacheDao.releaseLock(authKey, lockValue);
        }
    }

    private EntryGateSession awaitSessionFromCache(String authKey, long lockWaitMillis, long lockPollMillis) {
        long deadline = System.currentTimeMillis() + lockWaitMillis;
        while (System.currentTimeMillis() < deadline) {
            sleepQuietly(lockPollMillis);
            EntryGateSession shared = readSessionFromCache(authKey);
            if (isValid(shared, nowEpochSeconds())) {
                log.info("Acquired shared Entry-Gate session published by peer pod for key [{}]", authKey);
                return shared;
            }
        }
        log.warn("Timed out waiting for shared Entry-Gate session for key [{}] within {}ms", authKey, lockWaitMillis);
        throw new PkfareAdapterException(VERTEIL_ERROR.getCode(), TOKEN_WAIT_TIMEOUT_MSG, UNAVAILABLE_REASON_CODE);
    }

    private EntryGateSession login(String authKey) {
        log.info("Requesting a new Entry-Gate access token for key [{}]", authKey);
        var response = authClient.login();
        long ttlSeconds = Optional.ofNullable(response.expiresIn())
                .filter(expiresIn -> expiresIn > 0)
                .orElseGet(verteilProperties.getToken()::getTtlSeconds);
        EntryGateSession session = EntryGateSession.issuedAt(response.accessToken(), ttlSeconds, nowEpochSeconds());
        saveSessionToCache(authKey, session);
        log.info("Entry-Gate token cached for key [{}], expiresAt={}", authKey, Instant.ofEpochSecond(session.expiresAt()));
        return session;
    }

    private void saveSessionToCache(String authKey, EntryGateSession session) {
        try {
            long ttlSeconds = session.expiresAt() - nowEpochSeconds() - TOKEN_EXPIRY_BUFFER_SECONDS;
            sessionCacheDao.save(authKey, objectMapper.writeValueAsString(session), ttlSeconds);
        } catch (JsonProcessingException e) {
            log.warn("Unable to serialize Entry-Gate session for key [{}]: {}", authKey, e.getMessage());
        }
    }

    private EntryGateSession readSessionFromCache(String authKey) {
        String json = sessionCacheDao.find(authKey);
        if (isEmpty(json)) return null;
        try {
            EntryGateSession session = objectMapper.readValue(json, EntryGateSession.class);
            return Optional.ofNullable(session)
                    .filter(found -> nonNull(found.accessToken()))
                    .orElse(null);
        } catch (JsonProcessingException e) {
            log.warn("Unable to deserialize shared Entry-Gate session from Redis for key [{}]: {}", authKey, e.getMessage());
            return null;
        }
    }

    private static boolean isValid(EntryGateSession session, long now) {
        return Optional.ofNullable(session)
                .map(found -> found.isValid(now, TOKEN_EXPIRY_BUFFER_SECONDS))
                .orElse(false);
    }

    /** Follows the current username, so a rotated secret for another account never reuses the old account's token. */
    private String authKey() {
        String accountId = verteilProperties.getHost() + "|" + credentialsProvider.get().username();
        return REDIS_CACHE_KEY_SESSION + REDIS_CACHE_KEY_SEPARATOR + Hashing.sha256().hashString(accountId, StandardCharsets.UTF_8);
    }

    private static long nowEpochSeconds() {
        return Instant.now().getEpochSecond();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new PkfareAdapterException(VERTEIL_ERROR.getCode(), TOKEN_WAIT_TIMEOUT_MSG, UNAVAILABLE_REASON_CODE);
        }
    }
}
