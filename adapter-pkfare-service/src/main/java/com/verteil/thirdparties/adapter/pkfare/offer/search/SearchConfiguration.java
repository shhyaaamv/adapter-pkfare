package com.verteil.thirdparties.adapter.pkfare.offer.search;

import com.verteil.chain.Chain;
import com.verteil.chain.impl.ImmutableChain;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.command.AuthenticateRequestCmd;
import com.verteil.thirdparties.adapter.pkfare.common.command.FetchCabinMappingCmd;
import com.verteil.thirdparties.adapter.pkfare.common.command.InvokeEntryGateCmd;
import com.verteil.thirdparties.adapter.pkfare.offer.search.command.AirShoppingRSValidatorCmd;
import com.verteil.thirdparties.adapter.pkfare.offer.search.command.SearchCacheLookupCmd;
import com.verteil.thirdparties.adapter.pkfare.offer.search.command.SearchCacheStoreCmd;
import com.verteil.thirdparties.adapter.pkfare.offer.search.command.SearchRQCmd;
import com.verteil.thirdparties.adapter.pkfare.offer.search.command.SearchRSCmd;
import com.verteil.thirdparties.connector.common.chain.AddProviderApiParamsInConfigContextCmd;
import com.verteil.thirdparties.connector.common.chain.FetchConfigurationsCmd;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE_API;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AIRSHOPPING;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_SEARCH;

/**
 * PKFARE SEARCH -> Entry-Gate AirShopping.
 * <pre>
 * AddParams(Search, AirShopping) -> FetchConfigurations -> AuthenticateRequest (vendor code + IP)
 *   -> SearchCacheLookup [hit: done] -> FetchCabinMapping -> SearchRQ -> InvokeEntryGate
 *   -> AirShoppingRSValidator [no offers: done]
 *   -> SearchRS -> SearchCacheStore
 * </pre>
 */
@Configuration
public class SearchConfiguration {

    private AddProviderApiParamsInConfigContextCmd<PkfareAdapterContext> addProviderApisInConfigContextCmd() {
        return new AddProviderApiParamsInConfigContextCmd<>(PKFARE_API, SERVICE_SEARCH, SERVICE_AIRSHOPPING);
    }

    @Bean(SERVICE_SEARCH)
    public Chain<PkfareAdapterContext> search(FetchConfigurationsCmd<PkfareAdapterContext> fetchConfigurationsCmd,
                                              AuthenticateRequestCmd authenticateRequestCmd,
                                              SearchCacheLookupCmd searchCacheLookupCmd,
                                              FetchCabinMappingCmd fetchCabinMappingCmd,
                                              SearchRQCmd searchRQCmd,
                                              InvokeEntryGateCmd invokeEntryGateCmd,
                                              AirShoppingRSValidatorCmd airShoppingRSValidatorCmd,
                                              SearchRSCmd searchRSCmd,
                                              SearchCacheStoreCmd searchCacheStoreCmd) {
        return ImmutableChain.<PkfareAdapterContext>newBuilder()
                .add(addProviderApisInConfigContextCmd())
                .add(fetchConfigurationsCmd)
                .add(authenticateRequestCmd)
                .add(searchCacheLookupCmd)
                .add(fetchCabinMappingCmd)
                .add(searchRQCmd)
                .add(invokeEntryGateCmd)
                .add(airShoppingRSValidatorCmd)
                .add(searchRSCmd)
                .add(searchCacheStoreCmd)
                .build();
    }
}
