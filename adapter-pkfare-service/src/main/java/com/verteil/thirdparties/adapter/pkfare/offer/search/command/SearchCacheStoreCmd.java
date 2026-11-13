package com.verteil.thirdparties.adapter.pkfare.offer.search.command;

import com.verteil.chain.Command;
import com.verteil.schema.pkfare.SearchResponse;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.offer.search.cache.SearchResponseCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_SUCCESS;
import static java.util.Objects.nonNull;

/** Final command of the Search chain: caches a successful, non-empty response. */
@Component
@RequiredArgsConstructor
public class SearchCacheStoreCmd implements Command<PkfareAdapterContext> {

    private final SearchResponseCache searchResponseCache;

    @Override
    public boolean execute(PkfareAdapterContext context) {
        if (nonNull(context.getCacheKey()) && context.getResponse() instanceof SearchResponse response
                && Integer.valueOf(PK_STATUS_SUCCESS).equals(response.getStatus()) && !response.getShoppingResultList().isEmpty()) {
            searchResponseCache.put(context.getCacheKey(), response);
        }
        return PROCESSING_COMPLETE;
    }
}
