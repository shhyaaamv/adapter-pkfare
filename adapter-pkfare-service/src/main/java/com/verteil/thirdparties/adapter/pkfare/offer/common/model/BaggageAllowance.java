package com.verteil.thirdparties.adapter.pkfare.offer.common.model;

import java.util.List;

/**
 * Free baggage of one passenger type on one itinerary segment, already in PKFARE terms
 * ({@code -1} not applicable, piece {@code 0} no baggage).
 *
 * @param carryOnSize [length, width, height] in cm, empty when the allowance does not carry all three
 */
public record BaggageAllowance(
        int travelDirection,
        int flightSeq,
        int passengerType,
        int baggagePiece,
        int baggageWeight,
        int carryOnPiece,
        int carryOnWeight,
        List<Integer> carryOnSize
) {
    public BaggageAllowance {
        carryOnSize = List.copyOf(carryOnSize);
    }
}
