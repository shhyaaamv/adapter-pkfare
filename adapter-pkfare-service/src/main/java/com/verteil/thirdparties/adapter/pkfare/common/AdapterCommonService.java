package com.verteil.thirdparties.adapter.pkfare.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.verteil.configurator.vdc.client.VdcConfiguratorServiceClient;
import com.verteil.configurator.vdc.data.VdcConfigurationContext;
import org.springframework.stereotype.Component;

import java.time.Duration;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ENABLE_SERVICE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PIPE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE_API;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_STATUS_CACHE_TTL_MINUTES;

@Component
public class AdapterCommonService {

    private final VdcConfiguratorServiceClient vdcConfiguratorServiceClient;
    // Memoised per vendor + service, but (unlike the connectors) only for a few minutes so a config change
    // takes effect without restarting pods.
    private final Cache<String, Boolean> serviceStatusCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(SERVICE_STATUS_CACHE_TTL_MINUTES))
            .maximumSize(1000)
            .build();

    public AdapterCommonService(VdcConfiguratorServiceClient vdcConfiguratorServiceClient) {
        this.vdcConfiguratorServiceClient = vdcConfiguratorServiceClient;
    }

    /**
     * {@code PKFARE_API.ENABLE_SERVICE} for this adapter service (e.g. {@code Search}), resolved with the PKFARE
     * vendor code as configurator office code, so a service can be switched on/off globally or per vendor.
     */
    public boolean isServiceEnabled(String service, String vendorCode) {
        return serviceStatusCache.get(vendorCode + PIPE + service, _ -> {
            VdcConfigurationContext vdcConfigurationContext = VdcConfigurationContext.newBuilder()
                    .officeCode(vendorCode)
                    .apiService(service)
                    .addParams(PKFARE_API)
                    .build();
            return vdcConfiguratorServiceClient.fetchConfiguration(vdcConfigurationContext)
                    .getBoolean(PKFARE_API, ENABLE_SERVICE, false);
        });
    }
}
