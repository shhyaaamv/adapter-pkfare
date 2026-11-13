package com.verteil.thirdparties.adapter.pkfare.offer.qte.converter;

import com.verteil.schema.pkfare.QteRequest;
import com.verteil.schema.pkfare.QteRequestRouting;
import com.verteil.schema.pkfare.QteSegment;
import com.verteil.schemas.ndc16_1.AircraftCode;
import com.verteil.schemas.ndc16_1.AircraftSummaryType;
import com.verteil.schemas.ndc16_1.AirlineID;
import com.verteil.schemas.ndc16_1.AnonymousTravelerList;
import com.verteil.schemas.ndc16_1.AnonymousTravelerType;
import com.verteil.schemas.ndc16_1.CabinType;
import com.verteil.schemas.ndc16_1.Departure;
import com.verteil.schemas.ndc16_1.FareList;
import com.verteil.schemas.ndc16_1.FlightArrivalType;
import com.verteil.schemas.ndc16_1.FlightCOSCoreType;
import com.verteil.schemas.ndc16_1.FlightDepartureType;
import com.verteil.schemas.ndc16_1.FlightNumber;
import com.verteil.schemas.ndc16_1.FlightPriceRQ;
import com.verteil.schemas.ndc16_1.FlightType;
import com.verteil.schemas.ndc16_1.ItemIDType;
import com.verteil.schemas.ndc16_1.MarketingCarrierFlightType;
import com.verteil.schemas.ndc16_1.ShoppingResponseIDType;
import com.verteil.schemas.ndc16_1.TravelerCoreType;
import com.verteil.schemas.ndc16_1.Travelers;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.common.model.CabinMapping;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.DataPassenger;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.DataSegment;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.SegmentMatchKey;
import com.verteil.thirdparties.adapter.pkfare.offer.qte.model.RequestedSegment;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.INTERNAL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VALIDATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_INVALID_ROUTING_DATA;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.INVALID_DATE_RANGE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.MSG_INVALID_DATE_RANGE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_DATE_TIME_FORMAT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PROCESS_FAILURE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PTC_ADULT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PTC_CHILD;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PTC_CHILD_ALT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PTC_INFANT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.TRIP_TYPE_ONE_WAY;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.TRIP_TYPE_ROUND_TRIP;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VALIDATION_FAILURE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_CHANNEL;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * PKFARE QTE request + the SEARCH {@link OfferData} it carries in {@code routing.data} -> VDC FlightPriceRQ.
 * <pre>
 * adultNumber/childNumber/infantNumber  -> Travelers.Traveler[].AnonymousTraveler.PTC (one traveler per passenger)
 * data shoppingResponseId               -> ShoppingResponseID
 * fromSegments / retSegments            -> Query.OriginDestination[0] / [1].Flight[]: times, carriers, aircraft,
 *                                          cabinClass (CabinType.Code via CabinMapping.DEFAULT) and rbd
 *                                          (ClassOfService.Code) from the request; SegmentKey from the offer
 *                                          data segment with the same {@link SegmentMatchKey}
 * data offer / passengers               -> Query.Offers.Offer[0] (OfferID with Channel {@code VDC_CHANNEL},
 *                                          OfferItemIDs + traveler refs)
 * data segment fare basis               -> DataLists.FareList.FareGroup FG&lt;n&gt;, referenced by ClassOfService.refs
 * data passengers                       -> DataLists.AnonymousTravelerList
 * </pre>
 * No Metadata is sent. Validation happens first, all as VALIDATION_FAILURE (PKFARE status 4), except duplicate
 * segments, a segment departing before the previous one arrives, or segments in another order than the offer data's,
 * which carry reason {@code INVALID_DATE_RANGE} (status 111). The date-range check runs before the segment match,
 * so out-of-order times are reported as such.
 */
@Component
public class QteRQConverter {

    private static final DateTimeFormatter PK_DATE_TIME = DateTimeFormatter.ofPattern(PK_DATE_TIME_FORMAT.replace("yyyy", "uuuu"))
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter NDC_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_PASSENGERS_PER_TYPE = 9;
    private static final String DEP_TIME_FIELD = "depTime";
    private static final String ARR_TIME_FIELD = "arrTime";

    private final DatatypeFactory datatypeFactory;

    public QteRQConverter() {
        try {
            this.datatypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new PkfareAdapterException(INTERNAL_ERROR.getCode(), "No XML DatatypeFactory available", e, PROCESS_FAILURE_REASON_CODE);
        }
    }

    public FlightPriceRQ convert(PkfareAdapterContext context) {
        QteRequest request = (QteRequest) context.getRequest();
        OfferData offerData = context.getOfferData();
        validateRequest(request);
        List<QteSegment> itinerary = mapItinerary(request.getRouting());
        validateDateRange(itinerary);
        validateOfferData(offerData);
        validatePassengers(request, offerData);
        int outboundCount = segments(request.getRouting().getFromSegments()).size();
        List<RequestedSegment> requestedSegments = matchSegments(itinerary, offerData);
        validateSegmentOrder(itinerary, offerData);

        FlightPriceRQ flightPriceRQ = new FlightPriceRQ();
        flightPriceRQ.setTravelers(mapTravelers(request));
        flightPriceRQ.setShoppingResponseID(mapShoppingResponseId(offerData));
        flightPriceRQ.setQuery(mapQuery(requestedSegments, outboundCount, offerData));
        flightPriceRQ.setDataLists(mapDataLists(requestedSegments, offerData));
        return flightPriceRQ;
    }

    /* ------------------------------------------------------------------------
     * VALIDATION
     * --------------------------------------------------------------------- */

    private static void validateRequest(QteRequest request) {
        if (isNull(request)) {
            throw validationError("Empty QTE request");
        }
        validatePassengerCounts(request);
        validateTrip(request);
        mapItinerary(request.getRouting()).forEach(QteRQConverter::validateSegment);
    }

    private static void validatePassengerCounts(QteRequest request) {
        int adults = request.getAdultNumber();
        int children = request.getChildNumber();
        int infants = request.getInfantNumber();
        if (adults < 1 || adults > MAX_PASSENGERS_PER_TYPE) {
            throw validationError("adultNumber must be between 1 and 9");
        }
        if (children < 0 || children > MAX_PASSENGERS_PER_TYPE || infants < 0 || infants > MAX_PASSENGERS_PER_TYPE) {
            throw validationError("childNumber/infantNumber must be between 0 and 9");
        }
        if (infants > adults) {
            throw validationError("infantNumber cannot exceed adultNumber");
        }
    }

    private static void validateTrip(QteRequest request) {
        String tripType = trimToEmpty(request.getTripType());
        if (!TRIP_TYPE_ONE_WAY.equals(tripType) && !TRIP_TYPE_ROUND_TRIP.equals(tripType)) {
            throw validationError("tripType must be 1 (one-way) or 2 (round-trip)");
        }
        if (isNull(request.getRouting()) || isNullOrEmpty(request.getRouting().getFromSegments())) {
            throw validationError("routing.fromSegments is required");
        }
        boolean hasReturn = !isNullOrEmpty(request.getRouting().getRetSegments());
        if (TRIP_TYPE_ROUND_TRIP.equals(tripType) && !hasReturn) {
            throw validationError("routing.retSegments is required for tripType 2");
        }
        if (TRIP_TYPE_ONE_WAY.equals(tripType) && hasReturn) {
            throw validationError("routing.retSegments must be empty for tripType 1");
        }
    }

    private static void validateSegment(QteSegment segment) {
        if (isNull(segment)) {
            throw validationError("routing segment is required");
        }
        boolean missingField = Stream.of(segment.getDepAirport(), segment.getArrAirport(), segment.getDepTime(),
                segment.getArrTime(), segment.getMarketingCarrier(), segment.getRbd()).anyMatch(StringUtils::isBlank);
        if (missingField || isNull(segment.getFlightNumber())) {
            throw validationError("Segment depAirport, arrAirport, depTime, arrTime, "
                    + "marketingCarrier, flightNumber and rbd are required");
        }
        parseDateTime(segment.getDepTime(), DEP_TIME_FIELD);
        parseDateTime(segment.getArrTime(), ARR_TIME_FIELD);
        if (isNotBlank(segment.getCabinClass()) && CabinMapping.DEFAULT.toVdc(segment.getCabinClass()).isEmpty()) {
            throw validationError("cabinClass must be one of " + String.join(", ", CabinMapping.DEFAULT.pkToVdc().keySet()));
        }
    }

    /**
     * Duplicate segments, or a segment departing before the previous one (fromSegments then retSegments) arrives
     * at the same airport. Arrival/departure times are local, so only times at a shared airport are compared.
     */
    private static void validateDateRange(List<QteSegment> itinerary) {
        long distinctSegments = itinerary.stream().map(QteRQConverter::mapMatchKey).distinct().count();
        boolean outOfOrder = IntStream.range(1, itinerary.size())
                .anyMatch(index -> departsBeforeArrival(itinerary.get(index - 1), itinerary.get(index)));
        if (distinctSegments != itinerary.size() || outOfOrder) {
            throw dateRangeError();
        }
    }

    /**
     * The offer data keeps SEARCH's itinerary order (outbound then return, each by flightSeq); fromSegments then
     * retSegments must list the same segments in that order, else they are out of order (status 111). Run after
     * {@link #matchSegments}, so both hold the same segments.
     */
    private static void validateSegmentOrder(List<QteSegment> itinerary, OfferData offerData) {
        List<String> dataOrder = offerData.segments().keySet().stream()
                .map(QteRQConverter::normalise)
                .toList();
        if (!dataOrder.equals(itinerary.stream().map(QteRQConverter::mapMatchKey).toList())) {
            throw dateRangeError();
        }
    }

    private static boolean departsBeforeArrival(QteSegment previous, QteSegment next) {
        return normalise(previous.getArrAirport()).equals(normalise(next.getDepAirport()))
                && parseDateTime(next.getDepTime(), DEP_TIME_FIELD)
                        .isBefore(parseDateTime(previous.getArrTime(), ARR_TIME_FIELD));
    }

    /** The offer data fields FlightPriceRQ cannot do without; the codec only checks the format. */
    private static void validateOfferData(OfferData offerData) {
        if (isNull(offerData)) {
            throw validationError(ERROR_MSG_INVALID_ROUTING_DATA);
        }
        boolean complete = hasShoppingResponseId(offerData) && hasOffer(offerData) && hasPassengers(offerData)
                && nonNull(offerData.segments());
        if (!complete) {
            throw validationError(ERROR_MSG_INVALID_ROUTING_DATA);
        }
    }

    private static boolean hasShoppingResponseId(OfferData offerData) {
        return nonNull(offerData.shoppingResponseId()) && isNotBlank(offerData.shoppingResponseId().value());
    }

    private static boolean hasOffer(OfferData offerData) {
        return nonNull(offerData.offer()) && isNotBlank(offerData.offer().id())
                && isNotBlank(offerData.offer().owner());
    }

    private static boolean hasPassengers(OfferData offerData) {
        return !isNullOrEmpty(offerData.passengers()) && offerData.passengers().stream()
                .noneMatch(passenger -> isNull(passenger) || isBlank(passenger.offerItemId()));
    }

    /** Passengers per PTC in the request must be the travelers the offer was shopped for (CHD and CNN are the same). */
    private static void validatePassengers(QteRequest request, OfferData offerData) {
        if (!mapRequestedCounts(request).equals(mapShoppedCounts(offerData))) {
            throw validationError("Passenger numbers do not match routing.data");
        }
    }

    private static Map<String, Long> mapRequestedCounts(QteRequest request) {
        return Stream.of(
                        Map.entry(PTC_ADULT, (long) requireNonNullElse(request.getAdultNumber(), 0)),
                        Map.entry(PTC_CHILD, (long) requireNonNullElse(request.getChildNumber(), 0)),
                        Map.entry(PTC_INFANT, (long) requireNonNullElse(request.getInfantNumber(), 0)))
                .filter(entry -> entry.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<String, Long> mapShoppedCounts(OfferData offerData) {
        Map<String, Long> shopped = offerData.passengers().stream()
                .collect(Collectors.groupingBy(passenger -> mapPtc(passenger.ptc()),
                        Collectors.summingLong(passenger -> mapTravelerRefs(passenger).size())));
        shopped.values().removeIf(count -> count == 0);
        return shopped;
    }

    /**
     * Pairs every requested segment, in request order, with the offer data segment of the same match key;
     * the offer data must hold exactly the requested segments.
     */
    private static List<RequestedSegment> matchSegments(List<QteSegment> itinerary, OfferData offerData) {
        if (offerData.segments().size() != itinerary.size()) {
            throw validationError("Segments do not match routing.data");
        }
        Map<String, DataSegment> byMatchKey = mapDataSegmentsByMatchKey(offerData);
        return IntStream.range(0, itinerary.size())
                .mapToObj(index -> mapRequestedSegment(index, itinerary.get(index), byMatchKey))
                .toList();
    }

    private static Map<String, DataSegment> mapDataSegmentsByMatchKey(OfferData offerData) {
        return offerData.segments().keySet().stream()
                .filter(StringUtils::isNotBlank)
                .flatMap(matchKey -> offerData.segment(matchKey).stream()
                        .map(dataSegment -> Map.entry(normalise(matchKey), dataSegment)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, _) -> first));
    }

    private static RequestedSegment mapRequestedSegment(int index, QteSegment segment,
                                                        Map<String, DataSegment> byMatchKey) {
        String matchKey = mapMatchKey(segment);
        DataSegment dataSegment = Optional.ofNullable(byMatchKey.get(matchKey))
                .orElseThrow(() -> validationError("Unknown segment " + matchKey));
        return new RequestedSegment(index, segment, dataSegment);
    }

    /* ------------------------------------------------------------------------
     * TRAVELERS, SHOPPING RESPONSE
     * --------------------------------------------------------------------- */

    private static Travelers mapTravelers(QteRequest request) {
        Travelers travelers = new Travelers();
        Stream.of(
                        mapTravelersOf(PTC_ADULT, requireNonNullElse(request.getAdultNumber(), 0)),
                        mapTravelersOf(PTC_CHILD, requireNonNullElse(request.getChildNumber(), 0)),
                        mapTravelersOf(PTC_INFANT, requireNonNullElse(request.getInfantNumber(), 0)))
                .flatMap(Function.identity())
                .forEach(travelers.getTraveler()::add);
        return travelers;
    }

    private static Stream<Travelers.Traveler> mapTravelersOf(String ptc, int count) {
        return IntStream.range(0, count).mapToObj(_ -> mapTraveler(ptc));
    }

    private static Travelers.Traveler mapTraveler(String ptc) {
        Travelers.Traveler traveler = new Travelers.Traveler();
        traveler.getAnonymousTraveler().add(mapAnonymousTraveler(null, ptc));
        return traveler;
    }

    private static AnonymousTravelerType mapAnonymousTraveler(String objectKey, String ptc) {
        TravelerCoreType.PTC travelerPtc = new TravelerCoreType.PTC();
        travelerPtc.setValue(ptc);
        AnonymousTravelerType anonymousTraveler = new AnonymousTravelerType();
        anonymousTraveler.setObjectKey(objectKey);
        anonymousTraveler.setPTC(travelerPtc);
        return anonymousTraveler;
    }

    private static ShoppingResponseIDType mapShoppingResponseId(OfferData offerData) {
        ShoppingResponseIDType.ResponseID responseId = new ShoppingResponseIDType.ResponseID();
        responseId.setValue(offerData.shoppingResponseId().value());
        ShoppingResponseIDType shoppingResponseId = new ShoppingResponseIDType();
        shoppingResponseId.setOwner(offerData.shoppingResponseId().owner());
        shoppingResponseId.setResponseID(responseId);
        return shoppingResponseId;
    }

    /* ------------------------------------------------------------------------
     * QUERY
     * --------------------------------------------------------------------- */

    private FlightPriceRQ.Query mapQuery(List<RequestedSegment> requestedSegments, int outboundCount,
                                         OfferData offerData) {
        FlightPriceRQ.Query query = new FlightPriceRQ.Query();
        query.getOriginDestination().add(mapOriginDestination(requestedSegments.subList(0, outboundCount)));
        if (requestedSegments.size() > outboundCount) {
            query.getOriginDestination().add(mapOriginDestination(
                    requestedSegments.subList(outboundCount, requestedSegments.size())));
        }
        query.setOffers(mapOffers(offerData));
        return query;
    }

    private FlightPriceRQ.Query.OriginDestination mapOriginDestination(List<RequestedSegment> requestedSegments) {
        FlightPriceRQ.Query.OriginDestination originDestination = new FlightPriceRQ.Query.OriginDestination();
        requestedSegments.stream()
                .map(this::mapFlight)
                .forEach(originDestination.getFlight()::add);
        return originDestination;
    }

    private FlightType.Flight mapFlight(RequestedSegment requestedSegment) {
        QteSegment segment = requestedSegment.segment();
        FlightType.Flight flight = new FlightType.Flight();
        flight.setSegmentKey(requestedSegment.dataSegment().segmentKey());
        flight.setDeparture(mapDeparture(segment));
        flight.setArrival(mapArrival(segment));
        flight.setMarketingCarrier(mapMarketingCarrier(segment));
        mapOperatingCarrier(segment).ifPresent(flight::setOperatingCarrier);
        mapEquipment(segment).ifPresent(flight::setEquipment);
        mapCabinType(segment).ifPresent(flight::setCabinType);
        flight.setClassOfService(mapClassOfService(requestedSegment));
        return flight;
    }

    private Departure mapDeparture(QteSegment segment) {
        LocalDateTime dateTime = parseDateTime(segment.getDepTime(), DEP_TIME_FIELD);
        FlightDepartureType.AirportCode airportCode = new FlightDepartureType.AirportCode();
        airportCode.setValue(normalise(segment.getDepAirport()));
        Departure departure = new Departure();
        departure.setAirportCode(airportCode);
        departure.setDate(toXmlDateTime(dateTime));
        departure.setTime(dateTime.format(NDC_TIME));
        return departure;
    }

    private FlightArrivalType mapArrival(QteSegment segment) {
        LocalDateTime dateTime = parseDateTime(segment.getArrTime(), ARR_TIME_FIELD);
        FlightArrivalType.AirportCode airportCode = new FlightArrivalType.AirportCode();
        airportCode.setValue(normalise(segment.getArrAirport()));
        FlightArrivalType arrival = new FlightArrivalType();
        arrival.setAirportCode(airportCode);
        arrival.setDate(toXmlDateTime(dateTime));
        arrival.setTime(dateTime.format(NDC_TIME));
        return arrival;
    }

    private static MarketingCarrierFlightType mapMarketingCarrier(QteSegment segment) {
        FlightNumber flightNumber = new FlightNumber();
        flightNumber.setValue(String.valueOf(segment.getFlightNumber()));
        MarketingCarrierFlightType marketingCarrier = new MarketingCarrierFlightType();
        marketingCarrier.setAirlineID(mapAirlineId(segment.getMarketingCarrier()));
        marketingCarrier.setFlightNumber(flightNumber);
        return marketingCarrier;
    }

    /** The echoed operating carrier; left out when the request has none. */
    private static Optional<FlightType.Flight.OperatingCarrier> mapOperatingCarrier(QteSegment segment) {
        return Optional.ofNullable(segment.getOperatingCarrier())
                .filter(StringUtils::isNotBlank)
                .map(QteRQConverter::mapOperatingCarrierOf);
    }

    private static FlightType.Flight.OperatingCarrier mapOperatingCarrierOf(String carrier) {
        FlightType.Flight.OperatingCarrier operatingCarrier = new FlightType.Flight.OperatingCarrier();
        operatingCarrier.setAirlineID(mapAirlineId(carrier));
        return operatingCarrier;
    }

    private static Optional<AircraftSummaryType> mapEquipment(QteSegment segment) {
        return Optional.ofNullable(segment.getAircraftCode())
                .filter(StringUtils::isNotBlank)
                .map(QteRQConverter::mapAircraftSummary);
    }

    private static AircraftSummaryType mapAircraftSummary(String code) {
        AircraftCode aircraftCode = new AircraftCode();
        aircraftCode.setValue(code.trim());
        AircraftSummaryType equipment = new AircraftSummaryType();
        equipment.setAircraftCode(aircraftCode);
        return equipment;
    }

    /** Code only: the requested PKFARE cabin mapped to VDC (validated); left out when the request has none. */
    private static Optional<CabinType> mapCabinType(QteSegment segment) {
        return Optional.ofNullable(segment.getCabinClass())
                .filter(StringUtils::isNotBlank)
                .flatMap(CabinMapping.DEFAULT::toVdc)
                .map(QteRQConverter::mapCabinTypeOf);
    }

    private static CabinType mapCabinTypeOf(String code) {
        CabinType cabinType = new CabinType();
        cabinType.setCode(code);
        return cabinType;
    }

    private static FlightCOSCoreType mapClassOfService(RequestedSegment requestedSegment) {
        FlightCOSCoreType.Code code = new FlightCOSCoreType.Code();
        code.setValue(requestedSegment.bookingClass());
        FlightCOSCoreType classOfService = new FlightCOSCoreType();
        classOfService.setCode(code);
        if (requestedSegment.hasFareGroup()) {
            classOfService.getRefs().add(requestedSegment.fareGroupKey());
        }
        return classOfService;
    }

    private static FlightPriceRQ.Query.Offers mapOffers(OfferData offerData) {
        FlightPriceRQ.Query.Offers.Offer offer = new FlightPriceRQ.Query.Offers.Offer();
        offer.setOfferID(mapOfferId(offerData.offer()));
        offer.setOfferItemIDs(mapOfferItemIds(offerData.passengers()));
        FlightPriceRQ.Query.Offers offers = new FlightPriceRQ.Query.Offers();
        offers.getOffer().add(offer);
        return offers;
    }

    private static ItemIDType mapOfferId(OfferData.OfferRef offerRef) {
        ItemIDType offerId = new ItemIDType();
        offerId.setValue(offerRef.id());
        offerId.setOwner(offerRef.owner());
        offerId.setChannel(VDC_CHANNEL);
        return offerId;
    }

    private static FlightPriceRQ.Query.Offers.Offer.OfferItemIDs mapOfferItemIds(List<DataPassenger> passengers) {
        FlightPriceRQ.Query.Offers.Offer.OfferItemIDs offerItemIds = new FlightPriceRQ.Query.Offers.Offer.OfferItemIDs();
        passengers.stream()
                .map(QteRQConverter::mapOfferItemId)
                .forEach(offerItemIds.getOfferItemID()::add);
        return offerItemIds;
    }

    private static ItemIDType mapOfferItemId(DataPassenger passenger) {
        ItemIDType offerItemId = new ItemIDType();
        offerItemId.setValue(passenger.offerItemId());
        offerItemId.getRefs().addAll(mapTravelerRefs(passenger));
        return offerItemId;
    }

    /* ------------------------------------------------------------------------
     * DATA LISTS
     * --------------------------------------------------------------------- */

    private static FlightPriceRQ.DataLists mapDataLists(List<RequestedSegment> requestedSegments, OfferData offerData) {
        FlightPriceRQ.DataLists dataLists = new FlightPriceRQ.DataLists();
        mapFareList(requestedSegments).ifPresent(dataLists::setFareList);
        dataLists.setAnonymousTravelerList(mapAnonymousTravelerList(offerData.passengers()));
        return dataLists;
    }

    private static Optional<FareList> mapFareList(List<RequestedSegment> requestedSegments) {
        List<FareList.FareGroup> fareGroups = requestedSegments.stream()
                .filter(RequestedSegment::hasFareGroup)
                .map(QteRQConverter::mapFareGroup)
                .toList();
        if (fareGroups.isEmpty()) {
            return Optional.empty();
        }
        FareList fareList = new FareList();
        fareList.getFareGroup().addAll(fareGroups);
        return Optional.of(fareList);
    }

    private static FareList.FareGroup mapFareGroup(RequestedSegment requestedSegment) {
        FareList.FareGroup.FareBasisCode fareBasisCode = new FareList.FareGroup.FareBasisCode();
        fareBasisCode.setCode(requestedSegment.dataSegment().fareBasis().trim());
        FareList.FareGroup fareGroup = new FareList.FareGroup();
        fareGroup.setListKey(requestedSegment.fareGroupKey());
        fareGroup.setFareBasisCode(fareBasisCode);
        return fareGroup;
    }

    private static AnonymousTravelerList mapAnonymousTravelerList(List<DataPassenger> passengers) {
        AnonymousTravelerList anonymousTravelerList = new AnonymousTravelerList();
        passengers.stream()
                .flatMap(passenger -> mapTravelerRefs(passenger).stream()
                        .map(travelerRef -> mapAnonymousTraveler(travelerRef, passenger.ptc())))
                .forEach(anonymousTravelerList.getAnonymousTraveler()::add);
        return anonymousTravelerList;
    }

    /* ------------------------------------------------------------------------
     * HELPERS
     * --------------------------------------------------------------------- */

    /** fromSegments then retSegments. */
    private static List<QteSegment> mapItinerary(QteRequestRouting routing) {
        return Stream.concat(segments(routing.getFromSegments()).stream(), segments(routing.getRetSegments()).stream())
                .toList();
    }

    private static List<QteSegment> segments(List<QteSegment> segments) {
        return Optional.ofNullable(segments).orElse(List.of());
    }

    private static String mapMatchKey(QteSegment segment) {
        return SegmentMatchKey.of(segment.getDepAirport(), segment.getDepTime(), segment.getArrAirport(),
                segment.getArrTime(), segment.getMarketingCarrier(), segment.getFlightNumber());
    }

    private static List<String> mapTravelerRefs(DataPassenger passenger) {
        return Optional.ofNullable(passenger.travelerRefs()).orElse(List.of()).stream()
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    /** Child PTC as the request counts it: VDC may price children as CNN. */
    private static String mapPtc(String ptc) {
        String code = normalise(ptc);
        return PTC_CHILD_ALT.equals(code) ? PTC_CHILD : code;
    }

    private static AirlineID mapAirlineId(String carrier) {
        AirlineID airlineId = new AirlineID();
        airlineId.setValue(normalise(carrier));
        return airlineId;
    }

    /** Date-time without time zone and with zero milliseconds, e.g. {@code 2026-11-18T07:50:00.000}. */
    private XMLGregorianCalendar toXmlDateTime(LocalDateTime dateTime) {
        return datatypeFactory.newXMLGregorianCalendar(dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth(),
                dateTime.getHour(), dateTime.getMinute(), 0, 0, DatatypeConstants.FIELD_UNDEFINED);
    }

    private static LocalDateTime parseDateTime(String value, String field) {
        try {
            return LocalDateTime.parse(trimToEmpty(value), PK_DATE_TIME);
        } catch (DateTimeParseException _) {
            throw validationError(field + " must be formatted as yyyyMMddHHmm");
        }
    }

    private static boolean isNullOrEmpty(Collection<?> values) {
        return isNull(values) || values.isEmpty();
    }

    private static String normalise(String value) {
        return trimToEmpty(value).toUpperCase(Locale.ROOT);
    }

    private static PkfareAdapterException dateRangeError() {
        return new PkfareAdapterException(VALIDATION_FAILURE.getCode(), MSG_INVALID_DATE_RANGE, INVALID_DATE_RANGE_REASON_CODE);
    }

    private static PkfareAdapterException validationError(String message) {
        return new PkfareAdapterException(VALIDATION_FAILURE.getCode(), message, VALIDATION_FAILURE_REASON_CODE);
    }
}
