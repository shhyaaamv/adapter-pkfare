package com.verteil.thirdparties.adapter.pkfare.client.interceptor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import com.verteil.thirdparties.adapter.pkfare.config.PkfareProperties;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.verteil.commons.logmask.utils.LoggerUtil.logTraces;
import static java.util.Optional.ofNullable;

/**
 * Masked trace logging of the two message pairs an adapter request produces:
 * PKFARE request/response (inbound) and VDC request/response (Entry-Gate). Driven by {@code pkfare.message-log.*}.
 * Always called on a virtual thread with a copied MDC, never on the request thread.
 */
@Slf4j
@Component
public class LoggerHelper {

    private final PkfareProperties.MessageLog messageLog;
    private final ObjectMapper pkfareObjectMapper = JsonMappers.pkfare();

    public LoggerHelper(PkfareProperties pkfareProperties) {
        this.messageLog = pkfareProperties.getMessageLog();
    }

    /** Entry-Gate request/response pair (VDC JSON). */
    public void logRequestAndResponse(String request, String response, Map<String, String> copyOfContextMap) {
        setMdc(copyOfContextMap);
        if (messageLog.isEnabled()) {
            displayLogs(request, response);
        }
    }

    /**
     * PKFARE request/response pair. Logged when the message log is on, or — with it off — only for failed
     * requests when prod error monitoring is on.
     */
    public void logPkfareRequestAndResponse(PkfareAdapterContext context, boolean hasError, Map<String, String> copyOfContextMap) {
        setMdc(copyOfContextMap);
        try {
            if (messageLog.isEnabled() || (hasError && messageLog.isProdErrorMonitoring())) {
                displayLogs(toJson(context.getRequest()), toJson(context.getResponse()));
            }
        } catch (Exception e) {
            log.error("Error in logging the PKFARE request/response {}", e.getMessage());
        }
    }

    private void displayLogs(String request, String response) {
        logTraces(request, response, messageLog.getMaskElements(), messageLog.getMaskAttributes());
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
