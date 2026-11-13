package com.verteil.thirdparties.adapter.pkfare.offer.qte.converter;

import com.verteil.schemas.ndc16_1.FlightPriceRS;
import com.verteil.schemas.ndc16_1.ItemIDType;
import com.verteil.schemas.ndc16_1.PricedFlightOffer;
import com.verteil.schemas.ndc16_1.ShoppingResponseIDType;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.TicketPriceConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.DataListsIndex;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PassengerOfferPrice;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.OfferRef;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.ShoppingResponseRef;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.DataSegment;
import com.verteil.thirdparties.adapter.pkfare.offer.qte.model.PricedSegment;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * FlightPriceRS -> {@link OfferData} for Order. The priced OfferID and segment keys differ from the
 * shopping ones, so the offer data is minted anew rather than echoed.
 * <pre>
 * shoppingResponseId  ShoppingResponseID.ResponseID, owner ShoppingResponseID.Owner else OfferID.Owner;
 *                     no ResponseID -> the SEARCH offer data's
 * offer               PricedFlightOffer.OfferID value / Owner
 * passengers[]        one per priced PTC: OfferItemID, PTC via AnonymousTravelerList, distinct TravelerReferences
 * segments{}          the priced itinerary in order: match key -> segment key | fare basis
 * </pre>
 */
@Component
public class QteOfferDataFactory {

    /**
     * @param shopData the decoded SEARCH offer data of the request, used for fallbacks only; may be null
     */
    public OfferData create(FlightPriceRS response, PricedFlightOffer offer, List<PassengerOfferPrice> passengerPrices,
                             List<PricedSegment> segments, DataListsIndex index, OfferData shopData) {
        return OfferData.builder()
                .shoppingResponseId(mapShoppingResponseId(response, offer)
                        .orElseGet(() -> Optional.ofNullable(shopData).map(OfferData::shoppingResponseId).orElse(null)))
                .offer(mapOfferRef(offer.getOfferID()))
                .passengers(passengerPrices.stream()
                        .map(passengerPrice -> TicketPriceConverter.mapDataPassenger(passengerPrice.offerPrice(), index))
                        .toList())
                .segments(mapSegments(segments))
                .build();
    }

    private static Optional<ShoppingResponseRef> mapShoppingResponseId(FlightPriceRS response, PricedFlightOffer offer) {
        ShoppingResponseIDType shoppingResponseId = response.getShoppingResponseID();
        return Optional.ofNullable(shoppingResponseId)
                .map(ShoppingResponseIDType::getResponseID)
                .map(ShoppingResponseIDType.ResponseID::getValue)
                .filter(StringUtils::isNotBlank)
                .map(value -> new ShoppingResponseRef(mapOwner(shoppingResponseId, offer), value));
    }

    private static String mapOwner(ShoppingResponseIDType shoppingResponseId, PricedFlightOffer offer) {
        return Optional.ofNullable(shoppingResponseId)
                .map(ShoppingResponseIDType::getOwner)
                .filter(StringUtils::isNotBlank)
                .orElseGet(() -> Optional.ofNullable(offer.getOfferID()).map(ItemIDType::getOwner).orElse(null));
    }

    private static OfferRef mapOfferRef(ItemIDType offerId) {
        Optional<ItemIDType> id = Optional.ofNullable(offerId);
        return new OfferRef(id.map(ItemIDType::getValue).orElse(null), id.map(ItemIDType::getOwner).orElse(null));
    }

    /** Insertion-ordered, so the data keeps the priced itinerary order. */
    private static Map<String, String> mapSegments(List<PricedSegment> segments) {
        return segments.stream()
                .collect(Collectors.toMap(PricedSegment::matchKey,
                        segment -> new DataSegment(segment.itinerary().segmentKey(), segment.fareBasis()).encode(),
                        (first, _) -> first, LinkedHashMap::new));
    }
}
