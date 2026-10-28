package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.verteil.schema.pkfare.SearchPrice;
import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schemas.ndc16_1.AirShoppingRS.OffersGroup.AirlineOffers.AirlineOffer;
import com.verteil.schemas.ndc16_1.PricedFlightOfferType;
import com.verteil.thirdparties.adapter.pkfare.common.utils.NdcUtil;
import com.verteil.thirdparties.adapter.pkfare.common.utils.RefUtil;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.PassengerOfferPrice;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.SearchMappingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_PASSENGER_ADULT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_PASSENGER_CHILD;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_PASSENGER_INFANT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PTC_ADULT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PTC_CHILD;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PTC_CHILD_ALT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PTC_INFANT;
import static java.util.Objects.requireNonNullElse;

/**
 * OfferPrice[] (one per PTC, per-passenger amounts) -> ticketList[].priceList[].
 * <pre>
 * passengerType  PTC of AssociatedTraveler.TravelerReferences (ADT 0, CHD/CNN 1, INF 2)
 * fare / maxFare PriceDetail.BaseAmount
 * tax            PriceDetail.TotalAmount - BaseAmount (all taxes, fees and surcharges), else Taxes.Total
 * </pre>
 */
@Slf4j
@Component
public class TicketPriceConverter {

    private static final Map<String, Integer> PTC_TO_PASSENGER_TYPE = Map.of(
            PTC_ADULT, PK_PASSENGER_ADULT, PTC_CHILD, PK_PASSENGER_CHILD, PTC_CHILD_ALT, PK_PASSENGER_CHILD, PTC_INFANT, PK_PASSENGER_INFANT);

    /**
     * @return one OfferPrice per requested passenger type, ordered adult/child/infant; empty when the offer does not
     * price every requested type or prices a type PKFARE cannot represent.
     */
    public Optional<List<PassengerOfferPrice>> resolve(AirlineOffer offer, SearchMappingContext context) {
        List<PricedFlightOfferType.OfferPrice> offerPrices = offer.getPricedOffer().getOfferPrice();
        Optional<PricedFlightOfferType.OfferPrice> unsupported = offerPrices.stream()
                .filter(offerPrice -> passengerType(offerPrice, context).isEmpty())
                .findFirst();
        if (unsupported.isPresent()) {
            log.warn("Offer {}: OfferPrice {} has unsupported/unknown PTC {}, offer skipped",
                    ItineraryConverter.offerId(offer), unsupported.get().getOfferItemID(), ptc(unsupported.get(), context).orElse(null));
            return Optional.empty();
        }
        // The first OfferPrice of each passenger type wins.
        Map<Integer, PassengerOfferPrice> byType = offerPrices.stream()
                .map(offerPrice -> new PassengerOfferPrice(passengerType(offerPrice, context).orElseThrow(), offerPrice))
                .collect(Collectors.toMap(PassengerOfferPrice::passengerType, Function.identity(), (first, _) -> first, TreeMap::new));
        if (!byType.keySet().equals(requestedTypes(context.request()))) {
            log.warn("Offer {} prices passenger types {} but {} were requested, offer skipped",
                    ItineraryConverter.offerId(offer), byType.keySet(), requestedTypes(context.request()));
            return Optional.empty();
        }
        return Optional.of(List.copyOf(byType.values()));
    }

    public SearchPrice convert(PassengerOfferPrice passengerOfferPrice) {
        var priceDetail = passengerOfferPrice.offerPrice().getRequestedDate().getPriceDetail();
        BigDecimal fare = requireNonNullElse(NdcUtil.amount(priceDetail.getBaseAmount()), BigDecimal.ZERO.setScale(NdcUtil.AMOUNT_SCALE));
        BigDecimal tax = NdcUtil.totalAmount(priceDetail)
                .map(NdcUtil::amount)
                .map(total -> total.subtract(fare))
                .orElseGet(() -> Optional.ofNullable(priceDetail.getTaxes())
                        .map(taxes -> NdcUtil.amount(taxes.getTotal()))
                        .orElse(BigDecimal.ZERO));
        tax = requireNonNullElse(NdcUtil.scale(tax), BigDecimal.ZERO.setScale(NdcUtil.AMOUNT_SCALE));
        if (tax.signum() == 0) {
            log.warn("OfferItem {} has zero tax; PKFARE requires tax to be non-zero", passengerOfferPrice.offerPrice().getOfferItemID());
        }
        SearchPrice price = new SearchPrice();
        price.setPassengerType(passengerOfferPrice.passengerType());
        price.setFare(fare);
        price.setMaxFare(fare);
        price.setTax(tax);
        return price;
    }

    /** Currency of the offer: TotalPrice, else the first OfferPrice total. */
    public static String currency(AirlineOffer offer) {
        return Optional.ofNullable(offer.getTotalPrice())
                .map(totalPrice -> totalPrice.getSimpleCurrencyPrice())
                .map(simpleCurrencyPrice -> simpleCurrencyPrice.getCode())
                .or(() -> offer.getPricedOffer().getOfferPrice().stream()
                        .flatMap(offerPrice -> Optional.ofNullable(offerPrice.getRequestedDate()).stream())
                        .flatMap(requestedDate -> NdcUtil.totalAmount(requestedDate.getPriceDetail()).stream())
                        .flatMap(amount -> Optional.ofNullable(amount.getCode()).stream())
                        .findFirst())
                .orElse(null);
    }

    /** PKFARE passenger type of the OfferPrice; empty when its PTC is unknown or not representable in PKFARE. */
    private static Optional<Integer> passengerType(PricedFlightOfferType.OfferPrice offerPrice, SearchMappingContext context) {
        return ptc(offerPrice, context).map(PTC_TO_PASSENGER_TYPE::get);
    }

    private static Optional<String> ptc(PricedFlightOfferType.OfferPrice offerPrice, SearchMappingContext context) {
        return travelerKeys(offerPrice).stream()
                .flatMap(key -> context.index().travelerPtc(key).stream())
                .findFirst();
    }

    private static List<String> travelerKeys(PricedFlightOfferType.OfferPrice offerPrice) {
        return Optional.ofNullable(offerPrice.getRequestedDate()).stream()
                .flatMap(requestedDate -> requestedDate.getAssociations().stream())
                .flatMap(association -> Optional.ofNullable(association.getAssociatedTraveler()).stream())
                .flatMap(traveler -> RefUtil.keys(traveler.getTravelerReferences()).stream())
                .toList();
    }

    private static Set<Integer> requestedTypes(SearchRequest request) {
        Set<Integer> types = new TreeSet<>();
        if (requireNonNullElse(request.getAdultNumber(), 0) > 0) types.add(PK_PASSENGER_ADULT);
        if (requireNonNullElse(request.getChildNumber(), 0) > 0) types.add(PK_PASSENGER_CHILD);
        if (requireNonNullElse(request.getInfantNumber(), 0) > 0) types.add(PK_PASSENGER_INFANT);
        return types;
    }
}
