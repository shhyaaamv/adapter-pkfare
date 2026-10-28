package com.verteil.thirdparties.adapter.pkfare.offer.search.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schema.pkfare.SearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_CABIN;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PIPE;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * Short-lived, per-pod cache of successful SEARCH responses. PKFARE re-sends identical searches at a high rate and
 * prices are re-validated at QTE anyway, so a TTL of a few minutes (config {@code PKFARE_API.SEARCH_CACHE_TTL_SECONDS},
 * 0 = off) trades negligible staleness for far fewer Entry-Gate calls.
 * <p>
 * Keyed by vendor code + every request field that changes the answer ({@code cutOffTime} does not). Bounded by
 * total shopping results + flights held ({@code adapter.search-cache.max-weight}), since one response can be large.
 * Cached responses are shared and must be treated as read-only.
 */
@Component
public class SearchResponseCache {

    private record Entry(SearchResponse response, long ttlSeconds) {}

    private final Cache<String, Entry> cache;

    public SearchResponseCache(@Value("${adapter.search-cache.max-weight:50000}") long maxWeight) {
        this.cache = Caffeine.newBuilder()
                .maximumWeight(maxWeight)
                .weigher((String _, Entry entry) -> 1 + entry.response().getShoppingResultList().size() + entry.response().getFlightList().size())
                .expireAfter(new Expiry<String, Entry>() {
                    @Override
                    public long expireAfterCreate(String key, Entry entry, long currentTime) {
                        return TimeUnit.SECONDS.toNanos(entry.ttlSeconds());
                    }

                    @Override
                    public long expireAfterUpdate(String key, Entry entry, long currentTime, long currentDuration) {
                        return TimeUnit.SECONDS.toNanos(entry.ttlSeconds());
                    }

                    @Override
                    public long expireAfterRead(String key, Entry entry, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    public Optional<SearchResponse> get(String key) {
        return Optional.ofNullable(cache.getIfPresent(key)).map(Entry::response);
    }

    public void put(String key, SearchResponse response, long ttlSeconds) {
        if (ttlSeconds > 0 && Objects.nonNull(response)) {
            cache.put(key, new Entry(response, ttlSeconds));
        }
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
