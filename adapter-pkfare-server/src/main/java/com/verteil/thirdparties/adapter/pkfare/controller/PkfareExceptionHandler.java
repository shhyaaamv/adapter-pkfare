package com.verteil.thirdparties.adapter.pkfare.controller;

import com.verteil.thirdparties.adapter.pkfare.common.converter.ErrorResponseConverter;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Optional;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VALIDATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.MSG_REQUEST_PARAMETERS_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_QTE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_SEARCH;

/**
 * Requests that never reach a service (unreadable JSON, wrong content type) still get PKFARE's HTTP 200 +
 * status 4 "Request Parameters error" body rather than a Spring error page: a {@code QteResponse} on {@code /qte},
 * a {@code SearchResponse} anywhere else.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class PkfareExceptionHandler {

    private static final String QTE_PATH_SUFFIX = "/qte";

    private final ErrorResponseConverter errorResponseConverter;

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public Object handleUnreadableRequest(Exception exception, HttpServletRequest request) {
        log.warn("Unreadable PKFARE request on {}: {}", request.getRequestURI(), exception.getMessage());
        return errorResponseConverter.createErrorResponse(
                new PkfareAdapterException(VALIDATION_FAILURE.getCode(), MSG_REQUEST_PARAMETERS_ERROR), serviceOf(request));
    }

    private static String serviceOf(HttpServletRequest request) {
        return Optional.ofNullable(request.getRequestURI())
                .filter(path -> path.endsWith(QTE_PATH_SUFFIX))
                .map(path -> SERVICE_QTE)
                .orElse(SERVICE_SEARCH);
    }
}
