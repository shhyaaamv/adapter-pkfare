
package com.verteil.thirdparties.connector.pkfare.offer;

import com.verteil.chain.Catalog;
import com.verteil.commons.core.dto.RequestWrapperDto;
import com.verteil.thirdparties.connector.pkfare.common.PkfareConnectorContext;
import com.verteil.thirdparties.connector.pkfare.common.ConnectorCommonService;
import com.verteil.thirdparties.connector.pkfare.common.converter.WarningsAndErrorConverter;
import com.verteil.thirdparties.connector.pkfare.common.exception.PkfareConnectorException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Executor;

import static com.verteil.commons.core.utils.RequestWrapperUtil.getFirstThirdPartyId;
import static com.verteil.thirdparties.connector.pkfare.common.exception.ErrorType.INTERNAL_ERROR;
import static com.verteil.thirdparties.connector.pkfare.common.utils.PkfareConstants.ERROR_MSG_SERVICE_NOT_IMPLEMENTED;
import static com.verteil.thirdparties.connector.pkfare.common.utils.PkfareConstants.SERVICE_AIRSHOPPING;
import static com.verteil.thirdparties.connector.pkfare.common.utils.PkfareConstants.SERVICE_FLIGHT_PRICE;
import static com.verteil.thirdparties.connector.pkfare.common.utils.PkfareConstants.SERVICE_SEAT_AVAILABILITY;
import static com.verteil.thirdparties.connector.pkfare.common.utils.PkfareConstants.SERVICE_SERVICELIST;
import static com.verteil.thirdparties.connector.pkfare.common.utils.PkfareConstants.STANDARD_ERROR_MESSAGE;
import lombok.AllArgsConstructor;

@Slf4j
@AllArgsConstructor
@Component
public class OfferService {

    private final ConnectorCommonService connectorCommonService;
    private final Catalog<PkfareConnectorContext> pkfareConnectorContextCatalog;
    private final WarningsAndErrorConverter warningsAndErrorConverter;
    private final Executor executor;


    public Object process(RequestWrapperDto requestWrapper) {
        String thirdPartyId = getFirstThirdPartyId(requestWrapper);

        if (connectorCommonService.isServiceEnabled(requestWrapper, thirdPartyId)) {
            Object request = connectorCommonService.sanitizeRequestFromRequestWrapper(requestWrapper);
            if(isPrimeBookingOfferService(requestWrapper)){
                connectorCommonService.stripPrefixInIds(request, thirdPartyId);
            }
            Object response = processRequest(requestWrapper, thirdPartyId);
            if(SERVICE_AIRSHOPPING.equals(requestWrapper.getService())) {
                response = connectorCommonService.sanitizeResponse(response, thirdPartyId);
            }
            requestWrapper.setResponse(response);
            return response;
        } else {
            return warningsAndErrorConverter.createErrorResponse(new PkfareConnectorException(INTERNAL_ERROR.getCode(),
                    requestWrapper.getService() + ERROR_MSG_SERVICE_NOT_IMPLEMENTED), requestWrapper.getService(), thirdPartyId);
        }
    }

    private boolean isPrimeBookingOfferService(RequestWrapperDto requestWrapper) {
        return SERVICE_FLIGHT_PRICE.equals(requestWrapper.getService())
                || SERVICE_SEAT_AVAILABILITY.equals(requestWrapper.getService())
                || SERVICE_SERVICELIST.equals(requestWrapper.getService());
    }

    private Object processRequest(RequestWrapperDto requestWrapperDto, String thirdPartyId) {
        PkfareConnectorContext pkfareConnectorContext = new PkfareConnectorContext();
        pkfareConnectorContext.setRequestWrapper(requestWrapperDto);
        pkfareConnectorContext.putAll(requestWrapperDto);
        var hasError = false;
        try {
            pkfareConnectorContextCatalog.getCommand(requestWrapperDto.getService()).execute(pkfareConnectorContext);
            return pkfareConnectorContext.getResponse();
        }catch (PkfareConnectorException e) {
            hasError = true;
            log.error("Exception in pkfare  : ", e);
            return warningsAndErrorConverter.createErrorResponse(e, requestWrapperDto.getService(), thirdPartyId);
        }
        catch (Exception exception) {
            hasError = true;
            log.error("Exception in pkfare", exception);
             return warningsAndErrorConverter.createErrorResponse(new PkfareConnectorException(INTERNAL_ERROR.getCode(),
                             STANDARD_ERROR_MESSAGE + requestWrapperDto.getTransactionId()), requestWrapperDto.getService(), thirdPartyId);
        }finally {
            var finalHasError = hasError;
            final Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
        }
    }
}
