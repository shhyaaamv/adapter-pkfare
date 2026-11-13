package com.verteil.thirdparties.adapter.pkfare.offer.common.converter;

import com.verteil.schemas.ndc16_1.DescriptionType;
import com.verteil.schemas.ndc16_1.FareBasisCodeType;
import com.verteil.schemas.ndc16_1.FareComponentType;
import com.verteil.schemas.ndc16_1.FareDetailType;
import com.verteil.schemas.ndc16_1.FareList;
import com.verteil.schemas.ndc16_1.FlightCOSCoreType;
import com.verteil.schemas.ndc16_1.PriceClassType;
import com.verteil.schemas.ndc16_1.PricedFlightOfferAssocType;
import com.verteil.thirdparties.adapter.pkfare.common.utils.NdcUtil;
import com.verteil.thirdparties.adapter.pkfare.common.utils.RefUtil;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.DataListsIndex;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.ItinerarySegment;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PassengerOfferPrice;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.FARE_BASIS_SEPARATOR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_PRIVATE_FARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_PUBLIC_FARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_PRIVATE_FARE_REMARK;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_PUBLIC_FARE_REMARK;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Fare-level attributes of a priced offer.
 * <pre>
 * productType     FareGroup Remarks (via FareComponent.refs / ClassOfService.refs) PVT -> PRV, PUBL -> PUB, else the default
 * fareBasis       per itinerary segment: its FareComponent's FareBasisCode, else that component's FareGroup FareBasisCode,
 *                 else the FareGroup FareBasisCode of the segment's ClassOfService.refs; ";"-joined, only if every segment has one
 * priceClasses    Associations[].PriceClass.PriceClassReference -> PriceClassList (distinct, known only)
 * </pre>
 */
@Component
public class OfferFareConverter {

    public String mapProductType(List<PassengerOfferPrice> passengerPrices, DataListsIndex index, String defaultProductType) {
        Set<String> remarks = mapRemarks(passengerPrices, index);
        if (remarks.contains(VDC_PRIVATE_FARE_REMARK)) {
            return PK_PRIVATE_FARE;
        }
        if (remarks.contains(VDC_PUBLIC_FARE_REMARK)) {
            return PK_PUBLIC_FARE;
        }
        return defaultProductType;
    }

    private static Set<String> mapRemarks(List<PassengerOfferPrice> passengerPrices, DataListsIndex index) {
        return passengerPrices.stream()
                .flatMap(passengerPrice -> index.fareGroups(passengerPrice.offerPrice()))
                .flatMap(OfferFareConverter::mapFareGroupRemarks)
                .map(remark -> remark.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static Stream<String> mapFareGroupRemarks(FareList.FareGroup fareGroup) {
        return Optional.ofNullable(fareGroup.getFare())
                .map(FareList.FareGroup.Fare::getFareDetail)
                .map(FareDetailType::getRemarks)
                .stream()
                .flatMap(fareRemarks -> fareRemarks.getRemark().stream())
                .flatMap(remark -> Optional.ofNullable(remark.getValue()).stream());
    }

    /** One fare basis per segment in itinerary order, ";"-joined; null when any segment lacks one. */
    public String mapFareBasis(List<ItinerarySegment> itinerary, PassengerOfferPrice passengerPrice, DataListsIndex index) {
        List<Optional<String>> fareBases = itinerary.stream()
                .map(segment -> mapFareBasis(segment, passengerPrice, index))
                .toList();
        if (fareBases.stream().anyMatch(Optional::isEmpty)) {
            return null;
        }
        return fareBases.stream()
                .flatMap(Optional::stream)
                .collect(Collectors.joining(FARE_BASIS_SEPARATOR));
    }

    /**
     * Fare basis of the first fare component priced for this segment, else of the fare groups the segment's class of
     * service refers to in this OfferPrice.
     */
    public Optional<String> mapFareBasis(ItinerarySegment segment, PassengerOfferPrice passengerPrice, DataListsIndex index) {
        return Optional.ofNullable(passengerPrice.offerPrice().getFareDetail()).stream()
                .flatMap(fareDetail -> fareDetail.getFareComponent().stream())
                .filter(component -> index.segmentKeyOf(component)
                        .map(segment.segmentKey()::equals)
                        .orElse(false))
                .flatMap(component -> mapFareBasis(component, index).stream())
                .findFirst()
                .filter(StringUtils::isNotBlank)
                .or(() -> mapClassOfServiceFareBasis(segment, passengerPrice, index));
    }

    public List<PriceClassType> mapPriceClasses(List<PricedFlightOfferAssocType> associations, DataListsIndex index) {
        return Optional.ofNullable(associations).stream()
                .flatMap(List::stream)
                .flatMap(association -> Optional.ofNullable(association.getPriceClass())
                        .map(priceClass -> RefUtil.key(priceClass.getPriceClassReference()))
                        .stream())
                .distinct()
                .flatMap(key -> index.priceClass(key).stream())
                .toList();
    }

    /** Name (else Code) of each price class, distinct. */
    public List<String> mapPriceClassNames(List<PricedFlightOfferAssocType> associations, DataListsIndex index) {
        return mapPriceClasses(associations, index).stream()
                .map(OfferFareConverter::mapPriceClassName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static String mapPriceClassName(PriceClassType priceClass) {
        return isNotBlank(priceClass.getName()) ? priceClass.getName() : priceClass.getCode();
    }

    /** Description texts of the price classes, distinct, blanks dropped. */
    public List<String> mapPriceClassDetails(List<PricedFlightOfferAssocType> associations, DataListsIndex index) {
        return mapPriceClasses(associations, index).stream()
                .flatMap(priceClass -> Optional.ofNullable(priceClass.getDescriptions()).stream())
                .flatMap(descriptions -> descriptions.getDescription().stream())
                .flatMap(description -> Optional.ofNullable(description.getText())
                        .map(DescriptionType.Text::getValue)
                        .stream())
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    /** The component's own fare basis code, else the first one of the fare groups it refers to. */
    private static Optional<String> mapFareBasis(FareComponentType component, DataListsIndex index) {
        return Optional.ofNullable(component.getFareBasis())
                .map(FareComponentType.FareBasis::getFareBasisCode)
                .map(FareBasisCodeType::getCode)
                .filter(StringUtils::isNotBlank)
                .or(() -> mapFareGroupFareBasis(RefUtil.keys(component.getRefs()).stream(), index));
    }

    private static Optional<String> mapClassOfServiceFareBasis(ItinerarySegment segment, PassengerOfferPrice passengerPrice,
                                                               DataListsIndex index) {
        return mapFareGroupFareBasis(mapClassOfServiceRefs(segment, passengerPrice), index);
    }

    /** {@code ClassOfService.refs} of this OfferPrice's references to the segment. */
    private static Stream<String> mapClassOfServiceRefs(ItinerarySegment segment, PassengerOfferPrice passengerPrice) {
        return NdcUtil.segmentReferences(passengerPrice.offerPrice())
                .filter(reference -> segment.segmentKey().equals(RefUtil.key(reference.getRef())))
                .flatMap(reference -> Optional.ofNullable(reference.getClassOfService())
                        .map(FlightCOSCoreType::getRefs)
                        .stream())
                .flatMap(refs -> RefUtil.keys(refs).stream());
    }

    private static Optional<String> mapFareGroupFareBasis(Stream<String> fareGroupKeys, DataListsIndex index) {
        return fareGroupKeys
                .flatMap(key -> index.fareGroup(key).stream())
                .flatMap(group -> Optional.ofNullable(group.getFareBasisCode())
                        .map(FareList.FareGroup.FareBasisCode::getCode)
                        .stream())
                .filter(StringUtils::isNotBlank)
                .findFirst();
    }
}
