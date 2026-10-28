package com.verteil.thirdparties.adapter.pkfare.common.exception;

import com.verteil.commons.core.exception.VerteilCommonsException;

public class PkfareAdapterException extends VerteilCommonsException {

    private final String reason;

    public PkfareAdapterException(String errorCode, String msg) {
        super(errorCode, msg);
        this.reason = null;
    }

    public PkfareAdapterException(String errorCode, String msg, String reason) {
        super(errorCode, msg);
        this.reason = reason;
    }

    public PkfareAdapterException(String errorCode, String msg, Throwable cause, String reason) {
        super(errorCode, msg, cause);
        this.reason = reason;
    }

    public String getReason() {
        return this.reason;
    }
}
