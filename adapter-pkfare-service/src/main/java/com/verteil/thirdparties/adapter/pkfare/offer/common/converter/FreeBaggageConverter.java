package com.verteil.thirdparties.adapter.pkfare.offer.common.converter;

import com.verteil.schemas.ndc16_1.BagAllowanceDimensionType;
import com.verteil.schemas.ndc16_1.BagAllowanceWeightType;
import com.verteil.schemas.ndc16_1.FlightSegmentReference;
import com.verteil.schemas.ndc16_1.PieceAllowance;
import com.verteil.schemas.ndc16_1.SizeUnitSimpleType;
import com.verteil.schemas.ndc16_1.WeightAllowance;
import com.verteil.schemas.ndc16_1.WeightUnitSimpleType;
import com.verteil.thirdparties.adapter.pkfare.common.utils.RefUtil;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.BaggageAllowance;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.DataListsIndex;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.ItinerarySegment;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PassengerOfferPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_NOT_APPLICABLE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_NO_BAGGAGE_PIECE;
import static java.util.Objects.nonNull;

/**
 * Per passenger type and segment, OfferPrice associations' BagDetailAssociation -> {@link BaggageAllowance}
 * (PKFARE {@code freeBaggageList[]}).
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
    private static final List<String> DIMENSION_CATEGORIES = List.of("LENGTH", "WIDTH", "HEIGHT");

    private static final Allowance NO_ALLOWANCE = new Allowance(0, 0, List.of());

    private record Allowance(int pieces, int weightKg, List<Integer> size) {}

    public List<BaggageAllowance> convert(List<ItinerarySegment> itinerary, List<PassengerOfferPrice> passengerPrices,
                                          DataListsIndex index) {
        return passengerPrices.stream()
                .flatMap(passengerPrice -> mapPassengerAllowances(passengerPrice, itinerary, index))
                .toList();
    }

    private static Stream<BaggageAllowance> mapPassengerAllowances(PassengerOfferPrice passengerPrice,
                                                                   List<ItinerarySegment> itinerary,
                                                                   DataListsIndex index) {
        Map<String, FlightSegmentReference> references = mapSegmentReferences(passengerPrice);
        return itinerary.stream().map(segment -> mapFreeBaggage(passengerPrice, segment, references, index));
    }

    private static BaggageAllowance mapFreeBaggage(PassengerOfferPrice passengerPrice, ItinerarySegment segment,
                                                   Map<String, FlightSegmentReference> references, DataListsIndex index) {
        var bagAssociation = Optional.ofNullable(references.get(segment.segmentKey()))
                .map(FlightSegmentReference::getBagDetailAssociation);
        Allowance checked = bagAssociation
                .flatMap(association -> RefUtil.keys(association.getCheckedBagReferences()).stream().findFirst())
                .flatMap(index::checkedBag)
                .map(bag -> mapAllowance(bag.getPieceAllowance(), bag.getWeightAllowance(), bag.getDimensionAllowance()))
                .orElse(NO_ALLOWANCE);
        Allowance carryOn = bagAssociation
                .flatMap(association -> RefUtil.keys(association.getCarryOnReferences()).stream().findFirst())
                .flatMap(index::carryOn)
                .map(bag -> mapAllowance(bag.getPieceAllowance(), bag.getWeightAllowance(), bag.getDimensionAllowance()))
                .orElse(NO_ALLOWANCE);
        boolean noChecked = checked.pieces() <= 0 && checked.weightKg() <= 0;
        return new BaggageAllowance(segment.travelDirection(), segment.flightSeq(), passengerPrice.passengerType(),
                noChecked ? PK_NO_BAGGAGE_PIECE : orNotApplicable(checked.pieces()),
                noChecked ? PK_NOT_APPLICABLE : orNotApplicable(checked.weightKg()),
                carryOn.pieces(), carryOn.weightKg(), carryOn.size());
    }

    private static int orNotApplicable(int value) {
        return value > 0 ? value : PK_NOT_APPLICABLE;
    }

    private static Allowance mapAllowance(List<PieceAllowance> pieceAllowances, WeightAllowance weightAllowance,
                                          BagAllowanceDimensionType dimensionAllowance) {
        int pieces = pieceAllowances.stream()
                .map(PieceAllowance::getTotalQuantity)
                .filter(Objects::nonNull)
                .mapToInt(BigInteger::intValue)
                .max()
                .orElse(0);
        int weight = mapMaxWeightKg(weightAllowance)
                .orElseGet(() -> mapPieceWeightKg(pieceAllowances));
        return new Allowance(pieces, weight, mapDimensions(dimensionAllowance));
    }

    /** Weight given per piece only, e.g. "1 piece of max 23 kg". */
    private static int mapPieceWeightKg(List<PieceAllowance> pieceAllowances) {
        return pieceAllowances.stream()
                .flatMap(piece -> piece.getPieceMeasurements().stream())
                .flatMap(measurement -> measurement.getPieceWeightAllowance().stream())
                .flatMap(pieceWeight -> mapMaxWeightKg(pieceWeight).stream())
                .max(Integer::compareTo)
                .orElse(0);
    }

    private static Optional<Integer> mapMaxWeightKg(BagAllowanceWeightType weightAllowance) {
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
    private static List<Integer> mapDimensions(BagAllowanceDimensionType dimensionAllowance) {
        return Optional.ofNullable(dimensionAllowance)
                .map(FreeBaggageConverter::mapDimensionsCm)
                .orElse(List.of());
    }

    private static List<Integer> mapDimensionsCm(BagAllowanceDimensionType dimensionAllowance) {
        boolean inches = SizeUnitSimpleType.INCH == dimensionAllowance.getDimensionUOM();
        Map<String, Integer> byCategory = dimensionAllowance.getDimensions().stream()
                .filter(dimension -> nonNull(dimension.getCategory()) && nonNull(dimension.getMaxValue()))
                .collect(Collectors.toMap(
                        dimension -> dimension.getCategory().trim().toUpperCase(Locale.ROOT),
                        dimension -> toCm(dimension.getMaxValue(), inches),
                        (_, last) -> last));
        if (!byCategory.keySet().containsAll(DIMENSION_CATEGORIES)) {
            return List.of();
        }
        return DIMENSION_CATEGORIES.stream()
                .map(byCategory::get)
                .toList();
    }

    private static int toCm(BigDecimal value, boolean inches) {
        return (inches ? value.multiply(CM_PER_INCH) : value).setScale(0, RoundingMode.DOWN).intValue();
    }

    private static Map<String, FlightSegmentReference> mapSegmentReferences(PassengerOfferPrice passengerPrice) {
        return Optional.ofNullable(passengerPrice.offerPrice().getRequestedDate()).stream()
                .flatMap(requestedDate -> requestedDate.getAssociations().stream())
                .flatMap(association -> Optional.ofNullable(association.getApplicableFlight()).stream())
                .flatMap(applicableFlight -> applicableFlight.getFlightSegmentReference().stream())
                .filter(reference -> nonNull(RefUtil.key(reference.getRef())))
                .collect(Collectors.toMap(reference -> RefUtil.key(reference.getRef()), Function.identity(), (first, _) -> first));
    }
}
