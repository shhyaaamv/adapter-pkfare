package com.verteil.thirdparties.adapter.pkfare.offer.qte.model;

import com.verteil.schema.pkfare.QteRequest;
import com.verteil.schema.pkfare.QteSegment;
import com.verteil.schemas.ndc16_1.FlightPriceRS;
import com.verteil.schemas.ndc16_1.ItemIDType;
import com.verteil.schemas.ndc16_1.PricedFlightOffer;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.DataSegment;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.DataListsIndex;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PricingContext;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

/**
 * Per-response mapping state for FlightPriceRS -> QteResponse.
 *
 * @param shopData        the request's decoded {@code routing.data}; may be null
 * @param offer           the PricedFlightOffer being mapped (the first one)
 * @param pricing         DataLists lookups and requested passenger types for the shared converters
 * @param requestSegments the request's segments by match key
 */
public record QteMappingContext(
        QteRequest request,
        OfferData shopData,
        FlightPriceRS response,
        PricedFlightOffer offer,
        PricingContext pricing,
        Map<String, QteSegment> requestSegments
) {
    public DataListsIndex index() {
        return pricing.index();
    }

    public Optional<QteSegment> requestSegment(String matchKey) {
        return Optional.ofNullable(requestSegments.get(matchKey));
    }

    public Optional<DataSegment> dataSegment(String matchKey) {
        return Optional.ofNullable(shopData).flatMap(offerData -> offerData.segment(matchKey));
    }

    public String offerId() {
        return Optional.ofNullable(offer.getOfferID())
                .map(ItemIDType::getValue)
                .orElse(null);
    }

    public int seatedPassengers() {
        return requireNonNullElse(request.getAdultNumber(), 0) + requireNonNullElse(request.getChildNumber(), 0);
    }
}
