package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.verteil.schema.pkfare.SearchChangeInfo;
import com.verteil.schema.pkfare.SearchFlightReference;
import com.verteil.schema.pkfare.SearchFreeBaggage;
import com.verteil.schema.pkfare.SearchOtherFareRule;
import com.verteil.schema.pkfare.SearchPrice;
import com.verteil.schema.pkfare.SearchRefundInfo;
import com.verteil.schema.pkfare.SearchShoppingResult;
import com.verteil.schema.pkfare.SearchTicket;
import com.verteil.schemas.ndc16_1.AirShoppingRS.OffersGroup.AirlineOffers;
import com.verteil.schemas.ndc16_1.AirShoppingRS.OffersGroup.AirlineOffers.AirlineOffer;
import com.verteil.schemas.ndc16_1.AirlineIDType;
import com.verteil.schemas.ndc16_1.FlightCOSCoreType;
import com.verteil.schemas.ndc16_1.FlightCabinCoreType;
import com.verteil.schemas.ndc16_1.FlightSegmentReference;
import com.verteil.schemas.ndc16_1.ItemIDType;
import com.verteil.schemas.ndc16_1.OfferItemCoreType;
import com.verteil.schemas.ndc16_1.OfferPriceLeadType;
import com.verteil.schemas.ndc16_1.PriceClassType;
import com.verteil.schemas.ndc16_1.PricedFlightOfferAssocType;
import com.verteil.schemas.ndc16_1.PricedFlightOfferType;
import com.verteil.schemas.ndc16_1.SimpleCurrencyPriceType;
import com.verteil.thirdparties.adapter.pkfare.common.model.CabinMapping;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.FareRuleConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.FreeBaggageConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.ItineraryConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.OfferFareConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.TicketPriceConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.DataListsIndex;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.ItinerarySegment;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PassengerOfferPrice;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferDataCodec;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.SearchMappingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.AirShoppingConstants.DEFAULT_PRODUCT_TYPE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.AirShoppingConstants.TICKET_ISSUE_TIME;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_CABIN;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.EMPTY;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_DEFAULT_TICKET_SEQ;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_MAX_SEAT_COUNT;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * One AirlineOffer -> one {@code shoppingResultList[]} entry (single ticket, ticketSeq 1). The offer logic lives in
 * the shared {@code offer/common} converters; this class maps their records to the SEARCH models.
 * <pre>
 * flightRefList[]        offer-level Associations.ApplicableFlight.FlightSegmentReference[] in itinerary order
 *   cabinClass / rbd     ClassOfService.MarketingName.CabinDesignator via CabinMapping.DEFAULT (W -> S) / ClassOfService.Code
 *   seatCount            ClassOfService.Code.SeatsLeft (max 9), else configured / passenger-count default
 * data                   Base64 {@link OfferData} ({@link SearchOfferDataFactory}); no ShoppingResponseID -> offer skipped
 * ticket.validatingCarrier ValidatingCarrier, else OfferID.Owner, else AirlineOffers.Owner
 * ticket.currency        TotalPrice, else the first OfferPrice total
 * ticket.productType / fareBasis / otherFareRules  {@link OfferFareConverter}
 * tag                    PriceClass.Name (returnTagPrice=Y only)
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShoppingResultConverter {

    private static final int THRESHOLD_NOT_SUPPORTED = 0;

    private final ItineraryConverter itineraryConverter;
    private final TicketPriceConverter ticketPriceConverter;
    private final FreeBaggageConverter freeBaggageConverter;
    private final FareRuleConverter fareRuleConverter;
    private final OfferFareConverter offerFareConverter;
    private final SearchOfferDataFactory offerDataFactory;
    private final OfferDataCodec offerDataCodec;

    public Optional<SearchShoppingResult> convert(AirlineOffers airlineOffers, AirlineOffer offer,
                                                  SearchMappingContext searchMappingContext) {
        if (isNull(offer.getPricedOffer())) {
            return Optional.empty();
        }
        List<ItinerarySegment> itinerary = itineraryConverter.convert(mapOfferId(offer), mapOfferAssociations(offer),
                searchMappingContext.index());
        if (itinerary.isEmpty()) {
            return Optional.empty();
        }
        boolean roundTrip = itinerary.stream().anyMatch(segment -> segment.travelDirection() > 1);
        if (roundTrip != SearchRQConverter.isRoundTrip(searchMappingContext.request())) {
            log.warn("Offer {} journey count does not match the requested trip type, offer skipped", mapOfferId(offer));
            return Optional.empty();
        }
        Optional<List<PassengerOfferPrice>> passengerPrices = ticketPriceConverter.resolve(mapOfferId(offer),
                offer.getPricedOffer().getOfferPrice(), searchMappingContext.pricing());
        if (passengerPrices.isEmpty()) {
            return Optional.empty();
        }
        List<PricedFlightOfferAssocType> associations = offer.getPricedOffer().getAssociations();
        List<String> priceClassNames = offerFareConverter.mapPriceClassNames(associations, searchMappingContext.index());
        String tag = searchMappingContext.returnTagPrice() ? String.join("/", priceClassNames) : EMPTY;
        if (isNotBlank(searchMappingContext.requestedTag()) && !matchesTag(associations, priceClassNames, searchMappingContext)) {
            return Optional.empty();
        }

        // Build everything that can fail before registering flight references, so a skipped offer never leaves
        // unreferenced flights behind in flightList.
        SearchTicket ticket = mapTicket(airlineOffers, offer, itinerary, passengerPrices.get(), tag, roundTrip, searchMappingContext);
        return mapData(offer, itinerary, passengerPrices.get(), searchMappingContext)
                .map(data -> mapShoppingResult(itinerary, data, ticket, tag, searchMappingContext));
    }

    static String mapOfferId(AirlineOffer offer) {
        return Optional.ofNullable(offer.getOfferID())
                .map(ItemIDType::getValue)
                .orElse(null);
    }

    /** The offer's Base64 offer data; empty (offer skipped) when QTE could not price it, i.e. no ShoppingResponseID. */
    private Optional<String> mapData(AirlineOffer offer, List<ItinerarySegment> itinerary, List<PassengerOfferPrice> passengerPrices,
                                     SearchMappingContext searchMappingContext) {
        Optional<OfferData> offerData = offerDataFactory.create(offer, itinerary, passengerPrices, searchMappingContext);
        if (offerData.isEmpty()) {
            log.warn("Offer {} has no ShoppingResponseID for its owner, offer skipped", mapOfferId(offer));
        }
        return offerData.map(offerDataCodec::encode);
    }

    /** Registers the itinerary's flight references, so call it only once the offer is sure to be returned. */
    private SearchShoppingResult mapShoppingResult(List<ItinerarySegment> itinerary, String data, SearchTicket ticket, String tag,
                                                   SearchMappingContext searchMappingContext) {
        SearchShoppingResult result = new SearchShoppingResult();
        itinerary.forEach(segment -> result.getFlightRefList().add(mapFlightReference(segment, searchMappingContext)));
        result.setData(data);
        result.getTicketList().add(ticket);
        result.setTag(tag);
        result.setThreshold(THRESHOLD_NOT_SUPPORTED);
        return result;
    }

    /** Offer-level associations carry this offer's class of service per segment; fall back to the first OfferPrice. */
    private static List<PricedFlightOfferAssocType> mapOfferAssociations(AirlineOffer offer) {
        PricedFlightOfferType pricedOffer = offer.getPricedOffer();
        if (!pricedOffer.getAssociations().isEmpty()) {
            return pricedOffer.getAssociations();
        }
        return mapFirstOfferPriceAssociations(pricedOffer);
    }

    private static List<PricedFlightOfferAssocType> mapFirstOfferPriceAssociations(PricedFlightOfferType pricedOffer) {
        return pricedOffer.getOfferPrice().stream()
                .flatMap(offerPrice -> Optional.ofNullable(offerPrice.getRequestedDate()).stream())
                .findFirst()
                .map(OfferPriceLeadType.RequestedDate::getAssociations)
                .orElse(List.of());
    }

    private SearchFlightReference mapFlightReference(ItinerarySegment segment, SearchMappingContext searchMappingContext) {
        FlightCOSCoreType classOfService = Optional.ofNullable(segment.reference())
                .map(FlightSegmentReference::getClassOfService)
                .orElse(segment.segment().getClassOfService());
        SearchFlightReference reference = new SearchFlightReference();
        reference.setFlightRefNum(searchMappingContext.flightRefNum(segment.segmentKey()));
        reference.setTravelDirection(segment.travelDirection());
        reference.setFlightSeq(segment.flightSeq());
        reference.setTicketSeq(PK_DEFAULT_TICKET_SEQ);
        reference.setCabinClass(mapCabin(segment, classOfService, searchMappingContext));
        reference.setRbd(mapRbd(classOfService));
        reference.setSeatCount(mapSeatCount(classOfService, searchMappingContext));
        return reference;
    }

    /** Throws a NullPointerException when the class of service has no code, so the offer is skipped. */
    private static String mapRbd(FlightCOSCoreType classOfService) {
        return Objects.requireNonNull(Optional.ofNullable(classOfService)
                .map(FlightCOSCoreType::getCode)
                .map(FlightCOSCoreType.Code::getValue)
                .orElse(null));
    }

    private static String mapCabin(ItinerarySegment segment, FlightCOSCoreType classOfService, SearchMappingContext searchMappingContext) {
        String vdcCabin = Optional.ofNullable(classOfService)
                .map(FlightCOSCoreType::getMarketingName)
                .map(FlightCOSCoreType.MarketingName::getCabinDesignator)
                .orElseGet(() -> mapReferenceCabin(segment));
        return CabinMapping.DEFAULT.toPk(vdcCabin)
                .or(() -> mapRequestedCabin(searchMappingContext))
                .orElse(DEFAULT_CABIN);
    }

    private static String mapReferenceCabin(ItinerarySegment segment) {
        return Optional.ofNullable(segment.reference())
                .map(FlightSegmentReference::getCabin)
                .map(FlightCabinCoreType::getCabinDesignator)
                .map(FlightCabinCoreType.CabinDesignator::getValue)
                .orElse(null);
    }

    /** The requested cabin, only when PKFARE can map it. */
    private static Optional<String> mapRequestedCabin(SearchMappingContext searchMappingContext) {
        return Optional.ofNullable(searchMappingContext.request().getCabinClass())
                .filter(requested -> CabinMapping.DEFAULT.toVdc(requested).isPresent())
                .map(requested -> requested.trim().toUpperCase(Locale.ROOT));
    }

    private static int mapSeatCount(FlightCOSCoreType classOfService, SearchMappingContext searchMappingContext) {
        int seats = Optional.ofNullable(classOfService)
                .map(FlightCOSCoreType::getCode)
                .map(FlightCOSCoreType.Code::getSeatsLeft)
                .filter(seatsLeft -> seatsLeft > 0)
                .orElseGet(() -> Math.max(mapSeatedPassengers(searchMappingContext), 1));
        return Math.min(seats, PK_MAX_SEAT_COUNT);
    }

    /** Seat-occupying passengers: what is promised when VDC does not say how many seats are left. */
    private static int mapSeatedPassengers(SearchMappingContext searchMappingContext) {
        return requireNonNullElse(searchMappingContext.request().getAdultNumber(), 0)
                + requireNonNullElse(searchMappingContext.request().getChildNumber(), 0);
    }

    private SearchTicket mapTicket(AirlineOffers airlineOffers, AirlineOffer offer, List<ItinerarySegment> itinerary,
                                   List<PassengerOfferPrice> passengerPrices, String tag, boolean roundTrip,
                                   SearchMappingContext searchMappingContext) {
        String currency = mapCurrency(offer);
        SearchTicket ticket = new SearchTicket();
        ticket.setTicketSeq(PK_DEFAULT_TICKET_SEQ);
        ticket.getPriceList().addAll(mapPriceList(passengerPrices));
        ticket.setValidatingCarrier(mapValidatingCarrier(airlineOffers, offer));
        ticket.setProductType(offerFareConverter.mapProductType(passengerPrices, searchMappingContext.index(),
                DEFAULT_PRODUCT_TYPE));
        ticket.setFreeBaggageList(mapFreeBaggageList(itinerary, passengerPrices, searchMappingContext.index()));
        ticket.setRefundInfoList(mapRefundInfoList(itinerary, passengerPrices, currency, roundTrip, searchMappingContext.index()));
        ticket.setChangeInfoList(mapChangeInfoList(itinerary, passengerPrices, currency, roundTrip, searchMappingContext.index()));
        ticket.setCurrency(currency);
        ticket.setTicketIssueTime(TICKET_ISSUE_TIME);
        ticket.setTag(tag);
        ticket.setOtherFareRules(mapOtherFareRules(offer, passengerPrices, searchMappingContext));
        ticket.setFareBasis(offerFareConverter.mapFareBasis(itinerary, passengerPrices.getFirst(), searchMappingContext.index()));
        return ticket;
    }

    private List<SearchPrice> mapPriceList(List<PassengerOfferPrice> passengerPrices) {
        return passengerPrices.stream()
                .map(ticketPriceConverter::mapFare)
                .map(SearchTicketMapper::mapPrice)
                .toList();
    }

    private List<SearchFreeBaggage> mapFreeBaggageList(List<ItinerarySegment> itinerary,
                                                       List<PassengerOfferPrice> passengerPrices, DataListsIndex index) {
        return freeBaggageConverter.convert(itinerary, passengerPrices, index).stream()
                .map(SearchTicketMapper::mapFreeBaggage)
                .toList();
    }

    private List<SearchRefundInfo> mapRefundInfoList(List<ItinerarySegment> itinerary,
                                                     List<PassengerOfferPrice> passengerPrices, String currency,
                                                     boolean roundTrip, DataListsIndex index) {
        return fareRuleConverter.mapRefunds(itinerary, passengerPrices, currency, roundTrip, index).stream()
                .map(SearchTicketMapper::mapRefundInfo)
                .toList();
    }

    private List<SearchChangeInfo> mapChangeInfoList(List<ItinerarySegment> itinerary,
                                                     List<PassengerOfferPrice> passengerPrices, String currency,
                                                     boolean roundTrip, DataListsIndex index) {
        return fareRuleConverter.mapChanges(itinerary, passengerPrices, currency, roundTrip, index).stream()
                .map(SearchTicketMapper::mapChangeInfo)
                .toList();
    }

    /** Currency of the offer: TotalPrice, else the first OfferPrice total. */
    private static String mapCurrency(AirlineOffer offer) {
        return Optional.ofNullable(offer.getTotalPrice())
                .map(OfferItemCoreType.TotalPrice::getSimpleCurrencyPrice)
                .map(SimpleCurrencyPriceType::getCode)
                .orElseGet(() -> TicketPriceConverter.mapCurrency(offer.getPricedOffer().getOfferPrice()));
    }

    private static String mapValidatingCarrier(AirlineOffers airlineOffers, AirlineOffer offer) {
        return Optional.ofNullable(offer.getValidatingCarrier())
                .filter(StringUtils::isNotBlank)
                .or(() -> mapOfferOwner(offer))
                .orElseGet(() -> Optional.ofNullable(airlineOffers.getOwner())
                        .map(AirlineIDType::getValue)
                        .orElse(null));
    }

    private static Optional<String> mapOfferOwner(AirlineOffer offer) {
        return Optional.ofNullable(offer.getOfferID())
                .map(ItemIDType::getOwner)
                .filter(StringUtils::isNotBlank);
    }

    private boolean matchesTag(List<PricedFlightOfferAssocType> associations, List<String> priceClassNames,
                               SearchMappingContext searchMappingContext) {
        String requested = searchMappingContext.requestedTag().trim();
        return priceClassNames.stream().anyMatch(requested::equalsIgnoreCase)
                || matchesPriceClassCode(associations, requested, searchMappingContext);
    }

    private boolean matchesPriceClassCode(List<PricedFlightOfferAssocType> associations, String requested,
                                          SearchMappingContext searchMappingContext) {
        return offerFareConverter.mapPriceClasses(associations, searchMappingContext.index()).stream()
                .map(PriceClassType::getCode)
                .anyMatch(requested::equalsIgnoreCase);
    }

    private List<SearchOtherFareRule> mapOtherFareRules(AirlineOffer offer, List<PassengerOfferPrice> passengerPrices,
                                                        SearchMappingContext searchMappingContext) {
        List<String> details = offerFareConverter.mapPriceClassDetails(offer.getPricedOffer().getAssociations(),
                searchMappingContext.index());
        return passengerPrices.stream()
                .map(passengerPrice -> SearchTicketMapper.mapOtherFareRule(passengerPrice.passengerType(), details))
                .toList();
    }
}
