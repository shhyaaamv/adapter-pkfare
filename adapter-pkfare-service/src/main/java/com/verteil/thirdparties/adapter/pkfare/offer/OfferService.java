package com.verteil.thirdparties.adapter.pkfare.offer;

import com.verteil.chain.Catalog;
import com.verteil.commons.core.dto.RequestWrapperDto;
import com.verteil.commons.core.web.dto.UserDetailsDto;
import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schema.pkfare.SearchResponse;
import com.verteil.thirdparties.adapter.pkfare.client.interceptor.LoggerHelper;
import com.verteil.thirdparties.adapter.pkfare.common.AdapterCommonService;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.converter.ErrorResponseConverter;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.AUTHENTICATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.INTERNAL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.SERVICE_DISABLED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_INVALID_VENDOR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_SERVICE_NOT_IMPLEMENTED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_INTERNAL_ERROR_MSG;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_SEARCH;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAUTHORIZED_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNIMPLEMENTED_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.XCID;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.XSID;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.XTID;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Offer group of PKFARE services (SEARCH now, QTE next). Builds the chain context for an inbound PKFARE request —
 * the PKFARE vendor code doubles as configurator office code, so every PKFARE_API setting can be overridden per
 * vendor — gates on {@code PKFARE_API.ENABLE_SERVICE}, runs the chain named after the service and turns any failure
 * into a PKFARE status response. PKFARE always gets HTTP 200.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfferService {

    private final AdapterCommonService adapterCommonService;
    private final Catalog<PkfareAdapterContext> pkfareAdapterContextCatalog;
    private final ErrorResponseConverter errorResponseConverter;
    private final LoggerHelper loggerHelper;

    public SearchResponse search(SearchRequest request, String clientIp) {
        return (SearchResponse) process(SERVICE_SEARCH, request, Optional.ofNullable(request).map(SearchRequest::getVendorCode).orElse(null), clientIp);
    }

    private Object process(String service, Object request, String vendorCode, String clientIp) {
        final PkfareAdapterContext context;
        try {
            context = createContext(service, request, trimToNull(vendorCode), clientIp);
        } catch (RuntimeException e) {
            log.error("Unable to set up the PKFARE {} request", service, e);
            return errorResponseConverter.createErrorResponse(new PkfareAdapterException(INTERNAL_ERROR.getCode(),
                    PK_INTERNAL_ERROR_MSG + MDC.get(XTID), e, null), service);
        }
        MDC.put(XSID, service);
        Optional.ofNullable(vendorCode).ifPresent(code -> MDC.put(XCID, code));
        log.info("Adapter received PKFARE {} request from {}", service, clientIp);
        var hasError = false;
        try {
            if (context.getVendorCode() == null) {
                throw new PkfareAdapterException(AUTHENTICATION_FAILURE.getCode(), ERROR_MSG_INVALID_VENDOR, UNAUTHORIZED_REASON_CODE);
            }
            if (!adapterCommonService.isServiceEnabled(service, context.getVendorCode())) {
                throw new PkfareAdapterException(SERVICE_DISABLED.getCode(), service + ERROR_MSG_SERVICE_NOT_IMPLEMENTED, UNIMPLEMENTED_REASON_CODE);
            }
            pkfareAdapterContextCatalog.getCommand(service).execute(context);
            return context.getResponse();
        } catch (PkfareAdapterException e) {
            hasError = true;
            log.error("PKFARE {} failed: {} - {}", service, e.getErrorCode(), e.getMessage(), e);
            context.setResponse(errorResponseConverter.createErrorResponse(e, service));
            return context.getResponse();
        } catch (Exception exception) {
            hasError = true;
            log.error("PKFARE {} failed with an unexpected error", service, exception);
            context.setResponse(errorResponseConverter.createErrorResponse(new PkfareAdapterException(INTERNAL_ERROR.getCode(),
                    PK_INTERNAL_ERROR_MSG + context.getTxnId(), exception, null), service));
            return context.getResponse();
        } finally {
            var finalHasError = hasError;
            final Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
            Thread.startVirtualThread(() -> loggerHelper.logPkfareRequestAndResponse(context, finalHasError, copyOfContextMap));
        }
    }

    private static PkfareAdapterContext createContext(String service, Object request, String vendorCode, String clientIp) {
        String transactionId = defaultIfBlank(MDC.get(XTID), UUID.randomUUID().toString());

        UserDetailsDto userDetails = new UserDetailsDto();
        // The configurator context builder rejects nulls. The vendor code is the office code (per-vendor overrides);
        // the IATA dimension is not used by the adapter and is pinned to PKFARE.
        userDetails.setUsername(defaultIfBlank(vendorCode, PKFARE));
        userDetails.setOfficeCode(defaultIfBlank(vendorCode, PKFARE));
        userDetails.setOfficeIataCode(PKFARE);

        RequestWrapperDto requestWrapper = new RequestWrapperDto();
        requestWrapper.setService(service);
        requestWrapper.setServiceWithResourceOnly(service);
        requestWrapper.setRequest(request);
        requestWrapper.setTransactionId(transactionId);
        requestWrapper.setUserDetails(userDetails);
        requestWrapper.setRemoteAddr(clientIp);
        // ConnectorContext.putAll reads the first third-party id; PKFARE is the "third party" of every adapter request.
        requestWrapper.setThirdPartyIds(new ArrayList<>(List.of(PKFARE)));

        PkfareAdapterContext context = new PkfareAdapterContext();
        context.setRequestWrapper(requestWrapper);
        context.putAll(requestWrapper);
        context.setService(service);
        context.setRequest(request);
        context.setTxnId(transactionId);
        context.setUserDetails(userDetails);
        context.setCarrier(PKFARE);
        context.setVendorCode(vendorCode);
        context.setClientIp(clientIp);
        return context;
    }
}
