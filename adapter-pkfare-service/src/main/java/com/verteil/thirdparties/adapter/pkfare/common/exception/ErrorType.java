package com.verteil.thirdparties.adapter.pkfare.common.exception;

public enum ErrorType {
    INTERNAL_ERROR("INTERNAL_ERROR"),
    VALIDATION_FAILURE("VALIDATION_FAILURE"),
    AUTHENTICATION_FAILURE("AUTHENTICATION_FAILURE"),
    SERVICE_DISABLED("SERVICE_DISABLED"),
    ENTRY_GATE_ERROR("ENTRY_GATE_ERROR");

    private final String code;

    ErrorType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
