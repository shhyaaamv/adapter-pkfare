package com.verteil.thirdparties.adapter.pkfare.offer.search.model;

import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schemas.ndc16_1.AirShoppingRS;
import com.verteil.thirdparties.adapter.pkfare.common.model.CabinMapping;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-response mapping state for AirShoppingRS -> SearchResponse.
 *
 * @param cabinMapping  configured PKFARE <-> VDC cabin codes
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
        CabinMapping cabinMapping,
        Map<String, Integer> flightRefNums
) {
    public SearchMappingContext(SearchRequest request, AirShoppingRS response, boolean returnTagPrice, String requestedTag,
                                Integer defaultSeatCount, int ticketIssueTime, String defaultProductType, CabinMapping cabinMapping) {
        this(request, response, new AirShoppingRSIndex(response), returnTagPrice, requestedTag, defaultSeatCount,
                ticketIssueTime, defaultProductType, cabinMapping, new LinkedHashMap<>());
    }

    public int flightRefNum(String segmentKey) {
        return flightRefNums.computeIfAbsent(segmentKey, _ -> flightRefNums.size() + 1);
    }
}
