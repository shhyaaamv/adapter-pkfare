package com.verteil.thirdparties.adapter.pkfare.common.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * PKFARE cabin code <-> VDC (NDC) cabin code, e.g. premium economy S -> W. The adapter's mapping is the static
 * {@link #PKFARE} (kept in code, not in the database).
 * The VDC -> PKFARE direction is derived; when several PKFARE cabins share a VDC cabin, the identical code wins,
 * else the alphabetically first.
 */
public record CabinMapping(Map<String, String> pkToVdc, Map<String, String> vdcToPk) {

    /** PKFARE cabin -> VDC cabin: F first, C business, S premium economy (VDC W), Y economy. */
    public static final CabinMapping PKFARE = of(Map.of("F", "F", "C", "C", "S", "W", "Y", "Y"));

    public static CabinMapping of(Map<String, String> pkToVdc) {
        Map<String, String> forward = new TreeMap<>();
        pkToVdc.forEach((pkCabin, vdcCabin) -> {
            if (!isBlank(pkCabin) && !isBlank(vdcCabin)) {
                forward.put(normalise(pkCabin), normalise(vdcCabin));
            }
        });
        Map<String, String> reverse = new LinkedHashMap<>();
        forward.forEach((pkCabin, vdcCabin) -> reverse.merge(vdcCabin, pkCabin,
                (existing, candidate) -> candidate.equals(vdcCabin) ? candidate : existing));
        return new CabinMapping(Collections.unmodifiableMap(forward), Collections.unmodifiableMap(reverse));
    }

    public Optional<String> toVdc(String pkCabin) {
        return Optional.ofNullable(pkToVdc.get(normalise(pkCabin)));
    }

    public Optional<String> toPk(String vdcCabin) {
        return Optional.ofNullable(vdcToPk.get(normalise(vdcCabin)));
    }

    public boolean isEmpty() {
        return pkToVdc.isEmpty();
    }

    private static String normalise(String cabin) {
        return trimToEmpty(cabin).toUpperCase(Locale.ROOT);
    }
}
