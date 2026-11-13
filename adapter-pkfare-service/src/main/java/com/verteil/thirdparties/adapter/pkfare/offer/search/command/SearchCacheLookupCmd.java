package com.verteil.thirdparties.adapter.pkfare.offer.search.command;

import com.verteil.chain.Command;
import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.offer.search.cache.SearchResponseCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;



/**
 * Serves a cached SEARCH response; runs after authentication so a cached answer is never handed to an
 * unauthenticated caller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchCacheLookupCmd implements Command<PkfareAdapterContext> {

    private final SearchResponseCache searchResponseCache;

    @Override
    public boolean execute(PkfareAdapterContext context) {
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
