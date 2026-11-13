package com.verteil.thirdparties.adapter.pkfare.common.utils;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_PUBLIC_FARE;

/** PKFARE QTE -> Entry-Gate FlightPrice behaviour. */
public final class FlightPriceConstants {
    private FlightPriceConstants() {
    }

    /* ------------------------------------------------------------------------
     * ENTRY-GATE CALL
     * --------------------------------------------------------------------- */
    /** FlightPrice timeout, capped by PKFARE's {@code cutOffTime} minus {@link #CUTOFF_BUFFER_MILLIS}. */
    public static final long TIMEOUT_MILLIS = 30000L;
    public static final long CUTOFF_BUFFER_MILLIS = 500L;

    /* ------------------------------------------------------------------------
     * QTE RS
     * --------------------------------------------------------------------- */
    /** Product type when the FareGroup remarks carry neither PUBL nor PVT. */
    public static final String DEFAULT_PRODUCT_TYPE = PK_PUBLIC_FARE;
}
