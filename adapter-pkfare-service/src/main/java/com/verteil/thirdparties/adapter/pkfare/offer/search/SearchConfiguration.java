package com.verteil.thirdparties.adapter.pkfare.offer.search;

import com.verteil.chain.Chain;
import com.verteil.chain.Command;
import com.verteil.chain.impl.ImmutableChain;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.command.AuthenticateRequestCmd;
import com.verteil.thirdparties.adapter.pkfare.common.command.InvokeEntryGateCmd;
import com.verteil.thirdparties.adapter.pkfare.offer.search.command.AirShoppingRSValidatorCmd;
import com.verteil.thirdparties.adapter.pkfare.offer.search.command.SearchCacheLookupCmd;
import com.verteil.thirdparties.adapter.pkfare.offer.search.command.SearchCacheStoreCmd;
import com.verteil.thirdparties.adapter.pkfare.offer.search.command.SearchRQCmd;
import com.verteil.thirdparties.adapter.pkfare.offer.search.command.SearchRSCmd;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Stream;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.AirShoppingConstants.SEARCH_CACHE_ENABLED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_SEARCH;

/**
 * PKFARE SEARCH -> Entry-Gate AirShopping.
 * <pre>
 * AuthenticateRequest (vendor code + IP) -> SearchCacheLookup [hit: done] -> SearchRQ -> InvokeEntryGate
 *   -> AirShoppingRSValidator [no offers: done]
 *   -> SearchRS -> SearchCacheStore
 * </pre>
 * The two cache commands are left out of the chain unless {@code SEARCH_CACHE_ENABLED}.
 */
@Configuration
public class SearchConfiguration {

    @Bean(SERVICE_SEARCH)
    public Chain<PkfareAdapterContext> search(AuthenticateRequestCmd authenticateRequestCmd,
                                              SearchCacheLookupCmd searchCacheLookupCmd,
                                              SearchRQCmd searchRQCmd,
                                              InvokeEntryGateCmd invokeEntryGateCmd,
                                              AirShoppingRSValidatorCmd airShoppingRSValidatorCmd,
                                              SearchRSCmd searchRSCmd,
                                              SearchCacheStoreCmd searchCacheStoreCmd) {
        return ImmutableChain.<PkfareAdapterContext>newBuilder()
                .addAll(Stream.<Command<PkfareAdapterContext>>of(authenticateRequestCmd, searchCacheLookupCmd, searchRQCmd,
                                invokeEntryGateCmd, airShoppingRSValidatorCmd, searchRSCmd, searchCacheStoreCmd)
                        .filter(SearchConfiguration::isActive)
                        .toList())
                .build();
    }

    private static boolean isActive(Command<PkfareAdapterContext> command) {
        return SEARCH_CACHE_ENABLED || !(command instanceof SearchCacheLookupCmd || command instanceof SearchCacheStoreCmd);
    }
}
