package com.verteil.thirdparties.adapter.pkfare.offer.common.model;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_PASSENGER_ADULT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_PASSENGER_CHILD;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_PASSENGER_INFANT;
import static java.util.Objects.requireNonNullElse;

/**
 * What the shared offer converters need from a priced VDC response, independent of the PKFARE service.
 *
 * @param index                   DataLists lookups of the response
 * @param requestedPassengerTypes PKFARE passenger types (0 adult, 1 child, 2 infant) the request asked for
 */
public record PricingContext(DataListsIndex index, Set<Integer> requestedPassengerTypes) {

    private record PassengerCount(int passengerType, Integer count) {}

    /** PKFARE passenger types with a positive count, in adult/child/infant order. */
    public static Set<Integer> requestedTypes(Integer adults, Integer children, Integer infants) {
        return Stream.of(new PassengerCount(PK_PASSENGER_ADULT, adults), new PassengerCount(PK_PASSENGER_CHILD, children),
                        new PassengerCount(PK_PASSENGER_INFANT, infants))
                .filter(passengerCount -> requireNonNullElse(passengerCount.count(), 0) > 0)
                .map(PassengerCount::passengerType)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
