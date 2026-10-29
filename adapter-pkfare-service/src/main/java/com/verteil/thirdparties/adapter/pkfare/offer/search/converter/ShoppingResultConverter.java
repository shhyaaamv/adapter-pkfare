package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.verteil.schema.pkfare.SearchFlightReference;
import com.verteil.schema.pkfare.SearchOtherFareRule;
import com.verteil.schema.pkfare.SearchShoppingResult;
import com.verteil.schema.pkfare.SearchTicket;
import com.verteil.schemas.ndc16_1.AirShoppingRS.OffersGroup.AirlineOffers;
import com.verteil.schemas.ndc16_1.AirShoppingRS.OffersGroup.AirlineOffers.AirlineOffer;
import com.verteil.schemas.ndc16_1.AirlineIDType;
import com.verteil.schemas.ndc16_1.DescriptionType;
import com.verteil.schemas.ndc16_1.FareBasisCodeType;
import com.verteil.schemas.ndc16_1.FareComponentType;
import com.verteil.schemas.ndc16_1.FareDetailType;
import com.verteil.schemas.ndc16_1.FareList;
import com.verteil.schemas.ndc16_1.FlightCOSCoreType;
import com.verteil.schemas.ndc16_1.FlightCabinCoreType;
import com.verteil.schemas.ndc16_1.FlightSegmentReference;
import com.verteil.schemas.ndc16_1.ItemIDType;
import com.verteil.schemas.ndc16_1.PriceClassType;
import com.verteil.schemas.ndc16_1.PricedFlightOfferType;
import com.verteil.schemas.ndc16_1.ShoppingResponseIDType;
import com.verteil.thirdparties.adapter.pkfare.common.model.CabinMapping;
import com.verteil.thirdparties.adapter.pkfare.common.utils.RefUtil;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.ItinerarySegment;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.OfferData;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.PassengerOfferPrice;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.SearchMappingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_CABIN;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.FARE_BASIS_SEPARATOR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_DEFAULT_TICKET_SEQ;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_MAX_SEAT_COUNT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_PRIVATE_FARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_PUBLIC_FARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_PRIVATE_FARE_REMARK;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_PUBLIC_FARE_REMARK;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.EMPTY;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * One AirlineOffer -> one {@code shoppingResultList[]} entry (single ticket, ticketSeq 1).
 * <pre>
 * flightRefList[]        offer-level Associations.ApplicableFlight.FlightSegmentReference[] in itinerary order
 *   cabinClass / rbd     ClassOfService.MarketingName.CabinDesignator via CABIN_BRAND.MAPPED_CABIN (W -> S) / ClassOfService.Code
 *   seatCount            ClassOfService.Code.SeatsLeft (max 9), else configured / passenger-count default
 * data                   Base64 {offerId, owner, shoppingResponseId, offerItemIds, segmentKeys}
 * ticket.validatingCarrier ValidatingCarrier, else OfferID.Owner, else AirlineOffers.Owner
 * ticket.productType     FareGroup Remarks PVT -> PRV, PUBL -> PUB, else configured default
 * ticket.fareBasis       per segment FareComponent -> FareGroup.FareBasisCode, ";"-joined, only if every segment has one
 * tag                    PriceClass.Name (returnTagPrice=Y only)
 * otherFareRules         PriceClass descriptions per passenger type
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
    private final OfferDataConverter offerDataConverter;

    public Optional<SearchShoppingResult> convert(AirlineOffers airlineOffers, AirlineOffer offer, SearchMappingContext context) {
        if (isNull(offer.getPricedOffer())) {
            return Optional.empty();
        }
        List<ItinerarySegment> itinerary = itineraryConverter.convert(offer, context);
        if (itinerary.isEmpty()) {
            return Optional.empty();
        }
        boolean roundTrip = itinerary.stream().anyMatch(segment -> segment.travelDirection() > 1);
        if (roundTrip != SearchRQConverter.isRoundTrip(context.request())) {
            log.warn("Offer {} journey count does not match the requested trip type, offer skipped", ItineraryConverter.offerId(offer));
            return Optional.empty();
        }
        Optional<List<PassengerOfferPrice>> passengerPrices = ticketPriceConverter.resolve(offer, context);
        if (passengerPrices.isEmpty()) {
            return Optional.empty();
        }
        List<String> priceClassNames = priceClassNames(offer, context);
        String tag = context.returnTagPrice() ? String.join("/", priceClassNames) : EMPTY;
        if (isNotBlank(context.requestedTag()) && !matchesTag(offer, priceClassNames, context)) {
            return Optional.empty();
        }

        // Build everything that can fail before registering flight references, so a skipped offer never leaves
        // unreferenced flights behind in flightList.
        SearchTicket ticket = ticket(airlineOffers, offer, itinerary, passengerPrices.get(), tag, roundTrip, context);
        String data = offerDataConverter.encode(offerData(airlineOffers, offer, itinerary, context));
        SearchShoppingResult result = new SearchShoppingResult();
        itinerary.forEach(segment -> result.getFlightRefList().add(flightReference(segment, context)));
        result.setData(data);
        result.getTicketList().add(ticket);
        result.setTag(tag);
        result.setThreshold(THRESHOLD_NOT_SUPPORTED);
        return Optional.of(result);
    }

    private SearchFlightReference flightReference(ItinerarySegment segment, SearchMappingContext context) {
        FlightCOSCoreType classOfService = Optional.ofNullable(segment.reference())
                .map(FlightSegmentReference::getClassOfService)
                .orElse(segment.segment().getClassOfService());
        SearchFlightReference reference = new SearchFlightReference();
        reference.setFlightRefNum(context.flightRefNum(segment.segmentKey()));
        reference.setTravelDirection(segment.travelDirection());
        reference.setFlightSeq(segment.flightSeq());
        reference.setTicketSeq(PK_DEFAULT_TICKET_SEQ);
        reference.setCabinClass(cabin(segment, classOfService, context));
        reference.setRbd(Objects.requireNonNull(Optional.ofNullable(classOfService)
                .map(FlightCOSCoreType::getCode)
                .map(FlightCOSCoreType.Code::getValue)
                .orElse(null)));
        reference.setSeatCount(seatCount(classOfService, context));
        return reference;
    }

    private static String cabin(ItinerarySegment segment, FlightCOSCoreType classOfService, SearchMappingContext context) {
        String vdcCabin = Optional.ofNullable(classOfService)
                .map(FlightCOSCoreType::getMarketingName)
                .map(FlightCOSCoreType.MarketingName::getCabinDesignator)
                .orElseGet(() -> Optional.ofNullable(segment.reference())
                        .map(FlightSegmentReference::getCabin)
                        .map(FlightCabinCoreType::getCabinDesignator)
                        .map(FlightCabinCoreType.CabinDesignator::getValue)
                        .orElse(null));
        CabinMapping cabinMapping = context.cabinMapping();
        return cabinMapping.toPk(vdcCabin)
                .or(() -> Optional.of(context.request().getCabinClass())
                        .filter(requested -> cabinMapping.toVdc(requested).isPresent())
                        .map(requested -> requested.trim().toUpperCase(Locale.ROOT)))
                .orElse(DEFAULT_CABIN);
    }

    private static int seatCount(FlightCOSCoreType classOfService, SearchMappingContext context) {
        int seats = Optional.ofNullable(classOfService)
                .map(FlightCOSCoreType::getCode)
                .map(FlightCOSCoreType.Code::getSeatsLeft)
                .filter(seatsLeft -> seatsLeft > 0)
                .or(() -> Optional.ofNullable(context.defaultSeatCount()).filter(defaultSeatCount -> defaultSeatCount > 0))
                // VDC does not say how many seats are left: promise exactly what was asked for (seat-occupying passengers).
                .orElseGet(() -> Math.max(requireNonNullElse(context.request().getAdultNumber(), 0)
                        + requireNonNullElse(context.request().getChildNumber(), 0), 1));
        return Math.min(seats, PK_MAX_SEAT_COUNT);
    }

    private SearchTicket ticket(AirlineOffers airlineOffers, AirlineOffer offer, List<ItinerarySegment> itinerary,
                                List<PassengerOfferPrice> passengerPrices, String tag, boolean roundTrip, SearchMappingContext context) {
        String currency = TicketPriceConverter.currency(offer);
        SearchTicket ticket = new SearchTicket();
        ticket.setTicketSeq(PK_DEFAULT_TICKET_SEQ);
        passengerPrices.forEach(passengerPrice -> ticket.getPriceList().add(ticketPriceConverter.convert(passengerPrice)));
        ticket.setValidatingCarrier(validatingCarrier(airlineOffers, offer));
        ticket.setProductType(productType(passengerPrices, context));
        ticket.setFreeBaggageList(freeBaggageConverter.convert(itinerary, passengerPrices, context));
        ticket.setRefundInfoList(fareRuleConverter.refunds(itinerary, passengerPrices, currency, roundTrip, context));
        ticket.setChangeInfoList(fareRuleConverter.changes(itinerary, passengerPrices, currency, roundTrip, context));
        ticket.setCurrency(currency);
        ticket.setTicketIssueTime(context.ticketIssueTime());
        ticket.setTag(tag);
        ticket.setOtherFareRules(otherFareRules(offer, passengerPrices, context));
        ticket.setFareBasis(fareBasis(itinerary, passengerPrices.getFirst(), context));
        return ticket;
    }

    private static String validatingCarrier(AirlineOffers airlineOffers, AirlineOffer offer) {
        return Optional.ofNullable(offer.getValidatingCarrier())
                .filter(StringUtils::isNotBlank)
                .or(() -> offerOwner(offer))
                .orElseGet(() -> Optional.ofNullable(airlineOffers.getOwner()).map(AirlineIDType::getValue).orElse(null));
    }

    private static Optional<String> offerOwner(AirlineOffer offer) {
        return Optional.ofNullable(offer.getOfferID()).map(ItemIDType::getOwner).filter(StringUtils::isNotBlank);
    }

    private static String productType(List<PassengerOfferPrice> passengerPrices, SearchMappingContext context) {
        Set<String> remarks = passengerPrices.stream()
                .flatMap(passengerPrice -> fareGroups(passengerPrice.offerPrice(), context))
                .flatMap(fareGroup -> Optional.ofNullable(fareGroup.getFare())
                        .map(FareList.FareGroup.Fare::getFareDetail)
                        .map(FareDetailType::getRemarks)
                        .stream())
                .flatMap(fareRemarks -> fareRemarks.getRemark().stream())
                .flatMap(remark -> Optional.ofNullable(remark.getValue()).stream())
                .map(remark -> remark.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (remarks.contains(VDC_PRIVATE_FARE_REMARK)) {
            return PK_PRIVATE_FARE;
        }
        if (remarks.contains(VDC_PUBLIC_FARE_REMARK)) {
            return PK_PUBLIC_FARE;
        }
        return context.defaultProductType();
    }

    /** One fare basis per segment in itinerary order, or nothing when any segment lacks one. */
    private static String fareBasis(List<ItinerarySegment> itinerary, PassengerOfferPrice passengerPrice, SearchMappingContext context) {
        return Optional.ofNullable(passengerPrice.offerPrice().getFareDetail())
                .map(fareDetail -> itinerary.stream().map(segment -> fareBasis(segment, fareDetail.getFareComponent(), context)).toList())
                .filter(fareBases -> fareBases.stream()
                        .allMatch(Optional::isPresent))
                .map(fareBases -> fareBases.stream()
                        .map(Optional::get)
                        .collect(Collectors.joining(FARE_BASIS_SEPARATOR)))
                .orElse(null);
    }

    /** Fare basis of the first fare component priced for this segment. */
    private static Optional<String> fareBasis(ItinerarySegment segment, List<FareComponentType> fareComponents, SearchMappingContext context) {
        return fareComponents.stream()
                .filter(component -> Optional.ofNullable(component.getSegmentReference())
                        .map(segmentReference -> segment.segmentKey().equals(RefUtil.key(segmentReference.getValue())))
                        .orElse(false))
                .flatMap(component -> fareBasis(component, context).stream())
                .findFirst()
                .filter(StringUtils::isNotBlank);
    }

    /** The component's own fare basis code, else the first one of the fare groups it refers to. */
    private static Optional<String> fareBasis(FareComponentType component, SearchMappingContext context) {
        return Optional.ofNullable(component.getFareBasis())
                .map(FareComponentType.FareBasis::getFareBasisCode)
                .map(FareBasisCodeType::getCode)
                .filter(StringUtils::isNotBlank)
                .or(() -> RefUtil.keys(component.getRefs()).stream()
                        .flatMap(key -> context.index().fareGroup(key).stream())
                        .flatMap(group -> Optional.ofNullable(group.getFareBasisCode())
                                .map(FareList.FareGroup.FareBasisCode::getCode).stream())
                        .filter(StringUtils::isNotBlank)
                        .findFirst());
    }

    private static Stream<FareList.FareGroup> fareGroups(PricedFlightOfferType.OfferPrice offerPrice, SearchMappingContext context) {
        return Optional.ofNullable(offerPrice.getFareDetail()).stream()
                .flatMap(fareDetail -> fareDetail.getFareComponent().stream())
                .flatMap(component -> RefUtil.keys(component.getRefs()).stream())
                .distinct()
                .flatMap(key -> context.index().fareGroup(key).stream());
    }

    private static List<PriceClassType> priceClasses(AirlineOffer offer, SearchMappingContext context) {
        return offer.getPricedOffer().getAssociations().stream()
                .flatMap(association -> Optional.ofNullable(association.getPriceClass())
                        .map(priceClass -> RefUtil.key(priceClass.getPriceClassReference()))
                        .stream())
                .distinct()
                .flatMap(key -> context.index().priceClass(key).stream())
                .toList();
    }

    private static List<String> priceClassNames(AirlineOffer offer, SearchMappingContext context) {
        return priceClasses(offer, context).stream()
                .map(priceClass -> isNotBlank(priceClass.getName()) ? priceClass.getName() : priceClass.getCode())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static boolean matchesTag(AirlineOffer offer, List<String> priceClassNames, SearchMappingContext context) {
        String requested = context.requestedTag().trim();
        return priceClassNames.stream().anyMatch(requested::equalsIgnoreCase)
                || priceClasses(offer, context).stream().anyMatch(priceClass -> requested.equalsIgnoreCase(priceClass.getCode()));
    }

    private static List<SearchOtherFareRule> otherFareRules(AirlineOffer offer, List<PassengerOfferPrice> passengerPrices, SearchMappingContext context) {
        List<String> details = priceClasses(offer, context).stream()
                .flatMap(priceClass -> Optional.ofNullable(priceClass.getDescriptions()).stream())
                .flatMap(descriptions -> descriptions.getDescription().stream())
                .flatMap(description -> Optional.ofNullable(description.getText()).map(DescriptionType.Text::getValue).stream())
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        return passengerPrices.stream().map(passengerPrice -> {
            SearchOtherFareRule rule = new SearchOtherFareRule();
            rule.setPassengerType(passengerPrice.passengerType());
            rule.setDetail(new ArrayList<>(details));
            return rule;
        }).toList();
    }

    private static OfferData offerData(AirlineOffers airlineOffers, AirlineOffer offer, List<ItinerarySegment> itinerary, SearchMappingContext context) {
        return new OfferData(
                ItineraryConverter.offerId(offer),
                validatingCarrierOrOwner(airlineOffers, offer),
                Optional.ofNullable(context.response().getShoppingResponseID())
                        .map(ShoppingResponseIDType::getResponseID)
                        .map(ShoppingResponseIDType.ResponseID::getValue)
                        .orElse(null),
                offer.getPricedOffer().getOfferPrice().stream().map(PricedFlightOfferType.OfferPrice::getOfferItemID).filter(Objects::nonNull).toList(),
                itinerary.stream().map(ItinerarySegment::segmentKey).toList());
    }

    private static String validatingCarrierOrOwner(AirlineOffers airlineOffers, AirlineOffer offer) {
        return offerOwner(offer).orElseGet(() -> validatingCarrier(airlineOffers, offer));
    }
}
