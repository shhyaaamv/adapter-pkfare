
package com.verteil.thirdparties.connector.pkfare.order;

import com.verteil.chain.Catalog;
import com.verteil.commons.core.dto.RequestWrapperDto;
import com.verteil.commons.core.utils.jaxb.preprocessor.JaxbResolver;
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
import static com.verteil.thirdparties.connector.pkfare.common.utils.PkfareConstants.STANDARD_ERROR_MESSAGE;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@Slf4j
@Component
public class OrderService {

    private final ConnectorCommonService connectorCommonService;
    private final Catalog<PkfareConnectorContext> pkfareConnectorContextCatalog;
    private final WarningsAndErrorConverter warningsAndErrorConverter;
    private final Executor executor;
    private final JaxbResolver jaxbResolver;

    public Object process(RequestWrapperDto requestWrapperDto) {
        log.info("Connector received request for service {}",  requestWrapperDto.getService());
        String thirdPartyId = getFirstThirdPartyId(requestWrapperDto);
        if (connectorCommonService.isServiceEnabled(requestWrapperDto, thirdPartyId)) {
            connectorCommonService.sanitizeRequestFromRequestWrapper(requestWrapperDto);
            Object response = processRequest(requestWrapperDto, thirdPartyId);
            response = jaxbResolver.resolve(response);
            requestWrapperDto.setResponse(response);
            return response;
        } else {
            return warningsAndErrorConverter.createErrorResponse(new PkfareConnectorException(INTERNAL_ERROR.getCode(),
                    requestWrapperDto.getService() + ERROR_MSG_SERVICE_NOT_IMPLEMENTED), requestWrapperDto.getService(), thirdPartyId);
        }
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
            log.error("Exception in pkfare: ", e);
            return warningsAndErrorConverter.createErrorResponse(e, requestWrapperDto.getService(), thirdPartyId);
        }
        catch (Exception exception) {
            hasError = true;
            log.error("Exception in pkfare: ", exception);
            return warningsAndErrorConverter.createErrorResponse(new PkfareConnectorException(INTERNAL_ERROR.getCode(),
                    STANDARD_ERROR_MESSAGE + requestWrapperDto.getTransactionId()), requestWrapperDto.getService(), thirdPartyId);
        }finally {
            var finalHasError = hasError;
            final Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
        }
    }
}
