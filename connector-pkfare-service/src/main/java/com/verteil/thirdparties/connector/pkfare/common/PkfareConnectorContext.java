
package com.verteil.thirdparties.connector.pkfare.common;

import com.verteil.thirdparties.connector.common.chain.ConnectorContext;

public class PkfareConnectorContext extends ConnectorContext {
    protected static final String KEY_STATUS_CODE = "$.statusCode";

    public String getStatusCode() {
        return (String) context.get(KEY_STATUS_CODE);
    }

    public void setStatusCode(String statusCode) {
        context.put(KEY_STATUS_CODE, statusCode);
    }
}
