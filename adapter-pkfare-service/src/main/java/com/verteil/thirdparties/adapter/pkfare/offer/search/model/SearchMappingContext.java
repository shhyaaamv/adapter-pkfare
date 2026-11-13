package com.verteil.thirdparties.adapter.pkfare.offer.search.model;

import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schemas.ndc16_1.AirShoppingRS;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.DataListsIndex;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PricingContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-response mapping state for AirShoppingRS -> SearchResponse.
 *
 * @param pricing       DataLists lookups and requested passenger types for the shared converters
 * @param metadata      Metadata lookups (ShoppingResponseIDs) for the offer data
 * @param flightRefNums segment key -> PKFARE {@code flightRefNum}, in first-seen order (1-based); also the
 *                      exact set of segments that go into {@code flightList}
 */
public record SearchMappingContext(
        SearchRequest request,
        AirShoppingRS response,
        PricingContext pricing,
        AirShoppingMetadataIndex metadata,
        boolean returnTagPrice,
        String requestedTag,
        Map<String, Integer> flightRefNums
) {
    public SearchMappingContext(SearchRequest request, AirShoppingRS response, boolean returnTagPrice, String requestedTag) {
        this(request, response, pricingContext(request, response), new AirShoppingMetadataIndex(response),
                returnTagPrice, requestedTag, new LinkedHashMap<>());
    }

    public DataListsIndex index() {
        return pricing.index();
    }

    public int flightRefNum(String segmentKey) {
        return flightRefNums.computeIfAbsent(segmentKey, _ -> flightRefNums.size() + 1);
    }

    private static PricingContext pricingContext(SearchRequest request, AirShoppingRS response) {
        DataListsIndex index = new DataListsIndex(Optional.ofNullable(response).map(AirShoppingRS::getDataLists).orElse(null));
        return new PricingContext(index,
                PricingContext.requestedTypes(request.getAdultNumber(), request.getChildNumber(), request.getInfantNumber()));
    }
}
