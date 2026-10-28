package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.verteil.schema.pkfare.SearchFreeBaggage;
import com.verteil.schemas.ndc16_1.BagAllowanceDimensionType;
import com.verteil.schemas.ndc16_1.BagAllowanceWeightType;
import com.verteil.schemas.ndc16_1.FlightSegmentReference;
import com.verteil.schemas.ndc16_1.PieceAllowance;
import com.verteil.schemas.ndc16_1.SizeUnitSimpleType;
import com.verteil.schemas.ndc16_1.WeightAllowance;
import com.verteil.schemas.ndc16_1.WeightUnitSimpleType;
import com.verteil.thirdparties.adapter.pkfare.common.utils.RefUtil;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.ItinerarySegment;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.PassengerOfferPrice;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.SearchMappingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_NOT_APPLICABLE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_NO_BAGGAGE_PIECE;
import static java.util.Objects.nonNull;

/**
 * Per passenger type and segment, OfferPrice associations' BagDetailAssociation -> ticketList[].freeBaggageList[].
 * <pre>
 * checked (CheckedBagReferences -> CheckedBagAllowanceList)
 *   no baggage      baggagePiece 0,  baggageWeight -1
 *   weight concept  baggagePiece -1, baggageWeight kg
 *   piece concept   baggagePiece n,  baggageWeight -1
 *   both            baggagePiece n,  baggageWeight kg
 * carry-on (CarryOnReferences -> CarryOnAllowanceList): carryOnPiece / carryOnWeight (0 when none),
 *   carryOnSize [L, W, H] cm when the allowance carries all three dimensions
 * </pre>
 * A segment with no checked-bag reference is reported as "no baggage" (never over-promise an allowance).
 */
@Slf4j
@Component
public class FreeBaggageConverter {

    private static final BigDecimal KG_PER_POUND = new BigDecimal("0.45359237");
    private static final BigDecimal CM_PER_INCH = new BigDecimal("2.54");

    private record Allowance(int pieces, int weightKg, List<Integer> size) {
        static final Allowance NONE = new Allowance(0, 0, List.of());
    }

    public List<SearchFreeBaggage> convert(List<ItinerarySegment> itinerary, List<PassengerOfferPrice> passengerPrices,
                                           SearchMappingContext context) {
        return passengerPrices.stream().flatMap(passengerPrice -> {
            Map<String, FlightSegmentReference> references = segmentReferences(passengerPrice);
            return itinerary.stream().map(segment -> freeBaggage(passengerPrice, segment, references, context));
        }).toList();
    }

    private static SearchFreeBaggage freeBaggage(PassengerOfferPrice passengerPrice, ItinerarySegment segment,
                                                 Map<String, FlightSegmentReference> references, SearchMappingContext context) {
        var bagAssociation = Optional.ofNullable(references.get(segment.segmentKey()))
                .map(FlightSegmentReference::getBagDetailAssociation);
        Allowance checked = bagAssociation
                .flatMap(association -> RefUtil.keys(association.getCheckedBagReferences()).stream().findFirst())
                .flatMap(key -> context.index().checkedBag(key))
                .map(bag -> allowance(bag.getPieceAllowance(), bag.getWeightAllowance(), bag.getDimensionAllowance()))
                .orElse(Allowance.NONE);
        Allowance carryOn = bagAssociation
                .flatMap(association -> RefUtil.keys(association.getCarryOnReferences()).stream().findFirst())
                .flatMap(key -> context.index().carryOn(key))
                .map(bag -> allowance(bag.getPieceAllowance(), bag.getWeightAllowance(), bag.getDimensionAllowance()))
                .orElse(Allowance.NONE);

        SearchFreeBaggage baggage = new SearchFreeBaggage();
        baggage.setTravelDirection(segment.travelDirection());
        baggage.setFlightSeq(segment.flightSeq());
        baggage.setPassengerType(passengerPrice.passengerType());
        applyChecked(baggage, checked);
        baggage.setCarryOnPiece(carryOn.pieces());
        baggage.setCarryOnWeight(carryOn.weightKg());
        if (carryOn.size().size() == 3) {
            baggage.setCarryOnSize(new ArrayList<>(carryOn.size()));
        }
        return baggage;
    }

    private static void applyChecked(SearchFreeBaggage baggage, Allowance checked) {
        if (checked.pieces() <= 0 && checked.weightKg() <= 0) {
            baggage.setBaggagePiece(PK_NO_BAGGAGE_PIECE);
            baggage.setBaggageWeight(PK_NOT_APPLICABLE);
            return;
        }
        baggage.setBaggagePiece(checked.pieces() > 0 ? checked.pieces() : PK_NOT_APPLICABLE);
        baggage.setBaggageWeight(checked.weightKg() > 0 ? checked.weightKg() : PK_NOT_APPLICABLE);
    }

    private static Allowance allowance(List<PieceAllowance> pieceAllowances, WeightAllowance weightAllowance,
                                       BagAllowanceDimensionType dimensionAllowance) {
        int pieces = pieceAllowances.stream()
                .map(PieceAllowance::getTotalQuantity)
                .filter(Objects::nonNull)
                .mapToInt(java.math.BigInteger::intValue)
                .max()
                .orElse(0);
        int weight = maxWeightKg(weightAllowance)
                // weight given per piece only, e.g. "1 piece of max 23 kg"
                .orElseGet(() -> pieceAllowances.stream()
                        .flatMap(piece -> piece.getPieceMeasurements().stream())
                        .flatMap(measurement -> measurement.getPieceWeightAllowance().stream())
                        .flatMap(pieceWeight -> maxWeightKg(pieceWeight).stream())
                        .max(Integer::compareTo)
                        .orElse(0));
        return new Allowance(pieces, weight, dimensions(dimensionAllowance));
    }

    private static Optional<Integer> maxWeightKg(BagAllowanceWeightType weightAllowance) {
        return Optional.ofNullable(weightAllowance).stream()
                .flatMap(allowance -> allowance.getMaximumWeight().stream())
                .filter(weight -> nonNull(weight.getValue()))
                .map(weight -> WeightUnitSimpleType.POUND == weight.getUOM()
                        ? weight.getValue().multiply(KG_PER_POUND)
                        : weight.getValue())
                .map(kg -> kg.setScale(0, RoundingMode.DOWN).intValue())
                .max(Integer::compareTo);
    }

    /** [length, width, height] in cm, only when all three are present. */
    private static List<Integer> dimensions(BagAllowanceDimensionType dimensionAllowance) {
        return Optional.ofNullable(dimensionAllowance).map(FreeBaggageConverter::dimensionsCm).orElse(List.of());
    }

    private static List<Integer> dimensionsCm(BagAllowanceDimensionType dimensionAllowance) {
        boolean inches = SizeUnitSimpleType.INCH == dimensionAllowance.getDimensionUOM();
        Map<String, Integer> byCategory = dimensionAllowance.getDimensions().stream()
                .filter(dimension -> nonNull(dimension.getCategory()) && nonNull(dimension.getMaxValue()))
                .collect(Collectors.toMap(
                        dimension -> dimension.getCategory().trim().toUpperCase(Locale.ROOT),
                        dimension -> (inches ? dimension.getMaxValue().multiply(CM_PER_INCH) : dimension.getMaxValue())
                                .setScale(0, RoundingMode.DOWN).intValue(),
                        (_, last) -> last));
        List<String> categories = List.of("LENGTH", "WIDTH", "HEIGHT");
        return byCategory.keySet().containsAll(categories) ? categories.stream().map(byCategory::get).toList() : List.of();
    }

    private static Map<String, FlightSegmentReference> segmentReferences(PassengerOfferPrice passengerPrice) {
        return Optional.ofNullable(passengerPrice.offerPrice().getRequestedDate()).stream()
                .flatMap(requestedDate -> requestedDate.getAssociations().stream())
                .flatMap(association -> Optional.ofNullable(association.getApplicableFlight()).stream())
                .flatMap(applicableFlight -> applicableFlight.getFlightSegmentReference().stream())
                .filter(reference -> nonNull(RefUtil.key(reference.getRef())))
                .collect(Collectors.toMap(reference -> RefUtil.key(reference.getRef()), Function.identity(), (first, _) -> first));
    }
}
