package com.verteil.thirdparties.adapter.pkfare.offer.search.command;

import com.verteil.schema.pkfare.SearchResponse;
import com.verteil.schema.pkfare.SearchShoppingResult;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.offer.search.cache.SearchResponseCache;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.searchRequest;
import static org.assertj.core.api.Assertions.assertThat;

class SearchCacheStoreCmdTest {

    private final SearchResponseCache cache = new SearchResponseCache();
    private final SearchCacheStoreCmd cmd = new SearchCacheStoreCmd(cache);

    @Test
    void cachesASuccessfulResponseWithResults() {
        SearchResponse response = response(0, 1);

        assertThat(cmd.execute(context("KEY", response))).isTrue();
        assertThat(cache.get("KEY")).containsSame(response);
    }

    @Test
    void neverCachesFailuresOrEmptyResults() {
        cmd.execute(context("FAILED", response(3, 1)));
        cmd.execute(context("EMPTY", response(0, 0)));

        assertThat(cache.get("FAILED")).isEmpty();
        assertThat(cache.get("EMPTY")).isEmpty();
    }

    private static PkfareAdapterContext context(String cacheKey, SearchResponse response) {
        PkfareAdapterContext context = buildContext(searchRequest());
        context.setCacheKey(cacheKey);
        context.setResponse(response);
        return context;
    }

    private static SearchResponse response(int status, int results) {
        SearchResponse response = new SearchResponse();
        response.setStatus(status);
        response.getShoppingResultList().addAll(Collections.nCopies(results, new SearchShoppingResult()));
        return response;
    }
}
