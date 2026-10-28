package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.verteil.schema.pkfare.SearchFlight;
import com.verteil.schema.pkfare.SearchStop;
import com.verteil.schemas.ndc16_1.AirlineIDType;
import com.verteil.schemas.ndc16_1.FlightNumber;
import com.verteil.schemas.ndc16_1.ListOfFlightSegmentType;
import com.verteil.thirdparties.adapter.pkfare.common.utils.NdcUtil;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * DataLists.FlightSegmentList.FlightSegment -> SearchResponse.flightList[].
 */
@Component
public class FlightConverter {

    public SearchFlight convert(ListOfFlightSegmentType segment, int flightRefNum) {
        SearchFlight flight = new SearchFlight();
        flight.setFlightRefNum(flightRefNum);

        var marketing = Optional.ofNullable(segment.getMarketingCarrier());
        String marketingCarrier = marketing.map(carrier -> airline(carrier.getAirlineID())).orElse(null);
        flight.setMarketingCarrier(marketingCarrier);
        flight.setFlightNumber(marketing.map(carrier -> NdcUtil.toFlightNumber(flightNumber(carrier.getFlightNumber()))).orElse(null));

        Optional.ofNullable(segment.getDeparture()).ifPresent(departure -> {
            flight.setDepAirport(Optional.ofNullable(departure.getAirportCode()).map(code -> code.getValue()).orElse(null));
            flight.setDepTime(NdcUtil.toPkDateTime(NdcUtil.toLocalDateTime(departure.getDate(), departure.getTime())));
            flight.setDepTerminal(Optional.ofNullable(departure.getTerminal()).map(terminal -> terminal.getName()).orElse(null));
        });
        Optional.ofNullable(segment.getArrival()).ifPresent(arrival -> {
            flight.setArrAirport(Optional.ofNullable(arrival.getAirportCode()).map(code -> code.getValue()).orElse(null));
            flight.setArrTime(NdcUtil.toPkDateTime(NdcUtil.toLocalDateTime(arrival.getDate(), arrival.getTime())));
            flight.setArrTerminal(Optional.ofNullable(arrival.getTerminal()).map(terminal -> terminal.getName()).orElse(null));
        });

        var operating = Optional.ofNullable(segment.getOperatingCarrier());
        String operatingCarrier = operating.map(carrier -> airline(carrier.getAirlineID())).orElse(null);
        boolean codeShare = isNotBlank(operatingCarrier) && isNotBlank(marketingCarrier) && !operatingCarrier.equals(marketingCarrier);
        flight.setCodeShare(codeShare);
        flight.setOperatingCarrier(isNotBlank(operatingCarrier) ? operatingCarrier : marketingCarrier);
        if (codeShare) {
            operating.map(carrier -> NdcUtil.toFlightNumber(flightNumber(carrier.getFlightNumber())))
                    .ifPresent(flight::setOperatingFlightNumber);
        }

        Optional.ofNullable(segment.getEquipment())
                .map(equipment -> equipment.getAircraftCode())
                .map(aircraftCode -> aircraftCode.getValue())
                .ifPresent(flight::setAircraftCode);
        Optional.ofNullable(segment.getFlightDetail()).ifPresent(flightDetail -> {
            Optional.ofNullable(flightDetail.getFlightDuration())
                    .map(duration -> NdcUtil.toMinutes(duration.getValue()))
                    .ifPresent(flight::setDuration);
            Optional.ofNullable(flightDetail.getStops())
                    .map(stops -> stops.getStopLocations())
                    .stream()
                    .flatMap(locations -> locations.getStopLocation().stream())
                    .flatMap(location -> Optional.ofNullable(location.getAirportCode()).map(code -> code.getValue()).stream())
                    .forEach(airport -> {
                        SearchStop stop = new SearchStop();
                        stop.setStopAirport(airport);
                        flight.getStops().add(stop);
                    });
        });
        return flight;
    }

    private static String airline(AirlineIDType airlineId) {
        return Optional.ofNullable(airlineId).map(AirlineIDType::getValue).orElse(null);
    }

    private static String flightNumber(FlightNumber flightNumber) {
        return Optional.ofNullable(flightNumber).map(FlightNumber::getValue).orElse(null);
    }
}
