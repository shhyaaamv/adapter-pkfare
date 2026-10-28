package com.verteil.thirdparties.adapter.pkfare.offer.search.command;

import com.verteil.chain.Command;
import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.offer.search.cache.SearchResponseCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE_API;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SEARCH_CACHE_TTL_SECONDS;

/**
 * Serves a cached SEARCH response when caching is on for this vendor; runs after authentication so a cached answer
 * is never handed to an unauthenticated caller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchCacheLookupCmd implements Command<PkfareAdapterContext> {

    private final SearchResponseCache searchResponseCache;

    @Override
    public boolean execute(PkfareAdapterContext context) {
        long ttlSeconds = Optional.ofNullable(context.getConfigurations().get(context.getService()))
                .map(configuration -> configuration.getLong(PKFARE_API, SEARCH_CACHE_TTL_SECONDS, 0L))
                .orElse(0L);
        if (ttlSeconds <= 0) {
            return CONTINUE_PROCESSING;
        }
        String cacheKey = SearchResponseCache.keyOf(context.getVendorCode(), (SearchRequest) context.getRequest());
        context.setCacheKey(cacheKey);
        var cached = searchResponseCache.get(cacheKey);
        if (cached.isPresent()) {
            log.info("Search served from cache");
            context.setCacheHit(true);
            context.setResponse(cached.get());
            return PROCESSING_COMPLETE;
        }
        return CONTINUE_PROCESSING;
    }
}
