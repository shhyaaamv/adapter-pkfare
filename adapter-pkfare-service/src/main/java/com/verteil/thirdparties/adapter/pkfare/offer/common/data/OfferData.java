package com.verteil.thirdparties.adapter.pkfare.offer.common.data;

import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PIPE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.splitPreserveAllTokens;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * What PKFARE carries for us in {@code routing.data} between services: only what Entry-Gate needs on the next
 * call that the PKFARE request itself lacks (ShoppingResponseID, OfferID, OfferItemIDs with their traveler refs,
 * VDC segment keys and fare basis). Everything the PKFARE request echoes (times, carriers, rbd, cabin, direction)
 * is taken from the request instead. Encoded by {@link OfferDataCodec}; opaque to PKFARE. SEARCH mints it for QTE,
 * QTE for Order; the data does not record which.
 *
 * @param segments {@link SegmentMatchKey} -> {@link DataSegment#encode() segmentKey|fareBasis}, in itinerary order
 *                 (outbound then return, each by flightSeq); the order is part of the data
 */
@Builder
public record OfferData(
        ShoppingResponseRef shoppingResponseId,
        OfferRef offer,
        List<DataPassenger> passengers,
        Map<String, String> segments
) {

    public record ShoppingResponseRef(String owner, String value) {
    }

    /** The OfferID; its Channel is not carried, every service sends {@code PkfareConstants.VDC_CHANNEL}. */
    public record OfferRef(String id, String owner) {
    }

    /**
     * One priced PTC of the offer.
     *
     * @param travelerRefs the VDC anonymous traveler keys priced by this OfferItem
     */
    public record DataPassenger(String offerItemId, String ptc, List<String> travelerRefs) {
    }

    /** The segment stored under {@code matchKey}; empty when the data has none or its segment key is blank. */
    public Optional<DataSegment> segment(String matchKey) {
        return Optional.ofNullable(segments)
                .map(byMatchKey -> byMatchKey.get(matchKey))
                .map(DataSegment::decode)
                .filter(segment -> !isBlank(segment.segmentKey()));
    }

    /**
     * One VDC segment of the offer, kept as the {@link #segments} value {@code segmentKey|fareBasis}
     * ({@code segmentKey} alone when the fare basis is unknown).
     */
    public record DataSegment(String segmentKey, String fareBasis) {

        public String encode() {
            return Optional.ofNullable(trimToNull(fareBasis))
                    .map(code -> segmentKey + PIPE + code)
                    .orElse(segmentKey);
        }

        public static DataSegment decode(String value) {
            String[] parts = Optional.ofNullable(splitPreserveAllTokens(value, PIPE)).orElse(new String[0]);
            return new DataSegment(part(parts, 0), part(parts, 1));
        }

        private static String part(String[] parts, int index) {
            return index < parts.length ? trimToNull(parts[index]) : null;
        }
    }
}
