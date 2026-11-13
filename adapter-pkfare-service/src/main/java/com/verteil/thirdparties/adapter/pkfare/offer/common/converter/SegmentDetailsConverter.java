package com.verteil.thirdparties.adapter.pkfare.offer.common.converter;

import com.verteil.schemas.ndc16_1.AircraftCode;
import com.verteil.schemas.ndc16_1.AircraftSummaryType;
import com.verteil.schemas.ndc16_1.AirlineIDType;
import com.verteil.schemas.ndc16_1.AirportCode;
import com.verteil.schemas.ndc16_1.FlightArrivalType;
import com.verteil.schemas.ndc16_1.FlightDepartureType;
import com.verteil.schemas.ndc16_1.FlightDetailType;
import com.verteil.schemas.ndc16_1.FlightNumber;
import com.verteil.schemas.ndc16_1.ListOfFlightSegmentType;
import com.verteil.thirdparties.adapter.pkfare.common.utils.NdcUtil;
import com.verteil.thirdparties.adapter.pkfare.offer.common.model.SegmentDetails;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * DataLists.FlightSegmentList.FlightSegment -> {@link SegmentDetails}.
 * <pre>
 * marketingCarrier / flightNumber   MarketingCarrier.AirlineID / FlightNumber (integer, no leading zeros)
 * operatingCarrier                  OperatingCarrier.AirlineID, else the marketing carrier
 * codeShare / operatingFlightNumber operating != marketing carrier / OperatingCarrier.FlightNumber (code shares only)
 * dep / arr                         Departure / Arrival AirportCode, Date + Time, Terminal.Name
 * aircraftCode                      Equipment.AircraftCode
 * durationMinutes / stopAirports    FlightDetail.FlightDuration / Stops.StopLocations airports
 * </pre>
 */
@Component
public class SegmentDetailsConverter {

    private static final Endpoint NO_ENDPOINT = new Endpoint(null, null, null);

    private record Endpoint(String airport, LocalDateTime dateTime, String terminal) {}

    public SegmentDetails convert(ListOfFlightSegmentType segment) {
        String marketingCarrier = Optional.ofNullable(segment.getMarketingCarrier())
                .map(carrier -> mapAirline(carrier.getAirlineID()))
                .orElse(null);
        String operatingCarrier = Optional.ofNullable(segment.getOperatingCarrier())
                .map(carrier -> mapAirline(carrier.getAirlineID()))
                .orElse(null);
        boolean codeShare = isNotBlank(operatingCarrier) && isNotBlank(marketingCarrier) && !operatingCarrier.equals(marketingCarrier);
        Endpoint departure = mapDeparture(segment.getDeparture());
        Endpoint arrival = mapArrival(segment.getArrival());
        return new SegmentDetails(marketingCarrier, mapMarketingFlightNumber(segment),
                isNotBlank(operatingCarrier) ? operatingCarrier : marketingCarrier,
                codeShare ? mapOperatingFlightNumber(segment) : null, codeShare,
                departure.airport(), arrival.airport(), departure.dateTime(), arrival.dateTime(),
                departure.terminal(), arrival.terminal(),
                mapAircraftCode(segment.getEquipment()), mapDurationMinutes(segment.getFlightDetail()),
                mapStopAirports(segment.getFlightDetail()));
    }

    private static Endpoint mapDeparture(FlightDepartureType departure) {
        return Optional.ofNullable(departure)
                .map(flightDeparture -> new Endpoint(
                        Optional.ofNullable(flightDeparture.getAirportCode())
                                .map(FlightDepartureType.AirportCode::getValue)
                                .orElse(null),
                        NdcUtil.toLocalDateTime(flightDeparture.getDate(), flightDeparture.getTime()),
                        Optional.ofNullable(flightDeparture.getTerminal())
                                .map(FlightDepartureType.Terminal::getName)
                                .orElse(null)))
                .orElse(NO_ENDPOINT);
    }

    private static Endpoint mapArrival(FlightArrivalType arrival) {
        return Optional.ofNullable(arrival)
                .map(flightArrival -> new Endpoint(
                        Optional.ofNullable(flightArrival.getAirportCode())
                                .map(FlightArrivalType.AirportCode::getValue)
                                .orElse(null),
                        NdcUtil.toLocalDateTime(flightArrival.getDate(), flightArrival.getTime()),
                        Optional.ofNullable(flightArrival.getTerminal())
                                .map(FlightArrivalType.Terminal::getName)
                                .orElse(null)))
                .orElse(NO_ENDPOINT);
    }

    private static Integer mapMarketingFlightNumber(ListOfFlightSegmentType segment) {
        return Optional.ofNullable(segment.getMarketingCarrier())
                .map(carrier -> NdcUtil.toFlightNumber(mapFlightNumber(carrier.getFlightNumber())))
                .orElse(null);
    }

    private static Integer mapOperatingFlightNumber(ListOfFlightSegmentType segment) {
        return Optional.ofNullable(segment.getOperatingCarrier())
                .map(carrier -> NdcUtil.toFlightNumber(mapFlightNumber(carrier.getFlightNumber())))
                .orElse(null);
    }

    private static String mapAircraftCode(AircraftSummaryType equipment) {
        return Optional.ofNullable(equipment)
                .map(AircraftSummaryType::getAircraftCode)
                .map(AircraftCode::getValue)
                .orElse(null);
    }

    private static Integer mapDurationMinutes(FlightDetailType flightDetail) {
        return Optional.ofNullable(flightDetail)
                .map(FlightDetailType::getFlightDuration)
                .map(duration -> NdcUtil.toMinutes(duration.getValue()))
                .orElse(null);
    }

    private static List<String> mapStopAirports(FlightDetailType flightDetail) {
        return Optional.ofNullable(flightDetail)
                .map(FlightDetailType::getStops)
                .map(FlightDetailType.Stops::getStopLocations)
                .stream()
                .flatMap(locations -> locations.getStopLocation().stream())
                .flatMap(location -> Optional.ofNullable(location.getAirportCode())
                        .map(AirportCode::getValue)
                        .stream())
                .toList();
    }

    private static String mapAirline(AirlineIDType airlineId) {
        return Optional.ofNullable(airlineId)
                .map(AirlineIDType::getValue)
                .orElse(null);
    }

    private static String mapFlightNumber(FlightNumber flightNumber) {
        return Optional.ofNullable(flightNumber)
                .map(FlightNumber::getValue)
                .orElse(null);
    }
}
