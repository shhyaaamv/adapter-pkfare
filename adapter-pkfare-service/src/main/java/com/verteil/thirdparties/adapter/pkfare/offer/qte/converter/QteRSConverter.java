package com.verteil.thirdparties.adapter.pkfare.offer.qte.converter;

import com.verteil.schema.pkfare.QteChangeInfo;
import com.verteil.schema.pkfare.QteFreeBaggage;
import com.verteil.schema.pkfare.QteOtherFareRule;
import com.verteil.schema.pkfare.QtePrice;
import com.verteil.schema.pkfare.QteRefundInfo;
import com.verteil.schema.pkfare.QteRequest;
import com.verteil.schema.pkfare.QteRequestRouting;
import com.verteil.schema.pkfare.QteResponse;
import com.verteil.schema.pkfare.QteRouting;
import com.verteil.schema.pkfare.QteSegment;
import com.verteil.schemas.ndc16_1.FareComponentType;
import com.verteil.schemas.ndc16_1.FlightCOSCoreType;
import com.verteil.schemas.ndc16_1.FlightCabinCoreType;
import com.verteil.schemas.ndc16_1.FlightPriceRS;
import com.verteil.schemas.ndc16_1.FlightSegmentReference;
import com.verteil.schemas.ndc16_1.ItemIDType;
import com.verteil.schemas.ndc16_1.OfferPriceLeadDetailType;
import com.verteil.schemas.ndc16_1.PricedFlightOffer;
import com.verteil.schemas.ndc16_1.PricedFlightOfferAssocType;
import com.verteil.schemas.ndc16_1.ShoppingResponseIDType;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.common.model.CabinMapping;
import com.verteil.thirdparties.adapter.pkfare.common.utils.NdcUtil;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.FareRuleConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.FreeBaggageConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.ItineraryConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.OfferFareConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.SegmentDetailsConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.TicketPriceConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.DataListsIndex;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.ItinerarySegment;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PassengerOfferPrice;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PricingContext;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.SegmentDetails;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.DataSegment;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferDataCodec;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.SegmentMatchKey;
import com.verteil.thirdparties.adapter.pkfare.offer.qte.model.PricedSegment;
import com.verteil.thirdparties.adapter.pkfare.offer.qte.model.QteMappingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VERTEIL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.FlightPriceConstants.DEFAULT_PRODUCT_TYPE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_CABIN;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.EMPTY;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.FARE_BASIS_SEPARATOR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.INVALID_FARE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.MSG_INSUFFICIENT_SEATS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.MSG_INVALID_FARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.MSG_NO_FARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.MSG_SOLD_OUT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.NO_FARE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_DIRECTION_OUTBOUND;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_DIRECTION_RETURN;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_ELIGIBILITY_NONE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_MAX_SEAT_COUNT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_INSUFFICIENT_SEATS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_SOLD_OUT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_SUCCESS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_SUCCESS_MSG;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_FLIGHTPRICE;

/**
 * VDC FlightPriceRS (first PricedFlightOffer) -> PKFARE QTE response.
 * <pre>
 * itinerary            first OfferPrice's associations via the shared ItineraryConverter; must equal the request's
 *                      segments per direction (matchKeys, in order), else 202
 * priceList            one per requested passenger type (shared TicketPriceConverter); missing type, missing
 *                      BaseAmount/TotalAmount or total &lt;= 0 -> 202
 * maxSeats             min ClassOfService.Code.SeatsLeft (&gt; 0) over the OfferPrices, max 9; any 0 -> status 2;
 *                      below adults + children -> status 1. Without SeatsLeft: adults + children (max 9)
 * sessionId            ShoppingResponseID.ResponseID, else the SEARCH offer data's
 * routing.data         offer data of the priced offer, for Order ({@link QteOfferDataFactory})
 * rbd                  first priced type's FareComponent FareBasis.RBD, else ClassOfService.Code, else the request segment's
 * cabinClass           ClassOfService cabin via CabinMapping.DEFAULT (W -> S), else the request segment's, else Y
 * fareBasis            per segment (first priced type), else the SEARCH offer data's fareBasis; ";"-joined, "" when any is unknown
 * productType / tag / otherFareRules / baggage / refund / change   shared offer converters
 * validatingCarrier    OfferID.Owner; eligibility ""
 * </pre>
 * Status 1 and 2 keep sessionId and maxSeats, with no routing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QteRSConverter {

    private final ItineraryConverter itineraryConverter;
    private final TicketPriceConverter ticketPriceConverter;
    private final FreeBaggageConverter freeBaggageConverter;
    private final FareRuleConverter fareRuleConverter;
    private final OfferFareConverter offerFareConverter;
    private final SegmentDetailsConverter segmentDetailsConverter;
    private final QteSegmentConverter qteSegmentConverter;
    private final QteOfferDataFactory offerDataFactory;
    private final OfferDataCodec offerDataCodec;

    private record Seats(int status, String msg, int maxSeats) {}

    public QteResponse convert(PkfareAdapterContext context) {
        QteMappingContext qteMappingContext = mapMappingContext(context);
        List<PassengerOfferPrice> passengerPrices = mapPassengerPrices(qteMappingContext);
        List<ItinerarySegment> itinerary = itineraryConverter.convert(qteMappingContext.offerId(),
                NdcUtil.firstOfferPriceAssociations(qteMappingContext.offer().getOfferPrice()), qteMappingContext.index());
        if (itinerary.isEmpty()) {
            throw invalidFare(qteMappingContext, "itinerary is not one-way or round trip");
        }
        List<PricedSegment> segments = itinerary.stream()
                .map(segment -> mapPricedSegment(segment, passengerPrices.getFirst(), qteMappingContext))
                .toList();
        requireRequestedItinerary(segments, qteMappingContext);

        Seats seats = mapSeats(qteMappingContext);
        String sessionId = mapSessionId(qteMappingContext);
        if (seats.status() != PK_STATUS_SUCCESS) {
            log.warn("Offer {}: {} (maxSeats {}, {} seated passengers)", qteMappingContext.offerId(), seats.msg(), seats.maxSeats(),
                    qteMappingContext.seatedPassengers());
            return mapResponse(seats, sessionId, null);
        }
        QteRouting routing = mapRouting(qteMappingContext, itinerary, segments, passengerPrices);
        log.info("Qte priced offer {}: {} segments, {} passenger types", qteMappingContext.offerId(), segments.size(),
                passengerPrices.size());
        return mapResponse(seats, sessionId, routing);
    }

    private static QteMappingContext mapMappingContext(PkfareAdapterContext context) {
        QteRequest request = (QteRequest) context.getRequest();
        FlightPriceRS response = (FlightPriceRS) context.getProviderApiResponse(SERVICE_FLIGHTPRICE);
        OfferData shopData = context.getOfferData();
        PricingContext pricing = new PricingContext(new DataListsIndex(response.getDataLists()),
                PricingContext.requestedTypes(request.getAdultNumber(), request.getChildNumber(), request.getInfantNumber()));
        return new QteMappingContext(request, shopData, response, mapPricedOffer(response), pricing,
                mapByMatchKey(mapRequestSegments(request)));
    }

    /** The validator guarantees one; the first is used when Entry-Gate returns several. */
    private static PricedFlightOffer mapPricedOffer(FlightPriceRS response) {
        return Optional.ofNullable(response.getPricedFlightOffers())
                .flatMap(offers -> offers.getPricedFlightOffer().stream().findFirst())
                .orElseThrow(() -> new PkfareAdapterException(VERTEIL_ERROR.getCode(), MSG_NO_FARE, NO_FARE_REASON_CODE));
    }

    /** One OfferPrice per requested passenger type, each with a base amount and a positive total; else 202. */
    private List<PassengerOfferPrice> mapPassengerPrices(QteMappingContext qteMappingContext) {
        List<PassengerOfferPrice> passengerPrices = ticketPriceConverter
                .resolve(qteMappingContext.offerId(), qteMappingContext.offer().getOfferPrice(), qteMappingContext.pricing())
                .orElseThrow(() -> invalidFare(qteMappingContext, "passenger types do not match the request"));
        if (!passengerPrices.stream().allMatch(QteRSConverter::hasUsableAmounts)) {
            throw invalidFare(qteMappingContext, "an OfferPrice lacks BaseAmount/TotalAmount or its total is not positive");
        }
        return passengerPrices;
    }

    private static boolean hasUsableAmounts(PassengerOfferPrice passengerPrice) {
        Optional<OfferPriceLeadDetailType.PriceDetail> priceDetail = Optional.ofNullable(passengerPrice.offerPrice().getRequestedDate())
                .map(OfferPriceLeadDetailType::getPriceDetail);
        boolean hasBase = priceDetail.map(OfferPriceLeadDetailType.PriceDetail::getBaseAmount)
                .map(NdcUtil::amount)
                .isPresent();
        boolean hasPositiveTotal = priceDetail.flatMap(NdcUtil::totalAmount)
                .map(NdcUtil::amount)
                .filter(total -> total.signum() > 0)
                .isPresent();
        return hasBase && hasPositiveTotal;
    }

    private PricedSegment mapPricedSegment(ItinerarySegment segment, PassengerOfferPrice firstPrice,
                                           QteMappingContext qteMappingContext) {
        SegmentDetails details = segmentDetailsConverter.convert(segment.segment());
        return new PricedSegment(segment, details, mapRbd(segment, details, firstPrice, qteMappingContext),
                mapCabinClass(segment, details, qteMappingContext), mapSegmentFareBasis(segment, details, firstPrice, qteMappingContext));
    }

    private static String mapRbd(ItinerarySegment segment, SegmentDetails details, PassengerOfferPrice firstPrice,
                                 QteMappingContext qteMappingContext) {
        return mapComponentRbd(segment, firstPrice, qteMappingContext.index())
                .or(() -> mapClassOfServiceCode(segment))
                .or(() -> mapRequestRbd(details, qteMappingContext))
                .orElse(null);
    }

    private static Optional<FlightCOSCoreType> mapClassOfService(ItinerarySegment segment) {
        return Optional.ofNullable(segment.reference()).map(FlightSegmentReference::getClassOfService);
    }

    private static Optional<String> mapClassOfServiceCode(ItinerarySegment segment) {
        return mapClassOfService(segment)
                .map(FlightCOSCoreType::getCode)
                .map(FlightCOSCoreType.Code::getValue)
                .filter(StringUtils::isNotBlank);
    }

    private static Optional<String> mapRequestRbd(SegmentDetails details, QteMappingContext qteMappingContext) {
        return qteMappingContext.requestSegment(details.matchKey())
                .map(QteSegment::getRbd)
                .filter(StringUtils::isNotBlank)
                .map(String::trim);
    }

    private String mapSegmentFareBasis(ItinerarySegment segment, SegmentDetails details, PassengerOfferPrice firstPrice,
                                       QteMappingContext qteMappingContext) {
        return offerFareConverter.mapFareBasis(segment, firstPrice, qteMappingContext.index())
                .or(() -> qteMappingContext.dataSegment(details.matchKey()).map(DataSegment::fareBasis))
                .orElse(null);
    }

    /** FareBasis.RBD of the first fare component priced for this segment. */
    private static Optional<String> mapComponentRbd(ItinerarySegment segment, PassengerOfferPrice passengerPrice, DataListsIndex index) {
        return Optional.ofNullable(passengerPrice.offerPrice().getFareDetail()).stream()
                .flatMap(fareDetail -> fareDetail.getFareComponent().stream())
                .filter(component -> index.segmentKeyOf(component)
                        .map(segment.segmentKey()::equals)
                        .orElse(false))
                .flatMap(component -> Optional.ofNullable(component.getFareBasis())
                        .map(FareComponentType.FareBasis::getRBD)
                        .stream())
                .filter(StringUtils::isNotBlank)
                .findFirst();
    }

    private static Optional<String> mapVdcCabin(ItinerarySegment segment) {
        return mapClassOfService(segment)
                .map(FlightCOSCoreType::getMarketingName)
                .map(FlightCOSCoreType.MarketingName::getCabinDesignator)
                .filter(StringUtils::isNotBlank)
                .or(() -> mapReferenceCabin(segment));
    }

    private static Optional<String> mapReferenceCabin(ItinerarySegment segment) {
        return Optional.ofNullable(segment.reference())
                .map(FlightSegmentReference::getCabin)
                .map(FlightCabinCoreType::getCabinDesignator)
                .map(FlightCabinCoreType.CabinDesignator::getValue)
                .filter(StringUtils::isNotBlank);
    }

    private static String mapCabinClass(ItinerarySegment segment, SegmentDetails details, QteMappingContext qteMappingContext) {
        return mapVdcCabin(segment).flatMap(CabinMapping.DEFAULT::toPk)
                .or(() -> mapRequestCabin(details, qteMappingContext))
                .orElse(DEFAULT_CABIN);
    }

    private static Optional<String> mapRequestCabin(SegmentDetails details, QteMappingContext qteMappingContext) {
        return qteMappingContext.requestSegment(details.matchKey())
                .map(QteSegment::getCabinClass)
                .filter(StringUtils::isNotBlank)
                .map(cabin -> cabin.trim().toUpperCase(Locale.ROOT));
    }

    /** The priced itinerary must be exactly the requested one, direction by direction, in order; else 202. */
    private static void requireRequestedItinerary(List<PricedSegment> segments, QteMappingContext qteMappingContext) {
        QteRequestRouting routing = qteMappingContext.request().getRouting();
        boolean matches = mapMatchKeys(routing.getFromSegments()).equals(mapPricedMatchKeys(segments, PK_DIRECTION_OUTBOUND))
                && mapMatchKeys(routing.getRetSegments()).equals(mapPricedMatchKeys(segments, PK_DIRECTION_RETURN));
        if (!matches) {
            throw invalidFare(qteMappingContext, "priced itinerary " + mapPricedMatchKeys(segments, PK_DIRECTION_OUTBOUND) + " / "
                    + mapPricedMatchKeys(segments, PK_DIRECTION_RETURN) + " differs from the request");
        }
    }

    private static List<String> mapPricedMatchKeys(List<PricedSegment> segments, int travelDirection) {
        return segments.stream()
                .filter(segment -> segment.itinerary().travelDirection() == travelDirection)
                .map(PricedSegment::matchKey)
                .toList();
    }

    private static Seats mapSeats(QteMappingContext qteMappingContext) {
        List<Integer> seatsLeft = mapSeatsLeft(qteMappingContext.offer());
        if (seatsLeft.contains(0)) {
            return new Seats(PK_STATUS_SOLD_OUT, MSG_SOLD_OUT, 0);
        }
        return seatsLeft.stream()
                .filter(left -> left > 0)
                .min(Integer::compare)
                .map(left -> mapAvailableSeats(left, qteMappingContext))
                .orElseGet(() -> new Seats(PK_STATUS_SUCCESS, PK_SUCCESS_MSG, mapDefaultSeats(qteMappingContext)));
    }

    private static Seats mapAvailableSeats(int left, QteMappingContext qteMappingContext) {
        int maxSeats = Math.min(left, PK_MAX_SEAT_COUNT);
        if (left < qteMappingContext.seatedPassengers()) {
            return new Seats(PK_STATUS_INSUFFICIENT_SEATS, MSG_INSUFFICIENT_SEATS, maxSeats);
        }
        return new Seats(PK_STATUS_SUCCESS, PK_SUCCESS_MSG, maxSeats);
    }

    /** VDC does not say how many seats are left: promise exactly the seat-occupying passengers. */
    private static int mapDefaultSeats(QteMappingContext qteMappingContext) {
        return Math.min(Math.max(qteMappingContext.seatedPassengers(), 1), PK_MAX_SEAT_COUNT);
    }

    /** Every ClassOfService.Code.SeatsLeft given on the priced offer's segment references. */
    private static List<Integer> mapSeatsLeft(PricedFlightOffer offer) {
        return offer.getOfferPrice().stream()
                .flatMap(NdcUtil::segmentReferences)
                .map(QteRSConverter::mapReferenceSeatsLeft)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<Integer> mapReferenceSeatsLeft(FlightSegmentReference reference) {
        return Optional.ofNullable(reference.getClassOfService())
                .map(FlightCOSCoreType::getCode)
                .map(FlightCOSCoreType.Code::getSeatsLeft);
    }

    private static String mapSessionId(QteMappingContext qteMappingContext) {
        return Optional.ofNullable(qteMappingContext.response().getShoppingResponseID())
                .map(ShoppingResponseIDType::getResponseID)
                .map(ShoppingResponseIDType.ResponseID::getValue)
                .filter(StringUtils::isNotBlank)
                .orElseGet(() -> mapShopSessionId(qteMappingContext));
    }

    private static String mapShopSessionId(QteMappingContext qteMappingContext) {
        return Optional.ofNullable(qteMappingContext.shopData())
                .map(OfferData::shoppingResponseId)
                .map(OfferData.ShoppingResponseRef::value)
                .orElse(null);
    }

    private QteRouting mapRouting(QteMappingContext qteMappingContext, List<ItinerarySegment> itinerary,
                                  List<PricedSegment> segments, List<PassengerOfferPrice> passengerPrices) {
        String currency = TicketPriceConverter.mapCurrency(qteMappingContext.offer().getOfferPrice());
        boolean roundTrip = itinerary.stream().anyMatch(segment -> segment.travelDirection() == PK_DIRECTION_RETURN);
        QteRouting routing = new QteRouting();
        routing.setData(mapRoutingData(qteMappingContext, segments, passengerPrices));
        routing.setFromSegments(mapQteSegments(segments, PK_DIRECTION_OUTBOUND));
        routing.setRetSegments(mapQteSegments(segments, PK_DIRECTION_RETURN));
        routing.setPriceList(mapPriceList(passengerPrices));
        routing.setEligibility(PK_ELIGIBILITY_NONE);
        routing.setValidatingCarrier(mapValidatingCarrier(qteMappingContext.offer()));
        routing.setFareBasis(mapFareBasis(segments, qteMappingContext));
        routing.setFreeBaggageList(mapFreeBaggageList(itinerary, passengerPrices, qteMappingContext.index()));
        routing.setRefundInfoList(mapRefundInfoList(itinerary, passengerPrices, currency, roundTrip, qteMappingContext.index()));
        routing.setChangeInfoList(mapChangeInfoList(itinerary, passengerPrices, currency, roundTrip, qteMappingContext.index()));
        routing.setCurrency(currency);
        routing.setProductType(offerFareConverter.mapProductType(passengerPrices, qteMappingContext.index(),
                DEFAULT_PRODUCT_TYPE));
        routing.setTag(mapTag(passengerPrices, qteMappingContext));
        routing.setOtherFareRules(mapOtherFareRules(passengerPrices, qteMappingContext));
        return routing;
    }

    private String mapRoutingData(QteMappingContext qteMappingContext, List<PricedSegment> segments,
                                  List<PassengerOfferPrice> passengerPrices) {
        return offerDataCodec.encode(offerDataFactory.create(qteMappingContext.response(), qteMappingContext.offer(), passengerPrices,
                segments, qteMappingContext.index(), qteMappingContext.shopData()));
    }

    private List<QtePrice> mapPriceList(List<PassengerOfferPrice> passengerPrices) {
        return passengerPrices.stream()
                .map(ticketPriceConverter::mapFare)
                .map(QteTicketMapper::mapPrice)
                .toList();
    }

    private static String mapValidatingCarrier(PricedFlightOffer offer) {
        return Optional.ofNullable(offer.getOfferID())
                .map(ItemIDType::getOwner)
                .orElse(null);
    }

    private List<QteFreeBaggage> mapFreeBaggageList(List<ItinerarySegment> itinerary,
                                                    List<PassengerOfferPrice> passengerPrices, DataListsIndex index) {
        return freeBaggageConverter.convert(itinerary, passengerPrices, index).stream()
                .map(QteTicketMapper::mapFreeBaggage)
                .toList();
    }

    private List<QteRefundInfo> mapRefundInfoList(List<ItinerarySegment> itinerary,
                                                  List<PassengerOfferPrice> passengerPrices, String currency,
                                                  boolean roundTrip, DataListsIndex index) {
        return fareRuleConverter.mapRefunds(itinerary, passengerPrices, currency, roundTrip, index).stream()
                .map(QteTicketMapper::mapRefundInfo)
                .toList();
    }

    private List<QteChangeInfo> mapChangeInfoList(List<ItinerarySegment> itinerary,
                                                  List<PassengerOfferPrice> passengerPrices, String currency,
                                                  boolean roundTrip, DataListsIndex index) {
        return fareRuleConverter.mapChanges(itinerary, passengerPrices, currency, roundTrip, index).stream()
                .map(QteTicketMapper::mapChangeInfo)
                .toList();
    }

    private List<QteSegment> mapQteSegments(List<PricedSegment> segments, int travelDirection) {
        return segments.stream()
                .filter(segment -> segment.itinerary().travelDirection() == travelDirection)
                .map(qteSegmentConverter::convert)
                .toList();
    }

    /** One fare basis per segment, ";"-joined; "" when any segment has none (PKFARE needs one per segment). */
    private static String mapFareBasis(List<PricedSegment> segments, QteMappingContext qteMappingContext) {
        if (!segments.stream().map(PricedSegment::fareBasis).allMatch(StringUtils::isNotBlank)) {
            log.warn("Offer {}: fare basis unknown for some segments, fareBasis sent empty", qteMappingContext.offerId());
            return EMPTY;
        }
        return segments.stream()
                .map(PricedSegment::fareBasis)
                .collect(Collectors.joining(FARE_BASIS_SEPARATOR));
    }

    /** Price class names of the priced passenger types, "/"-joined; else the request's tag. */
    private String mapTag(List<PassengerOfferPrice> passengerPrices, QteMappingContext qteMappingContext) {
        List<PricedFlightOfferAssocType> associations = passengerPrices.stream()
                .flatMap(passengerPrice -> NdcUtil.associations(passengerPrice.offerPrice()).stream())
                .toList();
        return Optional.of(offerFareConverter.mapPriceClassNames(associations, qteMappingContext.index()))
                .filter(names -> !names.isEmpty())
                .map(names -> String.join("/", names))
                .orElseGet(() -> qteMappingContext.request().getRouting().getTag());
    }

    private List<QteOtherFareRule> mapOtherFareRules(List<PassengerOfferPrice> passengerPrices,
                                                     QteMappingContext qteMappingContext) {
        return passengerPrices.stream()
                .map(passengerPrice -> QteTicketMapper.mapOtherFareRule(passengerPrice.passengerType(),
                        offerFareConverter.mapPriceClassDetails(NdcUtil.associations(passengerPrice.offerPrice()), qteMappingContext.index())))
                .toList();
    }

    private static QteResponse mapResponse(Seats seats, String sessionId, QteRouting routing) {
        QteResponse response = new QteResponse();
        response.setStatus(seats.status());
        response.setMsg(seats.msg());
        response.setSessionId(sessionId);
        response.setMaxSeats(seats.maxSeats());
        response.setRouting(routing);
        return response;
    }

    private static List<QteSegment> mapRequestSegments(QteRequest request) {
        return Optional.ofNullable(request.getRouting()).stream()
                .flatMap(routing -> Stream.concat(nullSafe(routing.getFromSegments()).stream(), nullSafe(routing.getRetSegments()).stream()))
                .toList();
    }

    private static List<String> mapMatchKeys(List<QteSegment> segments) {
        return nullSafe(segments).stream()
                .map(SegmentMatchKey::of)
                .toList();
    }

    private static Map<String, QteSegment> mapByMatchKey(List<QteSegment> segments) {
        return segments.stream()
                .collect(Collectors.toMap(SegmentMatchKey::of, Function.identity(), (first, _) -> first));
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return Optional.ofNullable(list).orElse(List.of());
    }

    private static PkfareAdapterException invalidFare(QteMappingContext qteMappingContext, String detail) {
        log.warn("Offer {} is not usable: {}", qteMappingContext.offerId(), detail);
        return new PkfareAdapterException(VERTEIL_ERROR.getCode(), MSG_INVALID_FARE, INVALID_FARE_REASON_CODE);
    }
}
