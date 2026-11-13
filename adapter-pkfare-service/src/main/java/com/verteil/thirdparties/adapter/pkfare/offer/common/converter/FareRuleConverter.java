package com.verteil.thirdparties.adapter.pkfare.offer.common.converter;

import com.verteil.schemas.ndc16_1.CurrencyAmountOptType;
import com.verteil.schemas.ndc16_1.FareComponentType;
import com.verteil.schemas.ndc16_1.FareRulesType;
import com.verteil.schemas.ndc16_1.PenaltyDetailType;
import com.verteil.schemas.ndc16_1.PenaltyType;
import com.verteil.thirdparties.adapter.pkfare.common.utils.RefUtil;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.DataListsIndex;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.ItinerarySegment;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PassengerOfferPrice;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PenaltyRule;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.RuleCondition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_DIRECTION_RETURN;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_REFUND_CHANGE_ENTIRE_UNUSED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_REFUND_CHANGE_PARTIALLY_USED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_AMOUNT_APPLICATION_MAX;
import static java.util.Objects.nonNull;

/**
 * OfferPrice FareDetail.FareComponent[].FareRules.Penalty (refs into PenaltyList, or inline details)
 * -> {@link RuleCondition}s (PKFARE {@code refundInfoList[]} / {@code changeInfoList[]}), per passenger type.
 * <ul>
 *   <li>type 0 (ticket entirely unused) uses the penalties of every direction; type 1 (outbound flown, return
 *   refunded/changed) — round trips only — uses the return direction's penalties.</li>
 *   <li>allowed when every involved direction allows it ({@code RefundableInd} / {@code ChangeAllowedInd}, or a
 *   Cancel/Refund resp. Change detail when the indicator is absent); fee = sum over directions of that direction's
 *   highest MAX amount, rounded up to whole units.</li>
 *   <li>VDC carries no structured time windows, so a single condition spans the whole period.</li>
 *   <li>no penalty information at all -> not refundable / not changeable.</li>
 *   <li>a fare component's segment is its SegmentReference, else its first segment {@code refs} key
 *   ({@link DataListsIndex#segmentKeyOf}).</li>
 * </ul>
 */
@Component
public class FareRuleConverter {

    public List<RuleCondition> mapRefunds(List<ItinerarySegment> itinerary, List<PassengerOfferPrice> passengerPrices,
                                          String currency, boolean roundTrip, DataListsIndex index) {
        return mapConditions(itinerary, passengerPrices, currency, roundTrip, index, PenaltyRule.REFUND);
    }

    public List<RuleCondition> mapChanges(List<ItinerarySegment> itinerary, List<PassengerOfferPrice> passengerPrices,
                                          String currency, boolean roundTrip, DataListsIndex index) {
        return mapConditions(itinerary, passengerPrices, currency, roundTrip, index, PenaltyRule.CHANGE);
    }

    private static List<RuleCondition> mapConditions(List<ItinerarySegment> itinerary,
                                                     List<PassengerOfferPrice> passengerPrices, String currency,
                                                     boolean roundTrip, DataListsIndex index, PenaltyRule rule) {
        return passengerPrices.stream()
                .flatMap(passengerPrice ->
                        mapPassengerConditions(passengerPrice, itinerary, currency, roundTrip, index, rule))
                .toList();
    }

    private static Stream<RuleCondition> mapPassengerConditions(PassengerOfferPrice passengerPrice,
                                                                List<ItinerarySegment> itinerary, String currency,
                                                                boolean roundTrip, DataListsIndex index,
                                                                PenaltyRule rule) {
        Map<Integer, List<PenaltyType>> byDirection = mapPenaltiesByDirection(itinerary, passengerPrice, index);
        return mapRefundChangeTypes(roundTrip).stream()
                .map(type -> mapCondition(passengerPrice.passengerType(), type, byDirection,
                        mapDirections(type, byDirection.keySet()), rule, currency));
    }

    private static List<Integer> mapRefundChangeTypes(boolean roundTrip) {
        return roundTrip ? List.of(PK_REFUND_CHANGE_ENTIRE_UNUSED, PK_REFUND_CHANGE_PARTIALLY_USED) : List.of(PK_REFUND_CHANGE_ENTIRE_UNUSED);
    }

    private static Collection<Integer> mapDirections(int type, Set<Integer> available) {
        return type == PK_REFUND_CHANGE_PARTIALLY_USED ? List.of(PK_DIRECTION_RETURN) : available;
    }

    private static RuleCondition mapCondition(int passengerType, int type, Map<Integer, List<PenaltyType>> byDirection,
                                              Collection<Integer> directions, PenaltyRule rule, String currency) {
        List<List<PenaltyType>> penaltiesPerDirection = mapPenaltiesPerDirection(directions, byDirection, rule);
        if (!allDirectionsAllow(penaltiesPerDirection, rule)) {
            return new RuleCondition(passengerType, type, false, 0, currency);
        }
        // Each direction contributes its highest MAX amount; the fee currency is the last one given.
        List<CurrencyAmountOptType> maxAmounts = mapHighestAmounts(penaltiesPerDirection, rule);
        BigDecimal fee = maxAmounts.stream()
                .map(CurrencyAmountOptType::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String feeCurrency = mapFeeCurrency(maxAmounts, currency);
        return new RuleCondition(passengerType, type, true,
                fee.setScale(0, RoundingMode.CEILING).intValueExact(), feeCurrency);
    }

    private static List<List<PenaltyType>> mapPenaltiesPerDirection(Collection<Integer> directions,
                                                                    Map<Integer, List<PenaltyType>> byDirection,
                                                                    PenaltyRule rule) {
        return directions.stream()
                .map(direction -> byDirection.getOrDefault(direction, List.of()).stream()
                        .filter(penalty -> applies(penalty, rule))
                        .toList())
                .toList();
    }

    private static boolean allDirectionsAllow(List<List<PenaltyType>> penaltiesPerDirection, PenaltyRule rule) {
        return !penaltiesPerDirection.isEmpty() && penaltiesPerDirection.stream()
                .allMatch(penalties -> !penalties.isEmpty()
                        && penalties.stream().allMatch(penalty -> isAllowed(penalty, rule)));
    }

    private static List<CurrencyAmountOptType> mapHighestAmounts(List<List<PenaltyType>> penaltiesPerDirection,
                                                                 PenaltyRule rule) {
        return penaltiesPerDirection.stream()
                .flatMap(penalties -> penalties.stream()
                        .flatMap(penalty -> mapMaxAmounts(penalty, rule))
                        .max(Comparator.comparing(CurrencyAmountOptType::getValue))
                        .stream())
                .toList();
    }

    private static String mapFeeCurrency(List<CurrencyAmountOptType> maxAmounts, String currency) {
        return maxAmounts.stream()
                .map(CurrencyAmountOptType::getCode)
                .filter(Objects::nonNull)
                .reduce((_, last) -> last)
                .orElse(currency);
    }

    private static boolean applies(PenaltyType penalty, PenaltyRule rule) {
        return mapDetails(penalty).anyMatch(detail -> rule.isDetailType(detail.getType()))
                || nonNull(mapIndicator(penalty, rule));
    }

    private static boolean isAllowed(PenaltyType penalty, PenaltyRule rule) {
        return Optional.ofNullable(mapIndicator(penalty, rule)).orElse(true);
    }

    private static Boolean mapIndicator(PenaltyType penalty, PenaltyRule rule) {
        return rule == PenaltyRule.REFUND ? penalty.isRefundableInd() : penalty.isChangeAllowedInd();
    }

    private static Stream<CurrencyAmountOptType> mapMaxAmounts(PenaltyType penalty, PenaltyRule rule) {
        return mapDetails(penalty)
                .filter(detail -> rule.isDetailType(detail.getType()))
                .flatMap(detail -> Optional.ofNullable(detail.getAmounts()).stream())
                .flatMap(amounts -> amounts.getAmount().stream())
                .filter(amount -> Optional.ofNullable(amount.getAmountApplication())
                        .map(VDC_AMOUNT_APPLICATION_MAX::equalsIgnoreCase)
                        .orElse(true))
                .flatMap(amount -> Optional.ofNullable(amount.getCurrencyAmountValue()).stream())
                .filter(amount -> nonNull(amount.getValue()));
    }

    private static Stream<PenaltyType.Details.Detail> mapDetails(PenaltyType penalty) {
        return Optional.ofNullable(penalty.getDetails())
                .stream()
                .flatMap(details -> details.getDetail().stream());
    }

    /**
     * Distinct penalties (PenaltyList entries, or inline ones) per travel direction for this passenger type. A
     * direction whose fare components carry a Penalty is present even when none of it resolves (empty list).
     */
    private static Map<Integer, List<PenaltyType>> mapPenaltiesByDirection(List<ItinerarySegment> itinerary, PassengerOfferPrice passengerPrice,
                                                                           DataListsIndex index) {
        Map<String, Integer> directionOfSegment = itinerary.stream()
                .collect(Collectors.toMap(ItinerarySegment::segmentKey, ItinerarySegment::travelDirection, (_, last) -> last));
        return Optional.ofNullable(passengerPrice.offerPrice().getFareDetail()).stream()
                .flatMap(fareDetail -> fareDetail.getFareComponent().stream())
                .flatMap(component -> mapComponentPenalties(component, directionOfSegment, index).stream())
                .collect(Collectors.groupingBy(ComponentPenalties::direction, TreeMap::new, distinctPenalties()));
    }

    private static Collector<ComponentPenalties, ?, List<PenaltyType>> distinctPenalties() {
        return Collectors.collectingAndThen(
                Collectors.flatMapping(componentPenalties -> componentPenalties.penalties().stream(),
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, _) -> first,
                                LinkedHashMap::new)),
                penalties -> List.copyOf(penalties.values()));
    }

    /** One fare component's penalties, keyed by PenaltyList key (or {@code INLINE-<segment>}), in its segment's direction. */
    private record ComponentPenalties(int direction, List<Map.Entry<String, PenaltyType>> penalties) {}

    private static Optional<ComponentPenalties> mapComponentPenalties(FareComponentType component, Map<String, Integer> directionOfSegment,
                                                                      DataListsIndex index) {
        String segmentKey = index.segmentKeyOf(component).orElse(null);
        Optional<Integer> direction = Optional.ofNullable(segmentKey).map(directionOfSegment::get);
        return Optional.ofNullable(component.getFareRules())
                .map(FareRulesType::getPenalty)
                .flatMap(penalty -> direction.map(travelDirection -> new ComponentPenalties(travelDirection,
                        Stream.concat(mapListedPenalties(penalty, index), mapInlinePenalty(penalty, segmentKey)).toList())));
    }

    private static Stream<Map.Entry<String, PenaltyType>> mapListedPenalties(PenaltyDetailType penalty,
                                                                             DataListsIndex index) {
        return RefUtil.keys(penalty.getRefs()).stream()
                .flatMap(key -> index.penalty(key).stream().map(found -> Map.entry(key, found)));
    }

    private static Stream<Map.Entry<String, PenaltyType>> mapInlinePenalty(PenaltyDetailType penalty, String segmentKey) {
        return Optional.ofNullable(penalty.getDetails()).stream()
                .map(_ -> Map.entry("INLINE-" + segmentKey, penalty));
    }
}
