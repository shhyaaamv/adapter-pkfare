package com.verteil.thirdparties.adapter.pkfare.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.XTID;

/**
 * Copies the Verteil tracing headers into the MDC when present. PKFARE sends none of them, so a transaction id is
 * generated per request (X-TID); it is also returned as a response header and sent to Entry-Gate.
 */
@Component
public class RequestLoggingFilter implements Filter {

    private static final List<String> HEADERS = Arrays.asList("X-UID", "X-IATA", "X-SID", "X-CID", "X-TID");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        final HttpServletRequest req = (HttpServletRequest) request;
        HEADERS.forEach(header -> Optional.ofNullable(req.getHeader(header)).ifPresent(value -> MDC.put(header, value)));
        String transactionId = Optional.ofNullable(MDC.get(XTID)).orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
        MDC.put(XTID, transactionId);
        ((HttpServletResponse) response).setHeader(XTID, transactionId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
