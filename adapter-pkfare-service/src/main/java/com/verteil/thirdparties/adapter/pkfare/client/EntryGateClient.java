package com.verteil.thirdparties.adapter.pkfare.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verteil.schemas.ndc16_1.AirShoppingRS;
import com.verteil.thirdparties.adapter.pkfare.client.cache.LocalTokenCache;
import com.verteil.thirdparties.adapter.pkfare.client.interceptor.LoggerHelper;
import com.verteil.thirdparties.adapter.pkfare.client.model.HttpCallResult;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.Configuration;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.INTERNAL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VALIDATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_TIMEOUT_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ENTRY_GATE_OFFICE_ID_HEADER;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ENTRY_GATE_SERVICE_HEADER;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ENTRY_GATE_TRANSACTION_ID_HEADER;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_SERVICE_NOT_IMPLEMENTED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.OAUTH_BEARER_HEADER;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.OFFICE_ID;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE_API;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AIRSHOPPING;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.STANDARD_ERROR_MESSAGE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAUTHORIZED_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNIMPLEMENTED_REASON_CODE;
import static com.verteil.thirdparties.common.ThirdpartyConstants.ERROR_RESPONSE;
import static com.verteil.thirdparties.common.ThirdpartyConstants.SUCCESS_RESPONSE;
import static com.verteil.thirdparties.common.ThirdpartyConstants.SUPPLIER_RESPONSE_TIME_LOGGER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Single entry point for every Entry-Gate (VDC) call. Entry-Gate exposes a single JSON endpoint for every NDC service,
 * {@code POST {HOST}{PATH}} (e.g. {@code https://qaapi.sandbox.verteil.com/entrygate/rest/request}); the service is
 * selected by the {@code Service} header (e.g. {@code AirShopping}) and authenticated with a bearer token.
 * No {@code ThirdpartyId} header is sent, so Entry-Gate shops all airlines.
 */
@Slf4j
@Component
public class EntryGateClient {

    private static final Logger RESPONSE_TIME_LOGGER = LoggerFactory.getLogger(SUPPLIER_RESPONSE_TIME_LOGGER);

    /**
     * Entry-Gate matches {@code Content-Type} exactly against {@code application/json}, so the header must not carry a
     * {@code charset} parameter ({@link ContentType#APPLICATION_JSON} would send {@code application/json; charset=UTF-8}).
     * The body is still encoded as UTF-8 explicitly.
     */
    private static final ContentType ENTRY_GATE_CONTENT_TYPE = ContentType.create(APPLICATION_JSON_VALUE);

    private record ServiceSpec(Class<?> responseType, String entryGateService) {}

    private static final Map<String, ServiceSpec> SERVICE_SPEC_MAP = Map.of(
            SERVICE_AIRSHOPPING, new ServiceSpec(AirShoppingRS.class, SERVICE_AIRSHOPPING)
    );

    private final LoggerHelper loggerHelper;
    private final ThirdPartyConnector thirdPartyConnector;
    private final LocalTokenCache tokenCache;
    private final ObjectMapper ndcObjectMapper;

    public EntryGateClient(LoggerHelper loggerHelper, ThirdPartyConnector thirdPartyConnector, LocalTokenCache tokenCache,
                           @Qualifier("ndcObjectMapper") ObjectMapper ndcObjectMapper) {
        this.loggerHelper = loggerHelper;
        this.thirdPartyConnector = thirdPartyConnector;
        this.tokenCache = tokenCache;
        this.ndcObjectMapper = ndcObjectMapper;
    }

    public Object callWebService(String url, PkfareAdapterContext context) {
        boolean isErrorResponse = false;
        String apiService = context.getProviderApiService();
        Object request = context.getProviderApiRequest(apiService);
        long start = System.nanoTime();
        String httpStatusCode = EMPTY;
        HttpCallResult httpResponse = null;
        String jsonRequest = null;
        // Credentials, carrier scope and logging settings come from the inbound (PKFARE) service config.
        Configuration configuration = context.getConfigurations().get(context.getService()).subset(PKFARE_API);
        try {
            var serviceSpec = SERVICE_SPEC_MAP.get(apiService);
            if (serviceSpec == null) {
                throw new PkfareAdapterException(VALIDATION_FAILURE.getCode(), apiService + ERROR_MSG_SERVICE_NOT_IMPLEMENTED, UNIMPLEMENTED_REASON_CODE);
            }
            jsonRequest = ndcObjectMapper.writeValueAsString(request);
            int timeout = Optional.ofNullable(context.getTimeoutMillis()).orElse(DEFAULT_TIMEOUT_MILLIS);
            httpResponse = invoke(url, jsonRequest, serviceSpec, timeout, configuration, context);
            httpStatusCode = String.valueOf(httpResponse.statusCode());
            log.debug("Received response from Entry-Gate '{}', httpStatus={}", apiService, httpStatusCode);
            return ndcObjectMapper.readValue(httpResponse.body(), serviceSpec.responseType());
        } catch (Exception e) {
            isErrorResponse = true;
            log.error("Error calling Entry-Gate '{}', url={}, httpStatus={}", apiService, url, httpStatusCode, e);
            if (e instanceof PkfareAdapterException) throw (PkfareAdapterException) e;
            throw new PkfareAdapterException(INTERNAL_ERROR.getCode(), STANDARD_ERROR_MESSAGE + context.getTxnId(), e, null);
        } finally {
            long stop = System.nanoTime();
            RESPONSE_TIME_LOGGER.info("status={}, service={}, responseTimeMillis={}, transactionId={}, httpStatusCode={}",
                    isErrorResponse ? ERROR_RESPONSE : SUCCESS_RESPONSE, apiService, (stop - start) / 1000000,
                    context.getTxnId(), httpStatusCode);

            final Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
            final String finalRequest = jsonRequest;
            final String finalResponse = Optional.ofNullable(httpResponse).map(HttpCallResult::body).orElse(null);
            Thread.startVirtualThread(() -> loggerHelper.logRequestAndResponse(finalRequest, finalResponse, copyOfContextMap, configuration));
        }
    }

    /** Calls Entry-Gate; on HTTP 401 the cached token is dropped and the call retried once with a fresh one. */
    private HttpCallResult invoke(String url, String jsonRequest, ServiceSpec serviceSpec, int timeout,
                                  Configuration configuration, PkfareAdapterContext context) {
        try {
            return thirdPartyConnector.callSupplier(buildHttpRequest(url, jsonRequest, serviceSpec, timeout, configuration, context),
                    configuration, PKFARE, serviceSpec.entryGateService());
        } catch (PkfareAdapterException e) {
            if (!UNAUTHORIZED_REASON_CODE.equals(e.getReason())) {
                throw e;
            }
            log.warn("Entry-Gate rejected the cached token, retrying once with a new token");
            tokenCache.invalidate(configuration);
            return thirdPartyConnector.callSupplier(buildHttpRequest(url, jsonRequest, serviceSpec, timeout, configuration, context),
                    configuration, PKFARE, serviceSpec.entryGateService());
        }
    }

    private HttpPost buildHttpRequest(String url, String jsonBody, ServiceSpec serviceSpec, int timeout,
                                      Configuration configuration, PkfareAdapterContext context) {
        HttpPost post = new HttpPost(url);
        post.setEntity(new ByteArrayEntity(jsonBody.getBytes(UTF_8), ENTRY_GATE_CONTENT_TYPE));
        post.setConfig(thirdPartyConnector.buildRequestConfig(timeout));
        post.addHeader(ACCEPT, APPLICATION_JSON_VALUE);
        post.addHeader(AUTHORIZATION, OAUTH_BEARER_HEADER + tokenCache.getAccessToken(configuration));
        post.addHeader(ENTRY_GATE_SERVICE_HEADER, serviceSpec.entryGateService());
        String officeId = configuration.getString(OFFICE_ID, null);
        if (isNotBlank(officeId)) {
            post.addHeader(ENTRY_GATE_OFFICE_ID_HEADER, officeId);
        }
        if (isNotBlank(context.getTxnId())) {
            post.addHeader(ENTRY_GATE_TRANSACTION_ID_HEADER, context.getTxnId());
        }
        return post;
    }
}
