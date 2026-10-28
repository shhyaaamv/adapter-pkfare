package com.verteil.thirdparties.adapter.pkfare.common.command;

import com.verteil.configurator.vdc.client.VdcConfiguratorServiceClient;
import com.verteil.configurator.vdc.data.VdcConfiguration;
import com.verteil.configurator.vdc.data.VdcConfigurationContext;
import com.verteil.thirdparties.adapter.pkfare.common.AdapterCommonService;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.INTERNAL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.searchRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FetchCabinMappingCmdTest {

    private final VdcConfiguratorServiceClient vdcConfiguratorServiceClient = mock(VdcConfiguratorServiceClient.class);
    private final FetchCabinMappingCmd cmd = new FetchCabinMappingCmd(new AdapterCommonService(vdcConfiguratorServiceClient));

    @Test
    void readsMappedCabinMasterDataForPkfare() {
        when(vdcConfiguratorServiceClient.fetchMasterDataConfigs(any(VdcConfigurationContext.class))).thenReturn(Map.of(
                "S", mappedCabin("W"),
                "Y", mappedCabin("Y")));
        PkfareAdapterContext context = buildContext(searchRequest(), Map.of());
        context.setCabinMapping(null);

        assertThat(cmd.execute(context)).isFalse();
        cmd.execute(context);

        assertThat(context.getCabinMapping().toVdc("S")).contains("W");
        assertThat(context.getCabinMapping().toPk("W")).contains("S");
        ArgumentCaptor<VdcConfigurationContext> request = ArgumentCaptor.forClass(VdcConfigurationContext.class);
        verify(vdcConfiguratorServiceClient, times(1)).fetchMasterDataConfigs(request.capture());
        assertThat(request.getValue().getParams()).containsOnlyKeys("CABIN_BRAND");
        assertThat(request.getValue().getArgs()).containsValue("PKF");
    }

    @Test
    void failsWhenNoMappingIsConfigured() {
        when(vdcConfiguratorServiceClient.fetchMasterDataConfigs(any(VdcConfigurationContext.class))).thenReturn(Map.of());

        assertThatThrownBy(() -> cmd.execute(buildContext(searchRequest(), Map.of())))
                .isInstanceOf(PkfareAdapterException.class)
                .extracting("errorCode").isEqualTo(INTERNAL_ERROR.getCode());
    }

    private static VdcConfiguration mappedCabin(String vdcCabin) {
        return new VdcConfiguration(Map.of("CABIN_BRAND.MAPPED_CABIN", vdcCabin));
    }
}
