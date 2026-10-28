package com.verteil.thirdparties.adapter.pkfare.offer.search.command;

import com.verteil.schema.pkfare.SearchResponse;
import com.verteil.schema.pkfare.SearchShoppingResult;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.offer.search.cache.SearchResponseCache;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.searchRequest;
import static org.assertj.core.api.Assertions.assertThat;

class SearchCacheCmdTest {

    private final SearchResponseCache cache = new SearchResponseCache(1000);
    private final SearchCacheLookupCmd lookup = new SearchCacheLookupCmd(cache);
    private final SearchCacheStoreCmd store = new SearchCacheStoreCmd(cache);

    @Test
    void cachesSuccessfulResponsesAndServesThemToTheSameVendorAndSearch() {
        PkfareAdapterContext first = buildContext(searchRequest(), Map.of("SEARCH_CACHE_TTL_SECONDS", "60"));
        assertThat(lookup.execute(first)).isFalse();
        SearchResponse response = successWithOneResult();
        first.setResponse(response);
        assertThat(store.execute(first)).isTrue();

        PkfareAdapterContext second = buildContext(searchRequest(), Map.of("SEARCH_CACHE_TTL_SECONDS", "60"));
        assertThat(lookup.execute(second)).isTrue();
        assertThat(second.isCacheHit()).isTrue();
        assertThat(second.getResponse()).isSameAs(response);
    }

    @Test
    void cacheKeyIgnoresCutoffButNotSearchCriteriaOrVendor() {
        var request = searchRequest();
        String key = SearchResponseCache.keyOf("SHA-TEST", request);

        request.setCutOffTime(1);
        assertThat(SearchResponseCache.keyOf("SHA-TEST", request)).isEqualTo(key);
        assertThat(SearchResponseCache.keyOf("OTHER", request)).isNotEqualTo(key);
        request.setFromDate("20261021");
        assertThat(SearchResponseCache.keyOf("SHA-TEST", request)).isNotEqualTo(key);
    }

    @Test
    void doesNothingWhenCachingIsOff() {
        PkfareAdapterContext context = buildContext(searchRequest(), Map.of());
        assertThat(lookup.execute(context)).isFalse();
        assertThat(context.getCacheKey()).isNull();
        context.setResponse(successWithOneResult());
        assertThat(store.execute(context)).isTrue();

        PkfareAdapterContext withCache = buildContext(searchRequest(), Map.of("SEARCH_CACHE_TTL_SECONDS", "60"));
        assertThat(lookup.execute(withCache)).isFalse();
    }

    @Test
    void neverCachesFailuresOrEmptyResults() {
        PkfareAdapterContext context = buildContext(searchRequest(), Map.of("SEARCH_CACHE_TTL_SECONDS", "60"));
        lookup.execute(context);
        SearchResponse empty = new SearchResponse();
        empty.setStatus(0);
        context.setResponse(empty);
        store.execute(context);

        assertThat(cache.get(context.getCacheKey())).isEmpty();
    }

    private static SearchResponse successWithOneResult() {
        SearchResponse response = new SearchResponse();
        response.setStatus(0);
        response.setMsg("success");
        response.getShoppingResultList().add(new SearchShoppingResult());
        return response;
    }
}
