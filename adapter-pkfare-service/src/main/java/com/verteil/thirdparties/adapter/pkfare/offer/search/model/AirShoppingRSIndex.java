package com.verteil.thirdparties.adapter.pkfare.offer.search.model;

import com.verteil.schemas.ndc16_1.AirShoppingRS;
import com.verteil.schemas.ndc16_1.DataListType;
import com.verteil.schemas.ndc16_1.FareList;
import com.verteil.schemas.ndc16_1.ListOfBagAllowanceType;
import com.verteil.schemas.ndc16_1.ListOfCarryOnAllowanceType;
import com.verteil.schemas.ndc16_1.ListOfFlightSegmentType;
import com.verteil.schemas.ndc16_1.PenaltyType;
import com.verteil.schemas.ndc16_1.PriceClassType;
import com.verteil.thirdparties.adapter.pkfare.common.utils.RefUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

/**
 * Key lookups over an AirShoppingRS DataLists, built once per response so offer mapping never scans lists.
 */
public final class AirShoppingRSIndex {

    private final Map<String, ListOfFlightSegmentType> segments = new HashMap<>();
    private final Map<String, String> travelerPtcs = new HashMap<>();
    private final Map<String, FareList.FareGroup> fareGroups = new HashMap<>();
    private final Map<String, PenaltyType> penalties = new HashMap<>();
    private final Map<String, ListOfBagAllowanceType.CheckedBagAllowance> checkedBags = new HashMap<>();
    private final Map<String, ListOfCarryOnAllowanceType.CarryOnAllowance> carryOns = new HashMap<>();
    private final Map<String, PriceClassType> priceClasses = new HashMap<>();
    private final Map<String, String> segmentOriginDestinations = new HashMap<>();

    public AirShoppingRSIndex(AirShoppingRS response) {
        Optional<DataListType> dataLists = Optional.ofNullable(response).map(AirShoppingRS::getDataLists);
        dataLists.map(DataListType::getFlightSegmentList)
                .ifPresent(list -> index(segments, list.getFlightSegment(), ListOfFlightSegmentType::getSegmentKey));
        dataLists.map(DataListType::getAnonymousTravelerList)
                .ifPresent(list -> list.getAnonymousTraveler().stream()
                        .filter(traveler -> nonNull(traveler.getObjectKey()) && nonNull(traveler.getPTC()))
                        .forEach(traveler -> travelerPtcs.put(traveler.getObjectKey(), traveler.getPTC().getValue())));
        dataLists.map(DataListType::getFareList)
                .ifPresent(list -> index(fareGroups, list.getFareGroup(), FareList.FareGroup::getListKey));
        dataLists.map(DataListType::getPenaltyList)
                .ifPresent(list -> index(penalties, list.getPenalty(), PenaltyType::getObjectKey));
        dataLists.map(DataListType::getCheckedBagAllowanceList)
                .ifPresent(list -> index(checkedBags, list.getCheckedBagAllowance(), ListOfBagAllowanceType.CheckedBagAllowance::getListKey));
        dataLists.map(DataListType::getCarryOnAllowanceList)
                .ifPresent(list -> index(carryOns, list.getCarryOnAllowance(), ListOfCarryOnAllowanceType.CarryOnAllowance::getListKey));
        dataLists.map(DataListType::getPriceClassList)
                .ifPresent(list -> index(priceClasses, list.getPriceClass(), PriceClassType::getObjectKey));
        dataLists.ifPresent(this::indexSegmentOriginDestinations);
    }

    public Optional<ListOfFlightSegmentType> segment(String key) {
        return Optional.ofNullable(segments.get(key));
    }

    public Optional<String> travelerPtc(String travelerKey) {
        return Optional.ofNullable(travelerPtcs.get(travelerKey));
    }

    public Optional<FareList.FareGroup> fareGroup(String key) {
        return Optional.ofNullable(fareGroups.get(key));
    }

    public Optional<PenaltyType> penalty(String key) {
        return Optional.ofNullable(penalties.get(key));
    }

    public Optional<ListOfBagAllowanceType.CheckedBagAllowance> checkedBag(String key) {
        return Optional.ofNullable(checkedBags.get(key));
    }

    public Optional<ListOfCarryOnAllowanceType.CarryOnAllowance> carryOn(String key) {
        return Optional.ofNullable(carryOns.get(key));
    }

    public Optional<PriceClassType> priceClass(String key) {
        return Optional.ofNullable(priceClasses.get(key));
    }

    /** OriginDestination key a segment belongs to, via OriginDestinationList -> FlightList -> SegmentReferences. */
    public Optional<String> originDestinationOfSegment(String segmentKey) {
        return Optional.ofNullable(segmentOriginDestinations.get(segmentKey));
    }

    private void indexSegmentOriginDestinations(DataListType dataLists) {
        Map<String, List<String>> flightSegments = Optional.ofNullable(dataLists.getFlightList()).stream()
                .flatMap(flightList -> flightList.getFlight().stream())
                .filter(flight -> nonNull(flight.getFlightKey()) && nonNull(flight.getSegmentReferences()))
                .collect(Collectors.toMap(flight -> flight.getFlightKey(), flight -> RefUtil.keys(flight.getSegmentReferences().getValue()),
                        (_, last) -> last));
        Optional.ofNullable(dataLists.getOriginDestinationList()).stream()
                .flatMap(originDestinationList -> originDestinationList.getOriginDestination().stream())
                .forEach(originDestination -> Optional.ofNullable(originDestination.getFlightReferences()).stream()
                        .flatMap(flightReferences -> RefUtil.keys(flightReferences.getValue()).stream())
                        .flatMap(flightKey -> flightSegments.getOrDefault(flightKey, List.of()).stream())
                        .forEach(segmentKey -> segmentOriginDestinations.putIfAbsent(segmentKey, originDestination.getOriginDestinationKey())));
    }

    private static <T> void index(Map<String, ? super T> target, Collection<? extends T> items, Function<T, String> keyOf) {
        Optional.ofNullable(items).stream()
                .flatMap(Collection::stream)
                .filter(item -> nonNull(keyOf.apply(item)))
                .forEach(item -> target.put(keyOf.apply(item), item));
    }
}
