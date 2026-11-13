package com.verteil.thirdparties.adapter.pkfare.offer.search.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schema.pkfare.SearchResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.AirShoppingConstants.SEARCH_CACHE_MAX_WEIGHT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.AirShoppingConstants.SEARCH_CACHE_TTL_SECONDS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_CABIN;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PIPE;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * Short-lived, per-pod cache of successful SEARCH responses. PKFARE re-sends identical searches at a high rate and
 * prices are re-validated at QTE anyway, so a TTL of a few minutes ({@code SEARCH_CACHE_TTL_SECONDS}) trades negligible staleness for far fewer Entry-Gate calls.
 * <p>
 * Keyed by vendor code + every request field that changes the answer ({@code cutOffTime} does not). Bounded by
 * total shopping results + flights held ({@code SEARCH_CACHE_MAX_WEIGHT}), since one response can be large.
 * Cached responses are shared and must be treated as read-only.
 */
@Component
public class SearchResponseCache {

    private final Cache<String, SearchResponse> cache = Caffeine.newBuilder()
            .maximumWeight(SEARCH_CACHE_MAX_WEIGHT)
            .weigher((String _, SearchResponse response) -> 1 + response.getShoppingResultList().size() + response.getFlightList().size())
            .expireAfterWrite(Duration.ofSeconds(SEARCH_CACHE_TTL_SECONDS))
            .build();

    public Optional<SearchResponse> get(String key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    public void put(String key, SearchResponse response) {
        cache.put(key, response);
    }

    public static String keyOf(String vendorCode, SearchRequest request) {
        return new StringJoiner(PIPE)
                .add(trimToEmpty(vendorCode))
                .add(upper(request.getDepCity()))
                .add(upper(request.getArrCity()))
                .add(trimToEmpty(request.getFromDate()))
                .add(trimToEmpty(request.getRetDate()))
                .add(trimToEmpty(request.getTripType()))
                .add(String.valueOf(request.getAdultNumber()))
                .add(String.valueOf(request.getChildNumber()))
                .add(String.valueOf(request.getInfantNumber()))
                .add(defaultIfBlank(upper(request.getCabinClass()), DEFAULT_CABIN))
                .add(upper(request.getReturnTagPrice()))
                .add(trimToEmpty(request.getTag()))
                .toString();
    }

    private static String upper(String value) {
        return trimToEmpty(value).toUpperCase(Locale.ROOT);
    }
}
