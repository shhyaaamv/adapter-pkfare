package com.verteil.thirdparties.adapter.pkfare.offer.qte.model;

import com.verteil.schema.pkfare.QteSegment;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.DataSegment;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.FARE_GROUP_KEY_PREFIX;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * One requested QTE segment with its offer data segment, as sent in the FlightPriceRQ.
 *
 * @param index runs over fromSegments then retSegments
 */
public record RequestedSegment(int index, QteSegment segment, DataSegment dataSegment) {

    /** The request's {@code rbd}, which validation guarantees is not blank. */
    public String bookingClass() {
        return segment.getRbd().trim();
    }

    /** A FareGroup is only made for a segment whose fare basis is known. */
    public boolean hasFareGroup() {
        return isNotBlank(dataSegment.fareBasis());
    }

    public String fareGroupKey() {
        return FARE_GROUP_KEY_PREFIX + index;
    }
}
