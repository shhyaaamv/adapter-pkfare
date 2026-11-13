package com.verteil.thirdparties.adapter.pkfare.offer.common.model;

import com.verteil.thirdparties.adapter.pkfare.common.utils.NdcUtil;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.SegmentMatchKey;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One VDC flight segment (DataLists.FlightSegmentList.FlightSegment) in PKFARE terms.
 *
 * @param flightNumber          marketing flight number as an integer, no leading zeros
 * @param operatingCarrier      operating carrier, else the marketing carrier
 * @param operatingFlightNumber operating flight number, only for code shares
 * @param codeShare             operating and marketing carriers are both known and differ
 * @param durationMinutes       FlightDetail.FlightDuration in minutes
 * @param stopAirports          FlightDetail.Stops.StopLocations airports, in order
 */
public record SegmentDetails(
        String marketingCarrier,
        Integer flightNumber,
        String operatingCarrier,
        Integer operatingFlightNumber,
        boolean codeShare,
        String depAirport,
        String arrAirport,
        LocalDateTime departure,
        LocalDateTime arrival,
        String depTerminal,
        String arrTerminal,
        String aircraftCode,
        Integer durationMinutes,
        List<String> stopAirports
) {
    public SegmentDetails {
        stopAirports = List.copyOf(stopAirports);
    }

    /** PKFARE departure time, yyyyMMddHHmm. */
    public String depTime() {
        return NdcUtil.toPkDateTime(departure);
    }

    /** PKFARE arrival time, yyyyMMddHHmm. */
    public String arrTime() {
        return NdcUtil.toPkDateTime(arrival);
    }

    /** The segment's {@link SegmentMatchKey}, built from exactly the fields PKFARE echoes back. */
    public String matchKey() {
        return SegmentMatchKey.of(depAirport, depTime(), arrAirport, arrTime(), marketingCarrier, flightNumber);
    }
}
