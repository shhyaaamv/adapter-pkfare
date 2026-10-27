
package com.verteil.thirdparties.connector.pkfare.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class RequestLoggingFilter implements Filter {

    private static final List<String> HEADERS = Arrays.asList("X-UID", "X-IATA", "X-SID", "X-CID", "X-TID");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        final HttpServletRequest req = (HttpServletRequest) request;
        HEADERS.forEach(header -> {
            String value = req.getHeader(header);
            if (value != null) {
                MDC.put(header, value);
            }
        });
        chain.doFilter(request, response);
    }
}
