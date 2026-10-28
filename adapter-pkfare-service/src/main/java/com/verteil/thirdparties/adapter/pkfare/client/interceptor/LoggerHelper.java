package com.verteil.thirdparties.adapter.pkfare.client.interceptor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.Configuration;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.verteil.commons.logmask.utils.LoggerUtil.logTraces;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.MESSAGE_LOG;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE_API;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PROD_ERROR_MONITORING;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.XCID;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.XSID;
import static com.verteil.thirdparties.common.utils.VdcConfigurationUtil.getMaskAttributesList;
import static com.verteil.thirdparties.common.utils.VdcConfigurationUtil.getMaskElementList;
import static java.util.Optional.ofNullable;

/**
 * Masked trace logging of the two message pairs an adapter request produces:
 * PKFARE request/response (inbound) and VDC request/response (Entry-Gate). Driven by {@code PKFARE_API.MESSAGE_LOG*}.
 * Always called on a virtual thread with a copied MDC, never on the request thread.
 */
@Slf4j
@Component
public class LoggerHelper {

    private final ObjectMapper pkfareObjectMapper = JsonMappers.pkfare();

    public boolean isLogEnabled(Configuration configuration) {
        var isEnabled = configuration.getBoolean(MESSAGE_LOG, true);
        log.debug("IsLogEnabled - Vendor : {}, Service : {} , Log enabled : {}", MDC.get(XCID), MDC.get(XSID), isEnabled);
        return isEnabled;
    }

    private boolean isProdMonitoringLogEnabled(Configuration configuration) {
        return configuration.getBoolean(PROD_ERROR_MONITORING, false);
    }

    /** Entry-Gate request/response pair (VDC JSON). */
    public void logRequestAndResponse(String request, String response, Map<String, String> copyOfContextMap, Configuration configuration) {
        setMdc(copyOfContextMap);
        if (isLogEnabled(configuration)) {
            displayLogs(request, response, configuration);
        }
    }

    /**
     * PKFARE request/response pair. Logged when MESSAGE_LOG is on, or — with MESSAGE_LOG off — only for failed
     * requests when PROD_ERROR_MONITORING is on.
     */
    public void logPkfareRequestAndResponse(PkfareAdapterContext context, boolean hasError, Map<String, String> copyOfContextMap) {
        setMdc(copyOfContextMap);
        try {
            ofNullable(context.getConfigurations())
                    .map(configurations -> configurations.get(context.getService()))
                    .map(vdcConfiguration -> vdcConfiguration.subset(PKFARE_API))
                    .filter(configuration -> isLogEnabled(configuration) || (hasError && isProdMonitoringLogEnabled(configuration)))
                    .ifPresent(configuration -> displayLogs(toJson(context.getRequest()), toJson(context.getResponse()), configuration));
        } catch (Exception e) {
            log.error("Error in logging the PKFARE request/response {}", e.getMessage());
        }
    }

    public void displayLogs(String request, String response, Configuration configuration) {
        final var messageLogConfiguration = configuration.subset(MESSAGE_LOG);
        final var maskElementList = getMaskElementList(messageLogConfiguration);
        final var maskAttributesList = getMaskAttributesList(messageLogConfiguration);
        logTraces(request, response, maskElementList, maskAttributesList);
    }

    private String toJson(Object message) {
        return ofNullable(message).map(m -> {
            try {
                return pkfareObjectMapper.writeValueAsString(m);
            } catch (JsonProcessingException e) {
                log.warn("Unable to serialize message for logging: {}", e.getMessage());
                return null;
            }
        }).orElse(null);
    }

    private static void setMdc(Map<String, String> copyOfContextMap) {
        ofNullable(copyOfContextMap).ifPresent(MDC::setContextMap);
    }
}
