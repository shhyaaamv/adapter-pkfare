package com.verteil.thirdparties.adapter.pkfare.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Verteil Entry-Gate connection settings under {@code verteil.*}. The username and password are not here: they come
 * from AWS Secrets Manager (see {@link SecretsManagerConfig}).
 */
@Data
@ConfigurationProperties("verteil")
public class VerteilProperties {

    /** Base URL of Entry-Gate and its OAuth2 token server, e.g. {@code https://qaapi.sandbox.verteil.com}. */
    private String host;
    /** Endpoint shared by every Entry-Gate service; the {@code Service} header picks the operation. */
    private String path = "/entrygate/rest/request";
    private String authPath = "/oauth2/token";
    private String authScope = "api";
    /** Timeout of the token call. */
    private int timeoutMillis = 30000;
    private Token token = new Token();
    private CircuitBreakerSettings circuitBreaker = new CircuitBreakerSettings();

    @Data
    public static class Token {
        /** Token lifetime when the auth response has no {@code expires_in}. */
        private long ttlSeconds = 1800;
        private long lockLeaseMillis = 10000;
        private long lockWaitMillis = 6000;
        private long lockPollMillis = 50;
    }

    /**
     * One breaker for every Entry-Gate call (token and services). I/O failures and every HTTP 4xx/5xx
     * count as failures. While open, calls fail at once without reaching Entry-Gate.
     */
    @Data
    public static class CircuitBreakerSettings {
        /** Open when at least this % of the calls in the window failed. */
        private float failureRateThreshold = 50;
        /** Open when at least this % of the calls in the window took longer than {@code slowCallDurationMillis}. */
        private float slowCallRateThreshold = 100;
        private long slowCallDurationMillis = 30000;
        /** Number of most recent calls the rates are computed over. */
        private int slidingWindowSize = 50;
        /** No decision before this many calls in the window. */
        private int minimumNumberOfCalls = 20;
        /** How long the breaker stays open before letting trial calls through. */
        private long waitDurationInOpenStateMillis = 30000;
        private int permittedNumberOfCallsInHalfOpenState = 5;
    }
}
