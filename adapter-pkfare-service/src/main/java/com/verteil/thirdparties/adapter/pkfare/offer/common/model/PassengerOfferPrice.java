package com.verteil.thirdparties.adapter.pkfare.offer.common.model;

import com.verteil.schemas.ndc16_1.OfferPriceLeadType;

/**
 * The OfferPrice that prices one PKFARE passenger type (0 adult, 1 child, 2 infant). VDC OfferPrice amounts are
 * per passenger. Typed as the common base of AirShoppingRS and FlightPriceRS OfferPrices.
 */
public record PassengerOfferPrice(int passengerType, OfferPriceLeadType offerPrice) {
}
