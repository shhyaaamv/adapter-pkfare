package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.verteil.schema.pkfare.SearchFlight;
import com.verteil.schema.pkfare.SearchStop;
import com.verteil.schemas.ndc16_1.ListOfFlightSegmentType;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.SegmentDetailsConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.SegmentDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * DataLists.FlightSegmentList.FlightSegment -> SearchResponse.flightList[], via the shared {@link SegmentDetails}.
 */
@Component
@RequiredArgsConstructor
public class FlightConverter {

    private final SegmentDetailsConverter segmentDetailsConverter;

    public SearchFlight convert(ListOfFlightSegmentType segment, int flightRefNum) {
        SegmentDetails details = segmentDetailsConverter.convert(segment);
        SearchFlight flight = new SearchFlight();
        flight.setFlightRefNum(flightRefNum);
        flight.setMarketingCarrier(details.marketingCarrier());
        flight.setFlightNumber(details.flightNumber());
        flight.setDepAirport(details.depAirport());
        flight.setDepTime(details.depTime());
        flight.setDepTerminal(details.depTerminal());
        flight.setArrAirport(details.arrAirport());
        flight.setArrTime(details.arrTime());
        flight.setArrTerminal(details.arrTerminal());
        flight.setCodeShare(details.codeShare());
        flight.setOperatingCarrier(details.operatingCarrier());
        Optional.ofNullable(details.operatingFlightNumber()).ifPresent(flight::setOperatingFlightNumber);
        Optional.ofNullable(details.aircraftCode()).ifPresent(flight::setAircraftCode);
        Optional.ofNullable(details.durationMinutes()).ifPresent(flight::setDuration);
        details.stopAirports().stream()
                .map(FlightConverter::mapStop)
                .forEach(flight.getStops()::add);
        return flight;
    }

    private static SearchStop mapStop(String airport) {
        SearchStop stop = new SearchStop();
        stop.setStopAirport(airport);
        return stop;
    }
}
