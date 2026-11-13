package com.verteil.thirdparties.adapter.pkfare.offer.common.converter;

import com.verteil.schemas.ndc16_1.ApplicableFlight;
import com.verteil.schemas.ndc16_1.FlightSegmentReference;
import com.verteil.schemas.ndc16_1.ListOfFlightSegmentType;
import com.verteil.schemas.ndc16_1.PricedFlightOfferAssocType;
import com.verteil.thirdparties.adapter.pkfare.common.utils.NdcUtil;
import com.verteil.thirdparties.adapter.pkfare.common.utils.RefUtil;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.DataListsIndex;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.ItinerarySegment;
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
import java.util.stream.Stream;

import static java.util.Objects.nonNull;

/**
 * Orders an offer's segments the PKFARE way. Segments are grouped by origin-destination (the association's OD
 * reference, else the OD the segment belongs to in OriginDestinationList), groups are ordered by first departure
 * (1 = outbound, 2 = return) and segments within a group by departure ({@code flightSeq} 1..n).
 * Offers with more than two journeys are not representable in PKFARE and yield an empty itinerary.
 */
@Slf4j
@Component
public class ItineraryConverter {

    private static final int MAX_DIRECTIONS = 2;
    private static final Comparator<LocalDateTime> DEPARTURE_ORDER = Comparator.nullsLast(Comparator.naturalOrder());

    private record Leg(String segmentKey, ListOfFlightSegmentType segment, FlightSegmentReference reference, LocalDateTime departure) {}

    /** A segment reference of the offer, with the association it came from and that association's OD references. */
    private record SegmentRef(int association, List<String> odKeys, String segmentKey, FlightSegmentReference reference) {}

    /**
     * @param offerId      for logging only
     * @param associations the associations that carry the offer's class of service per segment (offer level for
     *                     AirShopping, else those of an OfferPrice)
     * @return the itinerary, empty when the offer references an unknown segment or is neither one-way nor round trip
     */
    public List<ItinerarySegment> convert(String offerId, List<PricedFlightOfferAssocType> associations, DataListsIndex index) {
        Map<String, SegmentRef> segmentRefs = mapSegmentRefs(Optional.ofNullable(associations).orElse(List.of()));
        Optional<String> unknownSegment = segmentRefs.keySet().stream()
                .filter(segmentKey -> index.segment(segmentKey).isEmpty())
                .findFirst();
        if (unknownSegment.isPresent()) {
            log.warn("Offer {} references unknown segment {}, offer skipped", offerId, unknownSegment.get());
            return List.of();
        }
        Map<String, List<Leg>> journeys = mapJourneys(segmentRefs.values(), index);
        if (journeys.isEmpty() || journeys.size() > MAX_DIRECTIONS) {
            log.warn("Offer {} has {} journeys, only one-way and round-trip are supported; offer skipped", offerId, journeys.size());
            return List.of();
        }

        return mapItinerary(journeys.values());
    }

    /** Segment key -> its reference; the first reference of each segment wins, in association order. */
    private static Map<String, SegmentRef> mapSegmentRefs(List<PricedFlightOfferAssocType> associations) {
        return IntStream.range(0, associations.size()).boxed()
                .flatMap(i -> Optional.ofNullable(associations.get(i).getApplicableFlight()).stream()
                        .flatMap(applicableFlight -> mapSegmentRefs(i, applicableFlight)))
                .filter(segmentRef -> nonNull(segmentRef.segmentKey()))
                .collect(Collectors.toMap(SegmentRef::segmentKey, Function.identity(), (first, _) -> first, LinkedHashMap::new));
    }

    private static Stream<SegmentRef> mapSegmentRefs(int association, ApplicableFlight applicableFlight) {
        List<String> odKeys = RefUtil.keys(applicableFlight.getOriginDestinationReferences());
        return applicableFlight.getFlightSegmentReference().stream()
                .map(reference -> new SegmentRef(association, odKeys, RefUtil.key(reference.getRef()), reference));
    }

    private static Map<String, List<Leg>> mapJourneys(Collection<SegmentRef> segmentRefs, DataListsIndex index) {
        return segmentRefs.stream()
                .collect(Collectors.groupingBy(segmentRef -> mapJourneyKey(segmentRef, index), LinkedHashMap::new,
                        Collectors.mapping(segmentRef -> mapLeg(segmentRef, index), Collectors.toList())));
    }

    /** Journeys ordered by first departure (travelDirection 1, 2), legs within each by departure (flightSeq 1..n). */
    private static List<ItinerarySegment> mapItinerary(Collection<List<Leg>> journeys) {
        List<List<Leg>> ordered = mapOrderedJourneys(journeys);
        return IntStream.range(0, ordered.size()).boxed()
                .flatMap(direction -> mapDirectionSegments(ordered.get(direction), direction + 1))
                .toList();
    }

    private static List<List<Leg>> mapOrderedJourneys(Collection<List<Leg>> journeys) {
        Comparator<Leg> byDeparture = Comparator.comparing(Leg::departure, DEPARTURE_ORDER);
        return journeys.stream()
                .map(legs -> legs.stream()
                        .sorted(byDeparture)
                        .toList())
                .sorted(Comparator.comparing(legs -> legs.getFirst().departure(), DEPARTURE_ORDER))
                .toList();
    }

    private static Stream<ItinerarySegment> mapDirectionSegments(List<Leg> legs, int travelDirection) {
        return IntStream.range(0, legs.size())
                .mapToObj(seq -> mapItinerarySegment(legs.get(seq), travelDirection, seq + 1));
    }

    /** The association's single OD reference, else the OD the segment belongs to, else one journey per association. */
    private static String mapJourneyKey(SegmentRef segmentRef, DataListsIndex index) {
        return segmentRef.odKeys().size() == 1 ? segmentRef.odKeys().getFirst()
                : index.originDestinationOfSegment(segmentRef.segmentKey()).orElse("ASSOCIATION-" + segmentRef.association());
    }

    private static Leg mapLeg(SegmentRef segmentRef, DataListsIndex index) {
        ListOfFlightSegmentType segment = index.segment(segmentRef.segmentKey()).orElseThrow();
        LocalDateTime departure = Optional.ofNullable(segment.getDeparture())
                .map(flightDeparture -> NdcUtil.toLocalDateTime(flightDeparture.getDate(), flightDeparture.getTime()))
                .orElse(null);
        return new Leg(segmentRef.segmentKey(), segment, segmentRef.reference(), departure);
    }

    private static ItinerarySegment mapItinerarySegment(Leg leg, int travelDirection, int flightSeq) {
        return new ItinerarySegment(leg.segmentKey(), leg.segment(), leg.reference(), travelDirection, flightSeq, leg.departure());
    }
}
