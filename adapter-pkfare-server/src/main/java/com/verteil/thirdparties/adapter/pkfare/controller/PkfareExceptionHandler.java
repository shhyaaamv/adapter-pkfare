package com.verteil.thirdparties.adapter.pkfare.controller;

import com.verteil.schema.pkfare.SearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_REQUEST_PARAMETER_ERROR;

/**
 * Requests that never reach a service (unreadable JSON, wrong content type) still get PKFARE's HTTP 200 +
 * status 4 "Request Parameters error" body rather than a Spring error page.
 */
@Slf4j
@RestControllerAdvice
public class PkfareExceptionHandler {

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public SearchResponse handleUnreadableRequest(Exception exception) {
        log.warn("Unreadable PKFARE request: {}", exception.getMessage());
        SearchResponse response = new SearchResponse();
        response.setStatus(PK_STATUS_REQUEST_PARAMETER_ERROR);
        response.setMsg("Request Parameters error");
        return response;
    }
}
