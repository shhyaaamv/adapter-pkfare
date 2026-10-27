
package com.verteil.thirdparties.connector.pkfare.common.exception;

public enum ErrorType {
    INTERNAL_ERROR("INTERNAL_ERROR"),
    VALIDATION_FAILURE("VALIDATION_FAILURE"),
    AIRLINE_ERROR("AIRLINE_ERROR");

    private final String code;

    ErrorType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
