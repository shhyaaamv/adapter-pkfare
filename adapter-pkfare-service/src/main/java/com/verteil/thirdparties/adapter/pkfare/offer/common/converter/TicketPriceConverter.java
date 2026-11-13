package com.verteil.thirdparties.adapter.pkfare.offer.common.converter;

import com.verteil.schemas.ndc16_1.OfferPriceLeadDetailType;
import com.verteil.schemas.ndc16_1.OfferPriceLeadType;
import com.verteil.thirdparties.adapter.pkfare.common.utils.NdcUtil;
import com.verteil.thirdparties.adapter.pkfare.common.utils.RefUtil;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.DataPassenger;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.DataListsIndex;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PassengerFare;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PassengerOfferPrice;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PricingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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
 * OfferPrice[] (one per PTC, per-passenger amounts) -> per passenger type fares.
 * <pre>
 * passengerType  PTC of AssociatedTraveler.TravelerReferences (ADT 0, CHD/CNN 1, INF 2)
 * fare           PriceDetail.BaseAmount
 * tax            PriceDetail.TotalAmount - BaseAmount (all taxes, fees and surcharges), else Taxes.Total
 * </pre>
 */
@Slf4j
@Component
public class TicketPriceConverter {

    private static final Map<String, Integer> PTC_TO_PASSENGER_TYPE = Map.of(
            PTC_ADULT, PK_PASSENGER_ADULT, PTC_CHILD, PK_PASSENGER_CHILD, PTC_CHILD_ALT, PK_PASSENGER_CHILD, PTC_INFANT, PK_PASSENGER_INFANT);
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(NdcUtil.AMOUNT_SCALE);

    /**
     * @param offerId for logging only
     * @return one OfferPrice per requested passenger type, ordered adult/child/infant; empty when the offer does not
     * price every requested type or prices a type PKFARE cannot represent.
     */
    public Optional<List<PassengerOfferPrice>> resolve(String offerId, List<? extends OfferPriceLeadType> offerPrices, PricingContext context) {
        Optional<? extends OfferPriceLeadType> unsupported = offerPrices.stream()
                .filter(offerPrice -> mapPassengerType(offerPrice, context).isEmpty())
                .findFirst();
        if (unsupported.isPresent()) {
            log.warn("Offer {}: OfferPrice {} has unsupported/unknown PTC {}, offer skipped",
                    offerId, unsupported.get().getOfferItemID(), mapPtc(unsupported.get(), context).orElse(null));
            return Optional.empty();
        }
        // The first OfferPrice of each passenger type wins.
        Map<Integer, PassengerOfferPrice> byType = offerPrices.stream()
                .map(offerPrice -> new PassengerOfferPrice(mapPassengerType(offerPrice, context).orElseThrow(), offerPrice))
                .collect(Collectors.toMap(PassengerOfferPrice::passengerType, Function.identity(), (first, _) -> first, TreeMap::new));
        if (!byType.keySet().equals(context.requestedPassengerTypes())) {
            log.warn("Offer {} prices passenger types {} but {} were requested, offer skipped",
                    offerId, byType.keySet(), context.requestedPassengerTypes());
            return Optional.empty();
        }
        return Optional.of(List.copyOf(byType.values()));
    }

    public PassengerFare mapFare(PassengerOfferPrice passengerOfferPrice) {
        var priceDetail = passengerOfferPrice.offerPrice().getRequestedDate().getPriceDetail();
        BigDecimal fare = requireNonNullElse(NdcUtil.amount(priceDetail.getBaseAmount()), ZERO_AMOUNT);
        BigDecimal tax = requireNonNullElse(NdcUtil.scale(mapTax(priceDetail, fare)), ZERO_AMOUNT);
        if (tax.signum() == 0) {
            log.warn("OfferItem {} has zero tax; PKFARE requires tax to be non-zero", passengerOfferPrice.offerPrice().getOfferItemID());
        }
        return new PassengerFare(passengerOfferPrice.passengerType(), fare, tax);
    }

    private static BigDecimal mapTax(OfferPriceLeadDetailType.PriceDetail priceDetail, BigDecimal fare) {
        return NdcUtil.totalAmount(priceDetail)
                .map(NdcUtil::amount)
                .map(total -> total.subtract(fare))
                .orElseGet(() -> mapTaxesTotal(priceDetail));
    }

    private static BigDecimal mapTaxesTotal(OfferPriceLeadDetailType.PriceDetail priceDetail) {
        return Optional.ofNullable(priceDetail.getTaxes())
                .map(taxes -> NdcUtil.amount(taxes.getTotal()))
                .orElse(BigDecimal.ZERO);
    }

    /** Currency of the first OfferPrice total that carries one; null when none does. */
    public static String mapCurrency(List<? extends OfferPriceLeadType> offerPrices) {
        return offerPrices.stream()
                .flatMap(offerPrice -> Optional.ofNullable(offerPrice.getRequestedDate()).stream())
                .flatMap(requestedDate -> NdcUtil.totalAmount(requestedDate.getPriceDetail()).stream())
                .flatMap(amount -> Optional.ofNullable(amount.getCode()).stream())
                .findFirst()
                .orElse(null);
    }

    /** The OfferPrice's offer data passenger: OfferItemID, PTC of its first known traveler, distinct traveler refs. */
    public static DataPassenger mapDataPassenger(OfferPriceLeadType offerPrice, DataListsIndex index) {
        List<String> travelerRefs = mapTravelerKeys(offerPrice).stream()
                .distinct()
                .toList();
        String ptc = travelerRefs.stream()
                .flatMap(travelerRef -> index.travelerPtc(travelerRef).stream())
                .findFirst()
                .orElse(null);
        return new DataPassenger(offerPrice.getOfferItemID(), ptc, travelerRefs);
    }

    /** PKFARE passenger type of the OfferPrice; empty when its PTC is unknown or not representable in PKFARE. */
    private static Optional<Integer> mapPassengerType(OfferPriceLeadType offerPrice, PricingContext context) {
        return mapPtc(offerPrice, context).map(PTC_TO_PASSENGER_TYPE::get);
    }

    private static Optional<String> mapPtc(OfferPriceLeadType offerPrice, PricingContext context) {
        return mapTravelerKeys(offerPrice).stream()
                .flatMap(key -> context.index().travelerPtc(key).stream())
                .findFirst();
    }

    private static List<String> mapTravelerKeys(OfferPriceLeadType offerPrice) {
        return Optional.ofNullable(offerPrice.getRequestedDate()).stream()
                .flatMap(requestedDate -> requestedDate.getAssociations().stream())
                .flatMap(association -> Optional.ofNullable(association.getAssociatedTraveler()).stream())
                .flatMap(traveler -> RefUtil.keys(traveler.getTravelerReferences()).stream())
                .toList();
    }
}
