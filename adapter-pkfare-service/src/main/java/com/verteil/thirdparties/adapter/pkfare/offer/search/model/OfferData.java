package com.verteil.thirdparties.adapter.pkfare.offer.search.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * What the adapter needs back from PKFARE to price a selected solution later (QTE). Serialized to JSON, Base64
 * encoded, and sent as {@code shoppingResultList[].data}; PKFARE returns it unchanged in the pricing request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OfferData(
        String offerId,
        String owner,
        String shoppingResponseId,
        List<String> offerItemIds,
        List<String> segmentKeys
) {
}
