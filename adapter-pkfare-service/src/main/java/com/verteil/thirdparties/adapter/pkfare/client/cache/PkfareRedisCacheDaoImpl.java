package com.verteil.thirdparties.adapter.pkfare.client.cache;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.REDIS_CACHE_KEY_LOCK;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.REDIS_CACHE_KEY_PREFIX;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.REDIS_CACHE_KEY_SEPARATOR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.REDIS_LOCK_RELEASE_SCRIPT;
import static java.util.Objects.isNull;

/**
 * Backed by the shared connector Redis connection (from vdc-thirdparties-common-helper's
 * ConnectorRedisConfig, if present in this module) so this cache reuses one connection and one
 * {@code redis.*} config block rather than opening its own.
 */
@Component
@Slf4j
public class PkfareRedisCacheDaoImpl implements PkfareRedisCacheDao {

    private static final String LOCK_ACQUIRED = "OK";

    private final ObjectProvider<StatefulRedisConnection<String, String>> redisConnectionProvider;

    public PkfareRedisCacheDaoImpl(ObjectProvider<StatefulRedisConnection<String, String>> redisConnectionProvider) {
        this.redisConnectionProvider = redisConnectionProvider;
    }

    @Override
    public String find(String key) {
        RedisCommands<String, String> commands = redisCommands();
        if (isNull(commands)) {
            return null;
        }
        try {
            return commands.get(cacheKey(key));
        } catch (RuntimeException e) {
            log.warn("Unable to read the shared payload for key '{}' : {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public void save(String key, String payload, long ttlSeconds) {
        RedisCommands<String, String> commands = redisCommands();
        if (isNull(commands) || ttlSeconds <= 0) {
            return;
        }
        try {
            commands.setex(cacheKey(key), ttlSeconds, payload);
        } catch (RuntimeException e) {
            log.warn("Unable to cache the shared payload for key '{}' : {}", key, e.getMessage());
        }
    }

    /**
     * {@code SET key value NX PX lease} is a single atomic Redis step, so exactly one pod is granted the
     * lock. The lease guarantees the lock is released even when the holder dies mid-call.
     */
    @Override
    public boolean acquireLock(String key, String lockValue, long lockLeaseMillis) {
        RedisCommands<String, String> commands = redisCommands();
        if (isNull(commands)) {
            return true; // Redis unavailable — proceed alone rather than block
        }
        try {
            String result = commands.set(cacheKey(REDIS_CACHE_KEY_LOCK + REDIS_CACHE_KEY_SEPARATOR + key), lockValue,
                    SetArgs.Builder.nx().px(lockLeaseMillis));
            return LOCK_ACQUIRED.equals(result);
        } catch (RuntimeException e) {
            log.warn("Unable to acquire lock for key '{}', proceeding without it : {}",
                    key, e.getMessage());
            return true;
        }
    }

    @Override
    public void releaseLock(String key, String lockValue) {
        RedisCommands<String, String> commands = redisCommands();
        if (isNull(commands)) {
            return;
        }
        try {
            commands.eval(REDIS_LOCK_RELEASE_SCRIPT, ScriptOutputType.INTEGER,
                    new String[]{cacheKey(REDIS_CACHE_KEY_LOCK + REDIS_CACHE_KEY_SEPARATOR + key)}, lockValue);
        } catch (RuntimeException e) {
            log.warn("Unable to release lock for key '{}', it will expire on its own : {}",
                    key, e.getMessage());
        }
    }

    private String cacheKey(String cacheKey) {
        return REDIS_CACHE_KEY_PREFIX + REDIS_CACHE_KEY_SEPARATOR + cacheKey;
    }

    /**
     * @return synchronous commands against the shared connection, or {@code null} when Redis is not usable.
     * The connection is owned elsewhere, so it is never opened or closed here.
     */
    private RedisCommands<String, String> redisCommands() {
        try {
            StatefulRedisConnection<String, String> connection = redisConnectionProvider.getIfAvailable();
            if (isNull(connection) || !connection.isOpen()) {
                return null;
            }
            return connection.sync();
        } catch (RuntimeException e) {
            log.warn("Redis is unavailable for the shared cache : {}", e.getMessage());
            return null;
        }
    }
}