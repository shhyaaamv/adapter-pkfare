
package com.verteil.thirdparties.connector.pkfare.common.converter;
import com.verteil.thirdparties.connector.pkfare.common.exception.PkfareConnectorException;
import com.verteil.thirdparties.connector.pkfare.client.PkfareVdcConfiguratorServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import lombok.AllArgsConstructor;

@Slf4j
@Component
@AllArgsConstructor
public class WarningsAndErrorConverter {

    private final PkfareVdcConfiguratorServiceClient pkfareVdcConfiguratorServiceClient;

    public Object createErrorResponse(PkfareConnectorException ex, String service, String carrier) {
        return null;
    }
}
