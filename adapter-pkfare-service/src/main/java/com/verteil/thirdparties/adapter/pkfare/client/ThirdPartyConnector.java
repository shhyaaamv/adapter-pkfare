package com.verteil.thirdparties.adapter.pkfare.client;

import com.verteil.thirdparties.adapter.pkfare.client.model.HttpCallResult;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.common.metric.MetricSupplierContext;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.Configuration;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.util.function.Supplier;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.ENTRY_GATE_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.CONNECTION_ERROR_MSG;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAUTHORIZED_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAVAILABLE_REASON_CODE;
import static com.verteil.thirdparties.connector.common.resilience4j.Resilience4JUtil.RESILIENCE4J_CONFIG_SUFFIX;
import static com.verteil.thirdparties.connector.common.resilience4j.Resilience4JUtil.RESILIENCE_LOGGER;
import static com.verteil.thirdparties.connector.common.resilience4j.Resilience4JUtil.getBulkhead;
import static com.verteil.thirdparties.connector.common.resilience4j.Resilience4JUtil.getCircuitBreaker;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.hc.core5.util.Timeout.ofMilliseconds;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThirdPartyConnector {

    @Value("${connector.http.poolTimeoutMillis:1000}")
    private Long poolTimeoutMillis;
    private final HttpClient httpClient;

    @MetricSupplierContext
    public HttpCallResult callSupplier(HttpUriRequestBase httpRequest, Configuration providerConfiguration, String owner, String apiService) {
        var url = httpRequest.getRequestUri();
        log.debug("Calling Entry-Gate : '{}-{}', url : {}", owner, apiService, url);

        var resilienceConfigSuffix = providerConfiguration.getString(RESILIENCE4J_CONFIG_SUFFIX, EMPTY);
        Bulkhead bulkhead = null;
        CircuitBreaker circuitBreaker = null;

        if (isNotEmpty(resilienceConfigSuffix)) {
            bulkhead = getBulkhead(owner, resilienceConfigSuffix, providerConfiguration, null);
            circuitBreaker = getCircuitBreaker(owner, resilienceConfigSuffix, providerConfiguration, null);
        }

        Supplier<HttpCallResult> httpCall = () -> {
            var result = invokeAndGetResponse(httpRequest);
            validateHttpStatusCode(result.statusCode());
            return result;
        };

        try {
            if (nonNull(bulkhead) && nonNull(circuitBreaker)) {
                Supplier<HttpCallResult> withCb = CircuitBreaker.decorateSupplier(circuitBreaker, httpCall);
                Supplier<HttpCallResult> withCbAndBulkhead = Bulkhead.decorateSupplier(bulkhead, withCb);
                return withCbAndBulkhead.get();
            }
            return httpCall.get();
        } catch (BulkheadFullException | CallNotPermittedException exception) {
            log.error("CircuitBreakerException while connecting to Entry-Gate", exception);
            RESILIENCE_LOGGER.error("Resilience4j error {}", exception.getMessage());
            throw new PkfareAdapterException(ENTRY_GATE_ERROR.getCode(), CONNECTION_ERROR_MSG, UNAVAILABLE_REASON_CODE);
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
            throw new PkfareAdapterException(ENTRY_GATE_ERROR.getCode(), CONNECTION_ERROR_MSG, e, UNAVAILABLE_REASON_CODE);
        }
    }

    public RequestConfig buildRequestConfig(long timeout) {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(ofMilliseconds(poolTimeoutMillis))
                .setResponseTimeout(ofMilliseconds(timeout))
                .build();
    }

    private void validateHttpStatusCode(int statusCode) {
        if (statusCode == HttpStatus.UNAUTHORIZED.value()) {
            throw new PkfareAdapterException(ENTRY_GATE_ERROR.getCode(), "HTTP " + statusCode + " from Entry-Gate", UNAUTHORIZED_REASON_CODE);
        } else if (statusCode >= 400) {
            throw new PkfareAdapterException(ENTRY_GATE_ERROR.getCode(), "HTTP " + statusCode + " from Entry-Gate", UNAVAILABLE_REASON_CODE);
        }
    }
}
