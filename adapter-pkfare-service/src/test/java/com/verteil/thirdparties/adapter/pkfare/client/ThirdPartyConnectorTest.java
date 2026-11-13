package com.verteil.thirdparties.adapter.pkfare.client;

import com.verteil.thirdparties.adapter.pkfare.client.model.HttpCallResult;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThirdPartyConnectorTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final CircuitBreaker circuitBreaker = CircuitBreaker.of("entryGate", CircuitBreakerConfig.custom()
            .slidingWindowSize(2)
            .minimumNumberOfCalls(2)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .build());
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ThirdPartyConnector connector = new ThirdPartyConnector(httpClient, circuitBreaker, meterRegistry);

    @Test
    void returnsTheResponseAndRecordsTheCall() throws Exception {
        respondWith(200);

        assertThat(call().statusCode()).isEqualTo(200);
        assertThat(meterRegistry.get("supplier_transaction_duration").tags("service", "AirShopping", "status", "SUCCESS")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void opensOnConnectionFailuresAndStopsCallingEntryGate() throws Exception {
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class))).thenThrow(new IOException("refused"));

        assertThatThrownBy(this::call).isInstanceOf(PkfareAdapterException.class);
        assertThatThrownBy(this::call).isInstanceOf(PkfareAdapterException.class);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(this::call)
                .hasMessage("Unable to connect to Verteil. Please try again after sometime")
                .extracting("reason").isEqualTo("UNAVAILABLE");
        verify(httpClient, times(2)).execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
    }

    @Test
    void opensOnServerErrors() throws Exception {
        respondWith(503);

        assertThatThrownBy(this::call).hasMessage("HTTP 503 from Verteil");
        assertThatThrownBy(this::call).hasMessage("HTTP 503 from Verteil");

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void opensOnClientErrors() throws Exception {
        respondWith(400);

        assertThatThrownBy(this::call).hasMessage("HTTP 400 from Verteil").extracting("reason").isEqualTo("UNAVAILABLE");
        assertThatThrownBy(this::call).hasMessage("HTTP 400 from Verteil");

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void opensOnUnauthorizedAndKeepsItsReason() throws Exception {
        respondWith(401);

        assertThatThrownBy(this::call).extracting("reason").isEqualTo("UNAUTHORIZED");
        assertThatThrownBy(this::call).extracting("reason").isEqualTo("UNAUTHORIZED");

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(meterRegistry.get("supplier_transaction_duration").tags("status", "ERROR").timer().count()).isEqualTo(2);
    }

    private HttpCallResult call() {
        return connector.callSupplier(new HttpPost("https://api.example.com/entrygate/rest/request"), "PKFARE", "AirShopping");
    }

    @SuppressWarnings("unchecked")
    private void respondWith(int status) throws IOException {
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> invocation.getArgument(1, HttpClientResponseHandler.class)
                        .handleResponse(new BasicClassicHttpResponse(status)));
    }
}
