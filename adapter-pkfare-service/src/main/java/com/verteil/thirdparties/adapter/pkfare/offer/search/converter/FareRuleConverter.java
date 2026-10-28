package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.verteil.schema.pkfare.SearchChangeCondition;
import com.verteil.schema.pkfare.SearchChangeInfo;
import com.verteil.schema.pkfare.SearchRefundCondition;
import com.verteil.schema.pkfare.SearchRefundInfo;
import com.verteil.schemas.ndc16_1.CurrencyAmountOptType;
import com.verteil.schemas.ndc16_1.FareComponentType;
import com.verteil.schemas.ndc16_1.PenaltyType;
import com.verteil.thirdparties.adapter.pkfare.common.utils.RefUtil;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.ItinerarySegment;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.PassengerOfferPrice;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.SearchMappingContext;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_DIRECTION_RETURN;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_NO_TIME_LIMIT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_REFUND_CHANGE_ENTIRE_UNUSED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_REFUND_CHANGE_PARTIALLY_USED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_TIME_UNIT_HOUR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_AMOUNT_APPLICATION_MAX;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_PENALTY_CANCEL;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_PENALTY_CHANGE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_PENALTY_REFUND;
import static java.util.Objects.nonNull;

/**
 * OfferPrice FareDetail.FareComponent[].FareRules.Penalty (refs into PenaltyList, or inline details)
 * -> ticketList[].refundInfoList[] / changeInfoList[], per passenger type.
 * <ul>
 *   <li>type 0 (ticket entirely unused) uses the penalties of every direction; type 1 (outbound flown, return
 *   refunded/changed) — round trips only — uses the return direction's penalties.</li>
 *   <li>allowed when every involved direction allows it ({@code RefundableInd} / {@code ChangeAllowedInd}, or a
 *   Cancel/Refund resp. Change detail when the indicator is absent); fee = sum over directions of that direction's
 *   highest MAX amount, rounded up to whole units.</li>
 *   <li>VDC carries no structured time windows, so a single condition spans the whole period (start/end -1).</li>
 *   <li>no penalty information at all -> not refundable / not changeable.</li>
 * </ul>
 */
@Component
public class FareRuleConverter {

    private enum Rule {
        REFUND(type -> VDC_PENALTY_CANCEL.equalsIgnoreCase(type) || VDC_PENALTY_REFUND.equalsIgnoreCase(type)),
        CHANGE(VDC_PENALTY_CHANGE::equalsIgnoreCase);

        private final Predicate<String> detailType;

        Rule(Predicate<String> detailType) {
            this.detailType = detailType;
        }
    }

    private record Condition(boolean allowed, int fee, String currency) {}

    public List<SearchRefundInfo> refunds(List<ItinerarySegment> itinerary, List<PassengerOfferPrice> passengerPrices,
                                          String currency, boolean roundTrip, SearchMappingContext context) {
        return passengerPrices.stream().flatMap(passengerPrice -> {
            Map<Integer, List<PenaltyType>> byDirection = penaltiesByDirection(itinerary, passengerPrice, context);
            return refundChangeTypes(roundTrip).stream().map(type -> refundInfo(passengerPrice.passengerType(), type,
                    condition(byDirection, directions(type, byDirection.keySet()), Rule.REFUND, currency)));
        }).toList();
    }

    public List<SearchChangeInfo> changes(List<ItinerarySegment> itinerary, List<PassengerOfferPrice> passengerPrices,
                                          String currency, boolean roundTrip, SearchMappingContext context) {
        return passengerPrices.stream().flatMap(passengerPrice -> {
            Map<Integer, List<PenaltyType>> byDirection = penaltiesByDirection(itinerary, passengerPrice, context);
            return refundChangeTypes(roundTrip).stream().map(type -> changeInfo(passengerPrice.passengerType(), type,
                    condition(byDirection, directions(type, byDirection.keySet()), Rule.CHANGE, currency)));
        }).toList();
    }

    private static SearchRefundInfo refundInfo(int passengerType, int type, Condition condition) {
        SearchRefundCondition refundCondition = new SearchRefundCondition();
        refundCondition.setStart(PK_NO_TIME_LIMIT);
        refundCondition.setEnd(PK_NO_TIME_LIMIT);
        refundCondition.setUnit(PK_TIME_UNIT_HOUR);
        refundCondition.setRefundable(condition.allowed());
        refundCondition.setRefundFee(condition.fee());
        refundCondition.setCurrency(condition.currency());
        SearchRefundInfo refundInfo = new SearchRefundInfo();
        refundInfo.setPassengerType(passengerType);
        refundInfo.setRefundType(type);
        refundInfo.getConditionList().add(refundCondition);
        return refundInfo;
    }

    private static SearchChangeInfo changeInfo(int passengerType, int type, Condition condition) {
        SearchChangeCondition changeCondition = new SearchChangeCondition();
        changeCondition.setStart(PK_NO_TIME_LIMIT);
        changeCondition.setEnd(PK_NO_TIME_LIMIT);
        changeCondition.setUnit(PK_TIME_UNIT_HOUR);
        changeCondition.setChangeable(condition.allowed());
        changeCondition.setChangeFee(condition.fee());
        changeCondition.setCurrency(condition.currency());
        SearchChangeInfo changeInfo = new SearchChangeInfo();
        changeInfo.setPassengerType(passengerType);
        changeInfo.setChangeType(type);
        changeInfo.getConditionList().add(changeCondition);
        return changeInfo;
    }

    private static List<Integer> refundChangeTypes(boolean roundTrip) {
        return roundTrip ? List.of(PK_REFUND_CHANGE_ENTIRE_UNUSED, PK_REFUND_CHANGE_PARTIALLY_USED) : List.of(PK_REFUND_CHANGE_ENTIRE_UNUSED);
    }

    private static Collection<Integer> directions(int type, Set<Integer> available) {
        return type == PK_REFUND_CHANGE_PARTIALLY_USED ? List.of(PK_DIRECTION_RETURN) : available;
    }

    private static Condition condition(Map<Integer, List<PenaltyType>> byDirection, Collection<Integer> directions, Rule rule, String currency) {
        List<List<PenaltyType>> penaltiesPerDirection = directions.stream()
                .map(direction -> byDirection.getOrDefault(direction, List.of()).stream()
                        .filter(penalty -> applies(penalty, rule))
                        .toList())
                .toList();
        boolean allowed = !penaltiesPerDirection.isEmpty() && penaltiesPerDirection.stream()
                .allMatch(penalties -> !penalties.isEmpty() && penalties.stream().allMatch(penalty -> isAllowed(penalty, rule)));
        if (!allowed) {
            return new Condition(false, 0, currency);
        }
        // Each direction contributes its highest MAX amount; the fee currency is the last one given.
        List<CurrencyAmountOptType> maxAmounts = penaltiesPerDirection.stream()
                .flatMap(penalties -> penalties.stream()
                        .flatMap(penalty -> maxAmounts(penalty, rule))
                        .max(Comparator.comparing(CurrencyAmountOptType::getValue))
                        .stream())
                .toList();
        BigDecimal fee = maxAmounts.stream().map(CurrencyAmountOptType::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        String feeCurrency = maxAmounts.stream()
                .map(CurrencyAmountOptType::getCode)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse(currency);
        return new Condition(true, fee.setScale(0, RoundingMode.CEILING).intValueExact(), feeCurrency);
    }

    private static boolean applies(PenaltyType penalty, Rule rule) {
        return details(penalty).anyMatch(detail -> rule.detailType.test(detail.getType()))
                || Optional.ofNullable(indicator(penalty, rule)).isPresent();
    }

    private static boolean isAllowed(PenaltyType penalty, Rule rule) {
        return Optional.ofNullable(indicator(penalty, rule)).orElse(true);
    }

    private static Boolean indicator(PenaltyType penalty, Rule rule) {
        return rule == Rule.REFUND ? penalty.isRefundableInd() : penalty.isChangeAllowedInd();
    }

    private static Stream<CurrencyAmountOptType> maxAmounts(PenaltyType penalty, Rule rule) {
        return details(penalty)
                .filter(detail -> rule.detailType.test(detail.getType()))
                .flatMap(detail -> Optional.ofNullable(detail.getAmounts()).stream())
                .flatMap(amounts -> amounts.getAmount().stream())
                .filter(amount -> Optional.ofNullable(amount.getAmountApplication()).map(VDC_AMOUNT_APPLICATION_MAX::equalsIgnoreCase).orElse(true))
                .flatMap(amount -> Optional.ofNullable(amount.getCurrencyAmountValue()).stream())
                .filter(amount -> nonNull(amount.getValue()));
    }

    private static Stream<PenaltyType.Details.Detail> details(PenaltyType penalty) {
        return Optional.ofNullable(penalty.getDetails()).stream().flatMap(details -> details.getDetail().stream());
    }

    /**
     * Distinct penalties (PenaltyList entries, or inline ones) per travel direction for this passenger type. A
     * direction whose fare components carry a Penalty is present even when none of it resolves (empty list).
     */
    private static Map<Integer, List<PenaltyType>> penaltiesByDirection(List<ItinerarySegment> itinerary, PassengerOfferPrice passengerPrice,
                                                                        SearchMappingContext context) {
        Map<String, Integer> directionOfSegment = itinerary.stream()
                .collect(Collectors.toMap(ItinerarySegment::segmentKey, ItinerarySegment::travelDirection, (_, last) -> last));
        return Optional.ofNullable(passengerPrice.offerPrice().getFareDetail()).stream()
                .flatMap(fareDetail -> fareDetail.getFareComponent().stream())
                .flatMap(component -> componentPenalties(component, directionOfSegment, context).stream())
                .collect(Collectors.groupingBy(ComponentPenalties::direction, TreeMap::new,
                        Collectors.collectingAndThen(
                                Collectors.flatMapping(componentPenalties -> componentPenalties.penalties().stream(),
                                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, _) -> first, LinkedHashMap::new)),
                                penalties -> List.copyOf(penalties.values()))));
    }

    /** One fare component's penalties, keyed by PenaltyList key (or {@code INLINE-<segment>}), in its segment's direction. */
    private record ComponentPenalties(int direction, List<Map.Entry<String, PenaltyType>> penalties) {}

    private static Optional<ComponentPenalties> componentPenalties(FareComponentType component, Map<String, Integer> directionOfSegment,
                                                                   SearchMappingContext context) {
        String segmentKey = Optional.ofNullable(component.getSegmentReference())
                .map(segmentReference -> RefUtil.key(segmentReference.getValue()))
                .orElse(null);
        Optional<Integer> direction = Optional.ofNullable(segmentKey).map(directionOfSegment::get);
        return Optional.ofNullable(component.getFareRules())
                .map(fareRules -> fareRules.getPenalty())
                .flatMap(penalty -> direction.map(travelDirection -> new ComponentPenalties(travelDirection, Stream.concat(
                        RefUtil.keys(penalty.getRefs()).stream()
                                .flatMap(key -> context.index().penalty(key).stream().map(found -> Map.entry(key, found))),
                        Optional.ofNullable(penalty.getDetails()).stream()
                                .map(_ -> Map.<String, PenaltyType>entry("INLINE-" + segmentKey, penalty)))
                        .toList())));
    }
}
