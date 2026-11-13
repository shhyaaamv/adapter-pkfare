package com.verteil.thirdparties.adapter.pkfare.offer.qte.model;

import com.verteil.thirdparties.adapter.pkfare.offer.common.model.ItinerarySegment;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.SegmentDetails;

/**
 * One segment of the priced FlightPriceRS itinerary with everything resolved once for both the PKFARE
 * {@code QteSegment} and the offer data QTE mints for Order.
 *
 * @param rbd        PKFARE {@code rbd}: FareComponent FareBasis.RBD, else ClassOfService.Code, else the request segment's
 * @param cabinClass PKFARE {@code cabinClass}
 * @param fareBasis  fare basis of the first priced passenger type, else the SEARCH offer data's; null when unknown
 */
public record PricedSegment(
        ItinerarySegment itinerary,
        SegmentDetails details,
        String rbd,
        String cabinClass,
        String fareBasis
) {
    public String matchKey() {
        return details.matchKey();
    }
}
