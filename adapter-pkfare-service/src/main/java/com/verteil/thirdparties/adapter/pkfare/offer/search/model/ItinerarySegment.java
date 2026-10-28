package com.verteil.thirdparties.adapter.pkfare.offer.search.model;

import com.verteil.schemas.ndc16_1.FlightSegmentReference;
import com.verteil.schemas.ndc16_1.ListOfFlightSegmentType;

import java.time.LocalDateTime;

/**
 * One flight segment of an offer, positioned the PKFARE way: {@code travelDirection} 1 outbound / 2 return,
 * {@code flightSeq} 1..n within that direction.
 *
 * @param reference the offer-level segment reference (class of service / cabin of this offer on this segment)
 */
public record ItinerarySegment(
        String segmentKey,
        ListOfFlightSegmentType segment,
        FlightSegmentReference reference,
        int travelDirection,
        int flightSeq,
        LocalDateTime departure
) {
}
