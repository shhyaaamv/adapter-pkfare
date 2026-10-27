
package com.verteil.thirdparties.connector.pkfare.common;

import com.verteil.commons.core.dto.RequestWrapperDto;
import com.verteil.commons.core.utils.jaxb.preprocessor.JaxbResolver;
import com.verteil.configurator.vdc.client.VdcConfiguratorServiceClient;
import com.verteil.configurator.vdc.data.VdcConfigurationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ConnectorCommonService {

    private Map<String, Boolean> serviceStatusMap = new HashMap<>();
    private final VdcConfiguratorServiceClient vdcConfiguratorServiceClient;
    private final JaxbResolver jaxbResolver;

    public boolean isServiceEnabled(RequestWrapperDto requestWrapperDto, String thirdPartyId) {
      var apiService = requestWrapperDto.getService();

       return serviceStatusMap.computeIfAbsent(thirdPartyId+apiService,
               key -> {
                    VdcConfigurationContext vdcConfigurationContext =
                            VdcConfigurationContext.newBuilder()
                                    .carrier(thirdPartyId)
                                    .iata(requestWrapperDto.getUserDetails().getOfficeIataCode())
                                    .apiService(apiService)
                                    .addParams("PKFARE_API")
                                    .build();
                    return vdcConfiguratorServiceClient.fetchConfiguration(vdcConfigurationContext)
                            .getBoolean("PKFARE_API", "ENABLE_SERVICE", false);
       });
    }

    public Object sanitizeRequestFromRequestWrapper(RequestWrapperDto requestWrapper) {
        Object request = requestWrapper.getRequest();
        request = jaxbResolver.resolve(request);
        jaxbResolver.fixXmlGregorianCalendarValues(request);
        return request;
    }

    public Object sanitizeResponse(Object response, String thirdPartyId) {
        jaxbResolver.resolve(response);
        jaxbResolver.prefixIds(response, thirdPartyId);
        return response;
    }

    public void stripPrefixInIds(Object request, String thirdPartyId) {
        jaxbResolver.stripPrefixInIds(request, thirdPartyId);
    }
}
