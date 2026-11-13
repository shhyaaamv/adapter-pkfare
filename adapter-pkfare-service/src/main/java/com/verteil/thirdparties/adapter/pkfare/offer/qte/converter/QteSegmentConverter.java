package com.verteil.thirdparties.adapter.pkfare.offer.qte.converter;

import com.verteil.schema.pkfare.QteSegment;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.SegmentDetails;
import com.verteil.thirdparties.adapter.pkfare.offer.qte.model.PricedSegment;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.EMPTY;
import static java.util.Objects.requireNonNullElse;

/**
 * One priced segment -> {@code routing.fromSegments[]} / {@code retSegments[]} entry.
 * <pre>
 * codeShare              "true" / "false" (string, as the QTE schema types it)
 * aircraftCode           Equipment.AircraftCode, "" when absent
 * operatingFlightNumber  OperatingCarrier.FlightNumber (code shares), else the marketing flight number
 * stopAirport            stop airports "/"-joined, omitted when none
 * flightRefNum           not sent
 * </pre>
 */
@Component
public class QteSegmentConverter {

    private static final String STOP_SEPARATOR = "/";

    public QteSegment convert(PricedSegment pricedSegment) {
        SegmentDetails details = pricedSegment.details();
        QteSegment segment = new QteSegment();
        segment.setMarketingCarrier(details.marketingCarrier());
        segment.setFlightNumber(details.flightNumber());
        segment.setDepAirport(details.depAirport());
        segment.setDepTime(details.depTime());
        segment.setDepTerminal(details.depTerminal());
        segment.setArrAirport(details.arrAirport());
        segment.setArrTime(details.arrTime());
        segment.setArrTerminal(details.arrTerminal());
        segment.setDuration(details.durationMinutes());
        segment.setRbd(pricedSegment.rbd());
        segment.setCabinClass(pricedSegment.cabinClass());
        segment.setCodeShare(String.valueOf(details.codeShare()));
        segment.setAircraftCode(requireNonNullElse(details.aircraftCode(), EMPTY));
        segment.setOperatingCarrier(details.operatingCarrier());
        segment.setOperatingFlightNumber(Optional.ofNullable(details.operatingFlightNumber()).orElse(details.flightNumber()));
        segment.setTravelDirection(pricedSegment.itinerary().travelDirection());
        segment.setFlightSeq(String.valueOf(pricedSegment.itinerary().flightSeq()));
        Optional.of(details.stopAirports())
                .filter(stops -> !stops.isEmpty())
                .map(stops -> String.join(STOP_SEPARATOR, stops))
                .ifPresent(segment::setStopAirport);
        return segment;
    }
}
