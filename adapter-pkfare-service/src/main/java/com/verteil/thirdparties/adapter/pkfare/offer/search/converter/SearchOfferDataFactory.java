package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.verteil.schemas.ndc16_1.AirShoppingRS.OffersGroup.AirlineOffers.AirlineOffer;
import com.verteil.schemas.ndc16_1.ItemIDType;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.OfferFareConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.SegmentDetailsConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.TicketPriceConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.DataListsIndex;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.ItinerarySegment;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.PassengerOfferPrice;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.OfferRef;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.DataPassenger;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.DataSegment;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.SearchMappingContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AirShoppingRS offer -> {@link OfferData}, the shopping result's
 * {@code data}: what QTE needs to build the FlightPriceRQ that the PKFARE QTE request lacks.
 * <pre>
 * shoppingResponseId   Metadata SHOPPING_RESPONSE_IDS AugPoint of the OfferID owner, else the root ShoppingResponseID
 * offer                OfferID value / Owner
 * passengers[]         one per OfferPrice: OfferItemID, PTC and distinct TravelerReferences of its associations
 * segments{}           itinerary order: match key -> segment key | fare basis of the first priced passenger type
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class SearchOfferDataFactory {

    private final OfferFareConverter offerFareConverter;
    private final SegmentDetailsConverter segmentDetailsConverter;

    /**
     * @param itinerary       the offer's segments in itinerary order
     * @param passengerPrices the resolved OfferPrices, the first one gives the segments' fare basis
     * @return empty when the response has no ShoppingResponseID for the offer's owner (the offer cannot be priced)
     */
    public Optional<OfferData> create(AirlineOffer offer, List<ItinerarySegment> itinerary, List<PassengerOfferPrice> passengerPrices,
                                       SearchMappingContext searchMappingContext) {
        String owner = Optional.ofNullable(offer.getOfferID())
                .map(ItemIDType::getOwner)
                .orElse(null);
        return searchMappingContext.metadata().shoppingResponseId(owner)
                .map(shoppingResponseId -> OfferData.builder()
                        .shoppingResponseId(shoppingResponseId)
                        .offer(mapOfferRef(offer))
                        .passengers(mapPassengers(offer, searchMappingContext.index()))
                        .segments(mapSegments(itinerary, passengerPrices.getFirst(), searchMappingContext.index()))
                        .build());
    }

    private static OfferRef mapOfferRef(AirlineOffer offer) {
        return Optional.ofNullable(offer.getOfferID())
                .map(offerId -> new OfferRef(offerId.getValue(), offerId.getOwner()))
                .orElse(null);
    }

    private static List<DataPassenger> mapPassengers(AirlineOffer offer, DataListsIndex index) {
        return offer.getPricedOffer().getOfferPrice().stream()
                .map(offerPrice -> TicketPriceConverter.mapDataPassenger(offerPrice, index))
                .toList();
    }

    /** Insertion-ordered, so the data keeps the itinerary order; a repeated match key keeps its first segment. */
    private Map<String, String> mapSegments(List<ItinerarySegment> itinerary, PassengerOfferPrice passengerPrice, DataListsIndex index) {
        return itinerary.stream()
                .collect(Collectors.toMap(segment -> segmentDetailsConverter.convert(segment.segment()).matchKey(),
                        segment -> mapDataSegment(segment, passengerPrice, index).encode(),
                        (first, _) -> first, LinkedHashMap::new));
    }

    private DataSegment mapDataSegment(ItinerarySegment segment, PassengerOfferPrice passengerPrice, DataListsIndex index) {
        return new DataSegment(segment.segmentKey(), offerFareConverter.mapFareBasis(segment, passengerPrice, index).orElse(null));
    }
}
