package com.verteil.thirdparties.adapter.pkfare.common;

import com.verteil.configurator.vdc.VdcConfiguratorService;
import com.verteil.configurator.vdc.data.VdcConfigurationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ENABLE_SERVICE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE_API;

@Component
@RequiredArgsConstructor
public class AdapterCommonService {

    private final VdcConfiguratorService vdcConfiguratorService;

    /**
     * {@code PKFARE_API.ENABLE_SERVICE} for this adapter service (e.g. {@code Search}), falling back to the
     * NULL-service row. Evaluated in memory, so it needs no cache of its own.
     */
    public boolean isServiceEnabled(String service) {
        VdcConfigurationContext vdcConfigurationContext = VdcConfigurationContext.newBuilder()
                .apiService(service)
                .addParams(PKFARE_API)
                .build();
        return vdcConfiguratorService.fetchConfiguration(vdcConfigurationContext)
                .getBoolean(PKFARE_API, ENABLE_SERVICE, false);
    }
}
