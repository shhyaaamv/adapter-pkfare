
package com.verteil.thirdparties.connector.pkfare.common.exception;

import com.verteil.commons.core.exception.VerteilCommonsException;

public class PkfareConnectorException extends VerteilCommonsException {

    public PkfareConnectorException(String errorCode) {
        super(errorCode);
    }

    public PkfareConnectorException(String errorCode, String msg) {
        super(errorCode, msg);
    }

    public PkfareConnectorException(Throwable cause) {
        super(cause);
    }

    public PkfareConnectorException(String errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public PkfareConnectorException(String errorCode, String msg, Throwable cause) {
        super(errorCode, msg, cause);
    }
}
