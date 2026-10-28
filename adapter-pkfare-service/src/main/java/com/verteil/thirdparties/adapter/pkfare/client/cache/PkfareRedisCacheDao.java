package com.verteil.thirdparties.adapter.pkfare.client.cache;

/**
 * Cluster-wide cache for data shared by every adapter pod.
 * <p>
 * Without it every pod would request its own Entry-Gate token, multiplying calls against the auth server.
 */
public interface PkfareRedisCacheDao {

    /**
     * @return the cached payload for this key, or {@code null} when nothing usable is cached.
     */
    String find(String key);

    /**
     * Stores the payload for this key. Ignored when {@code ttlSeconds} is not positive.
     */
    void save(String key, String payload, long ttlSeconds);

    /**
     * Attempts to become the single pod allowed to call Entry-Gate's auth endpoint for this key.
     *
     * @param lockValue       caller-owned value, required later to release the lock safely.
     * @param lockLeaseMillis how long the lock survives without being released, so a pod that dies
     *                        mid-call never blocks the rest of the cluster.
     * @return {@code true} when the caller may proceed, {@code false} when another pod already is.
     * Also {@code true} when the cache is unavailable, so the caller can proceed alone.
     */
    boolean acquireLock(String key, String lockValue, long lockLeaseMillis);

    /**
     * Releases the lock, but only when it is still held by {@code lockValue}.
     */
    void releaseLock(String key, String lockValue);
}
