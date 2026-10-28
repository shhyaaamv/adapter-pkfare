package com.verteil.thirdparties.adapter.pkfare.offer.search.model;

import com.verteil.schemas.ndc16_1.PricedFlightOfferType;

/**
 * The OfferPrice that prices one PKFARE passenger type (0 adult, 1 child, 2 infant). VDC OfferPrice amounts are
 * per passenger.
 */
public record PassengerOfferPrice(int passengerType, PricedFlightOfferType.OfferPrice offerPrice) {
}
