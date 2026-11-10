package com.verteil.thirdparties.adapter.pkfare.offer.search.model;

import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schemas.ndc16_1.AirShoppingRS;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-response mapping state for AirShoppingRS -> SearchResponse.
 *
 * @param flightRefNums segment key -> PKFARE {@code flightRefNum}, in first-seen order (1-based); also the
 *                      exact set of segments that go into {@code flightList}
 */
public record SearchMappingContext(
        SearchRequest request,
        AirShoppingRS response,
        AirShoppingRSIndex index,
        boolean returnTagPrice,
        String requestedTag,
        Integer defaultSeatCount,
        int ticketIssueTime,
        String defaultProductType,
        Map<String, Integer> flightRefNums
) {
    public SearchMappingContext(SearchRequest request, AirShoppingRS response, boolean returnTagPrice, String requestedTag,
                                Integer defaultSeatCount, int ticketIssueTime, String defaultProductType) {
        this(request, response, new AirShoppingRSIndex(response), returnTagPrice, requestedTag, defaultSeatCount,
                ticketIssueTime, defaultProductType, new LinkedHashMap<>());
    }

    public int flightRefNum(String segmentKey) {
        return flightRefNums.computeIfAbsent(segmentKey, _ -> flightRefNums.size() + 1);
    }
}
