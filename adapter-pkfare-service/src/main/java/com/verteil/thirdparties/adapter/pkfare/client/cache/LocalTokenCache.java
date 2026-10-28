package com.verteil.thirdparties.adapter.pkfare.client.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.hash.Hashing;
import com.verteil.thirdparties.adapter.pkfare.client.EntryGateAuthClient;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.Configuration;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.ENTRY_GATE_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.INTERNAL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_TOKEN_TTL_SECONDS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.HOST;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.LOCK_POLL_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PROCESS_FAILURE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.REDIS_CACHE_KEY_SEPARATOR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.REDIS_CACHE_KEY_SESSION;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.TOKEN_EXPIRY_BUFFER_SECONDS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.TOKEN_LOCK_LEASE_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.TOKEN_LOCK_WAIT_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.TOKEN_TTL_SECONDS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.TOKEN_WAIT_TIMEOUT_MSG;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAVAILABLE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.USERNAME;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Single-flight, in-process cache for the Entry-Gate access token, backed by a cluster-wide Redis
 * entry shared across all pods. Keyed by a hash of Entry-Gate host + username, so several PKFARE vendor
 * codes mapped to different Entry-Gate accounts each get their own token.
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
    private final ObjectMapper objectMapper = JsonMappers.pkfare();

    private final LoadingCache<String, EntryGateSession> localCache;
    private final Map<String, Configuration> credentialsByKey = new ConcurrentHashMap<>();

    public LocalTokenCache(PkfareRedisCacheDao sessionCacheDao, EntryGateAuthClient authClient, Executor executor) {
        this.sessionCacheDao = sessionCacheDao;
        this.authClient = authClient;
        this.localCache = Caffeine.newBuilder()
                .expireAfter(new SessionExpiry())
                .executor(executor)
                .build(this::fetchAuthoritativeSession);
    }

    public String getAccessToken(Configuration providerApiConfig) {
        String authKey = authKeyOf(providerApiConfig.getString(HOST) + "|" + providerApiConfig.getString(USERNAME));
        credentialsByKey.put(authKey, providerApiConfig);
        return localCache.get(authKey).accessToken();
    }

    /**
     * Drops the token for this account locally, e.g. after Entry-Gate rejected it with HTTP 401.
     * The shared Redis copy is left alone; it is re-validated on the next load.
     */
    public void invalidate(Configuration providerApiConfig) {
        localCache.invalidate(authKeyOf(providerApiConfig.getString(HOST) + "|" + providerApiConfig.getString(USERNAME)));
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
        var providerConfigs = credentialsByKey.get(authKey);
        if (isNull(providerConfigs)) {
            throw new PkfareAdapterException(INTERNAL_ERROR.getCode(), "Token cache loaded before first getAccessToken() call for key " + authKey, PROCESS_FAILURE_REASON_CODE);
        }
        long now = nowEpochSeconds();
        EntryGateSession shared = readSessionFromCache(authKey);
        if (isValid(shared, now)) {
            log.debug("Found valid shared Entry-Gate session in Redis for key [{}], expiresAt={}", authKey, shared.expiresAt());
            return shared;
        }
        return fetchSessionUnderClusterLock(authKey, providerConfigs);
    }

    private EntryGateSession fetchSessionUnderClusterLock(String authKey, Configuration configuration) {
        long lockLeaseMillis = configuration.getLong(TOKEN_LOCK_LEASE_MILLIS, 10_000L);
        long lockWaitMillis = configuration.getLong(TOKEN_LOCK_WAIT_MILLIS, 6_000L);
        long lockPollMillis = configuration.getLong(LOCK_POLL_MILLIS, 50L);

        String lockValue = UUID.randomUUID().toString();
        if (!sessionCacheDao.acquireLock(authKey, lockValue, lockLeaseMillis)) {
            log.info("Cluster lock contention for key [{}]. Awaiting token publication from another pod.", authKey);
            return awaitSessionFromCache(authKey, lockWaitMillis, lockPollMillis);
        }
        try {
            // Another pod may have published a token between our read and the lock being granted.
            EntryGateSession shared = readSessionFromCache(authKey);
            if (isValid(shared, nowEpochSeconds())) {
                return shared;
            }
            return login(authKey, configuration);
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
        throw new PkfareAdapterException(ENTRY_GATE_ERROR.getCode(), TOKEN_WAIT_TIMEOUT_MSG, UNAVAILABLE_REASON_CODE);
    }

    private EntryGateSession login(String authKey, Configuration configuration) {
        log.info("Requesting a new Entry-Gate access token for key [{}]", authKey);
        var response = authClient.login(configuration);
        long ttlSeconds = Optional.ofNullable(response.expiresIn())
                .filter(expiresIn -> expiresIn > 0)
                .orElseGet(() -> configuration.getLong(TOKEN_TTL_SECONDS, DEFAULT_TOKEN_TTL_SECONDS));
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
            return Optional.ofNullable(session).filter(found -> nonNull(found.accessToken())).orElse(null);
        } catch (JsonProcessingException e) {
            log.warn("Unable to deserialize shared Entry-Gate session from Redis for key [{}]: {}", authKey, e.getMessage());
            return null;
        }
    }

    private static boolean isValid(EntryGateSession session, long now) {
        return Optional.ofNullable(session).map(found -> found.isValid(now, TOKEN_EXPIRY_BUFFER_SECONDS)).orElse(false);
    }

    private static String authKeyOf(String accountId) {
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
            throw new PkfareAdapterException(ENTRY_GATE_ERROR.getCode(), TOKEN_WAIT_TIMEOUT_MSG, UNAVAILABLE_REASON_CODE);
        }
    }
}
