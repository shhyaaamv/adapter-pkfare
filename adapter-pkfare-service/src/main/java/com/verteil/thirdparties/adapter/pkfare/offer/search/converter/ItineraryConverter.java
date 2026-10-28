package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.verteil.schemas.ndc16_1.AirShoppingRS.OffersGroup.AirlineOffers.AirlineOffer;
import com.verteil.schemas.ndc16_1.FlightSegmentReference;
import com.verteil.schemas.ndc16_1.ItemIDType;
import com.verteil.schemas.ndc16_1.ListOfFlightSegmentType;
import com.verteil.schemas.ndc16_1.OfferPriceLeadType;
import com.verteil.schemas.ndc16_1.PricedFlightOfferAssocType;
import com.verteil.thirdparties.adapter.pkfare.common.utils.NdcUtil;
import com.verteil.thirdparties.adapter.pkfare.common.utils.RefUtil;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.ItinerarySegment;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.SearchMappingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Objects.nonNull;

/**
 * Orders an offer's segments the PKFARE way. Segments are grouped by origin-destination (the association's OD
 * reference, else the OD the segment belongs to in OriginDestinationList), groups are ordered by first departure
 * (1 = outbound, 2 = return) and segments within a group by departure ({@code flightSeq} 1..n).
 * Offers with more than two journeys are not representable in PKFARE SEARCH and yield an empty itinerary.
 */
@Slf4j
@Component
public class ItineraryConverter {

    private static final int MAX_DIRECTIONS = 2;

    private record Leg(String segmentKey, ListOfFlightSegmentType segment, FlightSegmentReference reference, LocalDateTime departure) {}

    /** A segment reference of the offer, with the association it came from and that association's OD references. */
    private record SegmentRef(int association, List<String> odKeys, String segmentKey, FlightSegmentReference reference) {}

    public List<ItinerarySegment> convert(AirlineOffer offer, SearchMappingContext context) {
        Map<String, SegmentRef> segmentRefs = segmentRefs(offerAssociations(offer));
        Optional<String> unknownSegment = segmentRefs.keySet().stream()
                .filter(segmentKey -> context.index().segment(segmentKey).isEmpty())
                .findFirst();
        if (unknownSegment.isPresent()) {
            log.warn("Offer {} references unknown segment {}, offer skipped", offerId(offer), unknownSegment.get());
            return List.of();
        }
        Map<String, List<Leg>> journeys = segmentRefs.values().stream()
                .collect(Collectors.groupingBy(segmentRef -> journeyKey(segmentRef, context), LinkedHashMap::new,
                        Collectors.mapping(segmentRef -> leg(segmentRef, context), Collectors.toList())));
        if (journeys.isEmpty() || journeys.size() > MAX_DIRECTIONS) {
            log.warn("Offer {} has {} journeys, only one-way and round-trip are supported; offer skipped", offerId(offer), journeys.size());
            return List.of();
        }

        return itinerary(journeys.values());
    }

    /** Segment key -> its reference; the first reference of each segment wins, in association order. */
    private static Map<String, SegmentRef> segmentRefs(List<PricedFlightOfferAssocType> associations) {
        return IntStream.range(0, associations.size()).boxed()
                .flatMap(i -> Optional.ofNullable(associations.get(i).getApplicableFlight()).stream()
                        .flatMap(applicableFlight -> {
                            List<String> odKeys = RefUtil.keys(applicableFlight.getOriginDestinationReferences());
                            return applicableFlight.getFlightSegmentReference().stream()
                                    .map(reference -> new SegmentRef(i, odKeys, RefUtil.key(reference.getRef()), reference));
                        }))
                .filter(segmentRef -> nonNull(segmentRef.segmentKey()))
                .collect(Collectors.toMap(SegmentRef::segmentKey, Function.identity(), (first, _) -> first, LinkedHashMap::new));
    }

    /** Journeys ordered by first departure (travelDirection 1, 2), legs within each by departure (flightSeq 1..n). */
    private static List<ItinerarySegment> itinerary(Collection<List<Leg>> journeys) {
        Comparator<Leg> byDeparture = Comparator.comparing(Leg::departure, Comparator.nullsLast(Comparator.naturalOrder()));
        List<List<Leg>> ordered = journeys.stream()
                .map(legs -> legs.stream().sorted(byDeparture).toList())
                .sorted(Comparator.comparing(legs -> legs.getFirst().departure(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return IntStream.range(0, ordered.size()).boxed()
                .flatMap(direction -> IntStream.range(0, ordered.get(direction).size())
                        .mapToObj(seq -> itinerarySegment(ordered.get(direction).get(seq), direction + 1, seq + 1)))
                .toList();
    }

    /** The association's single OD reference, else the OD the segment belongs to, else one journey per association. */
    private static String journeyKey(SegmentRef segmentRef, SearchMappingContext context) {
        return segmentRef.odKeys().size() == 1 ? segmentRef.odKeys().getFirst()
                : context.index().originDestinationOfSegment(segmentRef.segmentKey()).orElse("ASSOCIATION-" + segmentRef.association());
    }

    private static Leg leg(SegmentRef segmentRef, SearchMappingContext context) {
        ListOfFlightSegmentType segment = context.index().segment(segmentRef.segmentKey()).orElseThrow();
        LocalDateTime departure = Optional.ofNullable(segment.getDeparture())
                .map(flightDeparture -> NdcUtil.toLocalDateTime(flightDeparture.getDate(), flightDeparture.getTime()))
                .orElse(null);
        return new Leg(segmentRef.segmentKey(), segment, segmentRef.reference(), departure);
    }

    private static ItinerarySegment itinerarySegment(Leg leg, int travelDirection, int flightSeq) {
        return new ItinerarySegment(leg.segmentKey(), leg.segment(), leg.reference(), travelDirection, flightSeq, leg.departure());
    }

    /** Offer-level associations carry this offer's class of service per segment; fall back to the first OfferPrice. */
    private static List<PricedFlightOfferAssocType> offerAssociations(AirlineOffer offer) {
        return Optional.ofNullable(offer.getPricedOffer())
                .map(pricedOffer -> !pricedOffer.getAssociations().isEmpty() ? pricedOffer.getAssociations()
                        : pricedOffer.getOfferPrice().stream()
                        .flatMap(offerPrice -> Optional.ofNullable(offerPrice.getRequestedDate()).stream())
                        .findFirst()
                        .map(OfferPriceLeadType.RequestedDate::getAssociations)
                        .orElse(List.of()))
                .orElse(List.of());
    }

    static String offerId(AirlineOffer offer) {
        return Optional.ofNullable(offer.getOfferID()).map(ItemIDType::getValue).orElse(null);
    }
}
