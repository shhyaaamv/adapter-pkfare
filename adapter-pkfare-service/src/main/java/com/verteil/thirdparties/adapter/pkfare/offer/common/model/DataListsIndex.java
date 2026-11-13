package com.verteil.thirdparties.adapter.pkfare.offer.common.model;

import com.verteil.schemas.ndc16_1.DataListType;
import com.verteil.schemas.ndc16_1.FareComponentType;
import com.verteil.schemas.ndc16_1.FareList;
import com.verteil.schemas.ndc16_1.FlightCOSCoreType;
import com.verteil.schemas.ndc16_1.FlightSegmentReference;
import com.verteil.schemas.ndc16_1.ListOfBagAllowanceType;
import com.verteil.schemas.ndc16_1.ListOfCarryOnAllowanceType;
import com.verteil.schemas.ndc16_1.ListOfFlightSegmentType;
import com.verteil.schemas.ndc16_1.OfferPriceLeadType;
import com.verteil.schemas.ndc16_1.PenaltyType;
import com.verteil.schemas.ndc16_1.PriceClassType;
import com.verteil.schemas.ndc16_1.SegmentReference;
import com.verteil.thirdparties.adapter.pkfare.common.utils.RefUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;

/**
 * Key lookups over a response's DataLists (AirShoppingRS or FlightPriceRS), built once per response so offer
 * mapping never scans lists.
 */
public final class DataListsIndex {

    private final Map<String, ListOfFlightSegmentType> segments = new HashMap<>();
    private final Map<String, String> travelerPtcs = new HashMap<>();
    private final Map<String, FareList.FareGroup> fareGroups = new HashMap<>();
    private final Map<String, PenaltyType> penalties = new HashMap<>();
    private final Map<String, ListOfBagAllowanceType.CheckedBagAllowance> checkedBags = new HashMap<>();
    private final Map<String, ListOfCarryOnAllowanceType.CarryOnAllowance> carryOns = new HashMap<>();
    private final Map<String, PriceClassType> priceClasses = new HashMap<>();
    private final Map<String, String> segmentOriginDestinations = new HashMap<>();

    public DataListsIndex(DataListType dataLists) {
        Optional<DataListType> lists = Optional.ofNullable(dataLists);
        lists.map(DataListType::getFlightSegmentList)
                .ifPresent(list -> index(segments, list.getFlightSegment(), ListOfFlightSegmentType::getSegmentKey));
        lists.map(DataListType::getAnonymousTravelerList)
                .ifPresent(list -> list.getAnonymousTraveler().stream()
                        .filter(traveler -> nonNull(traveler.getObjectKey()) && nonNull(traveler.getPTC()))
                        .forEach(traveler -> travelerPtcs.put(traveler.getObjectKey(), traveler.getPTC().getValue())));
        lists.map(DataListType::getFareList)
                .ifPresent(list -> index(fareGroups, list.getFareGroup(), FareList.FareGroup::getListKey));
        lists.map(DataListType::getPenaltyList)
                .ifPresent(list -> index(penalties, list.getPenalty(), PenaltyType::getObjectKey));
        lists.map(DataListType::getCheckedBagAllowanceList)
                .ifPresent(list -> index(checkedBags, list.getCheckedBagAllowance(), ListOfBagAllowanceType.CheckedBagAllowance::getListKey));
        lists.map(DataListType::getCarryOnAllowanceList)
                .ifPresent(list -> index(carryOns, list.getCarryOnAllowance(), ListOfCarryOnAllowanceType.CarryOnAllowance::getListKey));
        lists.map(DataListType::getPriceClassList)
                .ifPresent(list -> index(priceClasses, list.getPriceClass(), PriceClassType::getObjectKey));
        lists.ifPresent(this::indexSegmentOriginDestinations);
    }

    public Optional<ListOfFlightSegmentType> segment(String key) {
        return Optional.ofNullable(key).map(segments::get);
    }

    public Optional<String> travelerPtc(String travelerKey) {
        return Optional.ofNullable(travelerKey).map(travelerPtcs::get);
    }

    public Optional<FareList.FareGroup> fareGroup(String key) {
        return Optional.ofNullable(key).map(fareGroups::get);
    }

    public Optional<PenaltyType> penalty(String key) {
        return Optional.ofNullable(key).map(penalties::get);
    }

    public Optional<ListOfBagAllowanceType.CheckedBagAllowance> checkedBag(String key) {
        return Optional.ofNullable(key).map(checkedBags::get);
    }

    public Optional<ListOfCarryOnAllowanceType.CarryOnAllowance> carryOn(String key) {
        return Optional.ofNullable(key).map(carryOns::get);
    }

    public Optional<PriceClassType> priceClass(String key) {
        return Optional.ofNullable(key).map(priceClasses::get);
    }

    /** OriginDestination key a segment belongs to, via OriginDestinationList -> FlightList -> SegmentReferences. */
    public Optional<String> originDestinationOfSegment(String segmentKey) {
        return Optional.ofNullable(segmentKey).map(segmentOriginDestinations::get);
    }

    /**
     * Segment a fare component prices: its {@code SegmentReference} (AirShoppingRS style), else the first of its
     * {@code refs} that is an indexed segment (FlightPriceRS style).
     */
    public Optional<String> segmentKeyOf(FareComponentType component) {
        return Optional.ofNullable(component.getSegmentReference())
                .map(SegmentReference::getValue)
                .map(RefUtil::key)
                .or(() -> RefUtil.keys(component.getRefs()).stream()
                        .filter(segments::containsKey)
                        .findFirst());
    }

    /**
     * Fare groups an OfferPrice refers to, from its fare components' {@code refs} and its segment references'
     * {@code ClassOfService.refs}; distinct, in that order, unknown keys dropped.
     */
    public Stream<FareList.FareGroup> fareGroups(OfferPriceLeadType offerPrice) {
        return Stream.concat(fareComponentRefs(offerPrice), classOfServiceRefs(offerPrice))
                .distinct()
                .flatMap(key -> fareGroup(key).stream());
    }

    private static Stream<String> fareComponentRefs(OfferPriceLeadType offerPrice) {
        return Optional.ofNullable(offerPrice.getFareDetail()).stream()
                .flatMap(fareDetail -> fareDetail.getFareComponent().stream())
                .flatMap(component -> RefUtil.keys(component.getRefs()).stream());
    }

    private static Stream<String> classOfServiceRefs(OfferPriceLeadType offerPrice) {
        return Optional.ofNullable(offerPrice.getRequestedDate()).stream()
                .flatMap(requestedDate -> requestedDate.getAssociations().stream())
                .flatMap(association -> Optional.ofNullable(association.getApplicableFlight()).stream())
                .flatMap(applicableFlight -> applicableFlight.getFlightSegmentReference().stream())
                .flatMap(reference -> Optional.ofNullable(reference)
                        .map(FlightSegmentReference::getClassOfService)
                        .map(FlightCOSCoreType::getRefs)
                        .stream())
                .flatMap(refs -> RefUtil.keys(refs).stream());
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
