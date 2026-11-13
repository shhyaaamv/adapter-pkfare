package com.verteil.thirdparties.adapter.pkfare.offer.common.data;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PIPE;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * Identifies a segment the same way from the VDC side (SEARCH/QTE response, formatted as PKFARE's
 * {@code flightList}) and from the segment PKFARE echoes back in its QTE request:
 * {@code depAirport|depTime|arrAirport|arrTime|marketingCarrier|flightNumber}, times as {@code yyyyMMddHHmm},
 * flight number without leading zeros, trimmed and upper-cased. A missing part is an empty string.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SegmentMatchKey {

    public static String of(String depAirport, String depTime, String arrAirport, String arrTime,
                            String marketingCarrier, Integer flightNumber) {
        return Stream.of(depAirport, depTime, arrAirport, arrTime, marketingCarrier, Objects.toString(flightNumber, null))
                .map(part -> trimToEmpty(part).toUpperCase(Locale.ROOT))
                .collect(Collectors.joining(PIPE));
    }
}
