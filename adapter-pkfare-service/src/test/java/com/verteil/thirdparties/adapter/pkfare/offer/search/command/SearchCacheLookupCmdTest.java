package com.verteil.thirdparties.adapter.pkfare.offer.search.command;

import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schema.pkfare.SearchResponse;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.offer.search.cache.SearchResponseCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.VENDOR_CODE;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.searchRequest;
import static org.assertj.core.api.Assertions.assertThat;

class SearchCacheLookupCmdTest {

    private final SearchResponseCache cache = new SearchResponseCache();
    private final SearchCacheLookupCmd cmd = new SearchCacheLookupCmd(cache);
    private final SearchResponse cached = new SearchResponse();

    @BeforeEach
    void setUp() {
        cache.put(SearchResponseCache.keyOf(VENDOR_CODE, searchRequest()), cached);
    }

    @Test
    void servesTheCachedResponseForTheSameVendorAndSearchWhateverTheCutOff() {
        SearchRequest request = searchRequest();
        request.setCutOffTime(1);
        PkfareAdapterContext context = buildContext(request);

        assertThat(cmd.execute(context)).isTrue();
        assertThat(context.isCacheHit()).isTrue();
        assertThat(context.getResponse()).isSameAs(cached);
    }

    @Test
    void missesForAnotherVendorOrSearch() {
        SearchRequest otherVendor = searchRequest();
        otherVendor.setVendorCode("OTHER");
        SearchRequest otherDate = searchRequest();
        otherDate.setFromDate("20261021");

        assertThat(cmd.execute(buildContext(otherVendor))).isFalse();
        PkfareAdapterContext context = buildContext(otherDate);
        assertThat(cmd.execute(context)).isFalse();
        assertThat(context.isCacheHit()).isFalse();
        assertThat(context.getCacheKey()).isNotNull();
    }
}
