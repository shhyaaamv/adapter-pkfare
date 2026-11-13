package com.verteil.thirdparties.adapter.pkfare.client;

import com.verteil.thirdparties.adapter.pkfare.client.model.HttpCallResult;
import com.verteil.thirdparties.adapter.pkfare.common.exception.EntryGateHttpException;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.IOException;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VERTEIL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.CONNECTION_ERROR_MSG;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_RESPONSE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SUCCESS_RESPONSE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SUPPLIER_DURATION_METRIC;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAUTHORIZED_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAVAILABLE_REASON_CODE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.hc.core5.util.Timeout.ofMilliseconds;

/**
 * Executes Entry-Gate HTTP calls through the Entry-Gate circuit breaker and records
 * {@code supplier_transaction_duration} per Entry-Gate service and outcome.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThirdPartyConnector {

    @Value("${adapter.http.poolTimeoutMillis:1000}")
    private Long poolTimeoutMillis;
    private final HttpClient httpClient;
    private final CircuitBreaker entryGateCircuitBreaker;
    private final MeterRegistry meterRegistry;

    public HttpCallResult callSupplier(HttpUriRequestBase httpRequest, String owner, String apiService) {
        log.debug("Calling Entry-Gate : '{}-{}', url : {}", owner, apiService, httpRequest.getRequestUri());
        Timer.Sample sample = Timer.start(meterRegistry);
        String status = ERROR_RESPONSE;
        try {
            var result = callThroughCircuitBreaker(httpRequest);
            status = SUCCESS_RESPONSE;
            return result;
        } finally {
            sample.stop(meterRegistry.timer(SUPPLIER_DURATION_METRIC, "service", apiService, "status", status));
        }
    }

    /** I/O failures and every HTTP 4xx/5xx count against the breaker. A 401 keeps the {@code UNAUTHORIZED} reason. */
    private HttpCallResult callThroughCircuitBreaker(HttpUriRequestBase httpRequest) {
        try {
            return entryGateCircuitBreaker.executeSupplier(() -> {
                var result = invokeAndGetResponse(httpRequest);
                if (result.statusCode() >= HttpStatus.BAD_REQUEST.value()) {
                    throw httpError(result);
                }
                return result;
            });
        } catch (CallNotPermittedException e) {
            log.error("Entry-Gate circuit breaker is {}, {} not called", entryGateCircuitBreaker.getState(), httpRequest.getRequestUri());
            throw new PkfareAdapterException(VERTEIL_ERROR.getCode(), CONNECTION_ERROR_MSG, e, UNAVAILABLE_REASON_CODE);
        }
    }

    private HttpCallResult invokeAndGetResponse(HttpUriRequestBase httpRequest) {
        var stopWatch = new StopWatch();
        stopWatch.start();
        try {
            return httpClient.execute(httpRequest, response -> {
                int statusCode = response.getCode();
                var entity = response.getEntity();
                String body = nonNull(entity) ? EntityUtils.toString(entity, UTF_8) : EMPTY;
                stopWatch.stop();
                return new HttpCallResult(statusCode, body, stopWatch.getTotalTimeMillis());
            });
        } catch (IOException e) {
            stopWatch.stop();
            log.error("IOException while connecting to Entry-Gate, url={}", httpRequest.getRequestUri(), e);
            throw new PkfareAdapterException(VERTEIL_ERROR.getCode(), CONNECTION_ERROR_MSG, e, UNAVAILABLE_REASON_CODE);
        }
    }

    public RequestConfig buildRequestConfig(long timeout) {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(ofMilliseconds(poolTimeoutMillis))
                .setResponseTimeout(ofMilliseconds(timeout))
                .build();
    }

    private static EntryGateHttpException httpError(HttpCallResult result) {
        String reason = result.statusCode() == HttpStatus.UNAUTHORIZED.value() ? UNAUTHORIZED_REASON_CODE : UNAVAILABLE_REASON_CODE;
        return new EntryGateHttpException(result.statusCode(), result.body(), reason);
    }
}
