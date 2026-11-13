package com.verteil.thirdparties.adapter.pkfare.common.exception;

import lombok.Getter;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VERTEIL_ERROR;

/** Entry-Gate answered with an HTTP error status; keeps the status and body for callers that need to inspect them. */
@Getter
public class EntryGateHttpException extends PkfareAdapterException {

    private final int statusCode;
    private final String body;

    public EntryGateHttpException(int statusCode, String body, String reason) {
        super(VERTEIL_ERROR.getCode(), "HTTP " + statusCode + " from Verteil", reason);
        this.statusCode = statusCode;
        this.body = body;
    }
}
