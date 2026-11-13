package com.verteil.thirdparties.adapter.pkfare.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Shared Redis connection (Entry-Gate token cache across pods), from {@code ordermanagement.redis.*}. Redis is
 * optional: when it cannot be reached at startup the connection bean is null and the token cache works per pod.
 * Once connected, Lettuce reconnects by itself.
 */
@Slf4j
@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    public ClientResources redisClientResources() {
        return DefaultClientResources.create();
    }

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient(ClientResources redisClientResources,
                                   @Value("${ordermanagement.redis.host}") String host,
                                   @Value("${ordermanagement.redis.port}") int port,
                                   @Value("${ordermanagement.redis.password:}") String password,
                                   @Value("${ordermanagement.redis.database:0}") int database,
                                   @Value("${ordermanagement.redis.ssl.enabled:false}") boolean ssl,
                                   @Value("${ordermanagement.redis.timeout.seconds:5}") long timeoutSeconds) {
        RedisURI.Builder uri = RedisURI.Builder.redis(host, port)
                .withDatabase(database)
                .withSsl(ssl)
                .withTimeout(Duration.ofSeconds(timeoutSeconds));
        if (isNotBlank(password)) {
            uri.withPassword(password.toCharArray());
        }
        log.info("Redis: host={}, port={}, database={}, ssl={}", host, port, database, ssl);
        return RedisClient.create(redisClientResources, uri.build());
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, String> redisConnection(RedisClient redisClient) {
        try {
            return redisClient.connect();
        } catch (RuntimeException e) {
            log.warn("Redis is not reachable, the Entry-Gate token cache works per pod: {}", e.getMessage());
            return null;
        }
    }
}
