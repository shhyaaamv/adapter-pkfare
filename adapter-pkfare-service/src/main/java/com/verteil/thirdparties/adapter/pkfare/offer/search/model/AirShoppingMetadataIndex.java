package com.verteil.thirdparties.adapter.pkfare.offer.search.model;

import com.verteil.schemas.ndc16_1.AirShopResMetadataType;
import com.verteil.schemas.ndc16_1.AirShoppingRS;
import com.verteil.schemas.ndc16_1.AugPointType;
import com.verteil.schemas.ndc16_1.DescriptionMetadataType;
import com.verteil.schemas.ndc16_1.ShoppingResponseIDType;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.ShoppingResponseRef;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SHOPPING_RESPONSE_IDS;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

/**
 * Key lookups over an AirShoppingRS's {@code Metadata.Other.OtherMetadata[]}, built once per response.
 * <pre>
 * shoppingResponseId  DescriptionMetadata[MetadataKey=SHOPPING_RESPONSE_IDS].AugmentationPoint.AugPoint[Owner].Key,
 *                     else the root ShoppingResponseID (Owner, else the requested owner)
 * </pre>
 */
public final class AirShoppingMetadataIndex {

    private final Map<String, String> shoppingResponseIdsByOwner = new HashMap<>();
    private final ShoppingResponseIDType rootShoppingResponseId;

    public AirShoppingMetadataIndex(AirShoppingRS airShoppingRS) {
        Optional<AirShoppingRS> response = Optional.ofNullable(airShoppingRS);
        this.rootShoppingResponseId = response.map(AirShoppingRS::getShoppingResponseID).orElse(null);
        List<AirShopResMetadataType.Other.OtherMetadata> otherMetadata = response.map(AirShoppingRS::getMetadata)
                .map(AirShopResMetadataType::getOther)
                .map(AirShopResMetadataType.Other::getOtherMetadata)
                .orElse(List.of());
        indexShoppingResponseIds(otherMetadata);
    }

    /** The ShoppingResponseID to price an offer of {@code owner} with; empty when the response has none. */
    public Optional<ShoppingResponseRef> shoppingResponseId(String owner) {
        return Optional.ofNullable(owner)
                .map(shoppingResponseIdsByOwner::get)
                .map(value -> new ShoppingResponseRef(owner, value))
                .or(() -> rootShoppingResponseId(owner));
    }

    private Optional<ShoppingResponseRef> rootShoppingResponseId(String owner) {
        return Optional.ofNullable(rootShoppingResponseId)
                .flatMap(shoppingResponseId -> Optional.ofNullable(shoppingResponseId.getResponseID())
                        .map(ShoppingResponseIDType.ResponseID::getValue)
                        .filter(StringUtils::isNotBlank)
                        .map(value -> new ShoppingResponseRef(defaultIfBlank(shoppingResponseId.getOwner(), owner), value)));
    }

    private void indexShoppingResponseIds(List<AirShopResMetadataType.Other.OtherMetadata> otherMetadata) {
        otherMetadata.stream()
                .flatMap(metadata -> Optional.ofNullable(metadata.getDescriptionMetadatas()).stream())
                .flatMap(descriptions -> descriptions.getDescriptionMetadata().stream())
                .filter(description -> SHOPPING_RESPONSE_IDS.equals(description.getMetadataKey()))
                .flatMap(AirShoppingMetadataIndex::augPoints)
                .filter(augPoint -> StringUtils.isNotBlank(augPoint.getOwner()) && StringUtils.isNotBlank(augPoint.getKey()))
                .forEach(augPoint -> shoppingResponseIdsByOwner.putIfAbsent(augPoint.getOwner(), augPoint.getKey()));
    }

    private static Stream<AugPointType> augPoints(DescriptionMetadataType description) {
        return Optional.ofNullable(description.getAugmentationPoint()).stream()
                .flatMap(augmentationPoint -> augmentationPoint.getAugPoint().stream());
    }
}
