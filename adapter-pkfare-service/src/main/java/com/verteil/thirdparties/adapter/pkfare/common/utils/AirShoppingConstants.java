package com.verteil.thirdparties.adapter.pkfare.common.utils;

import java.util.List;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_PUBLIC_FARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_PRIVATE_FARE_REMARK;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_PUBLIC_FARE_REMARK;

/** PKFARE SEARCH -> Entry-Gate AirShopping behaviour. */
public final class AirShoppingConstants {
    private AirShoppingConstants() {
    }

    /* ------------------------------------------------------------------------
     * ENTRY-GATE CALL
     * --------------------------------------------------------------------- */
    /** AirShopping timeout, capped by PKFARE's {@code cutOffTime} minus {@link #CUTOFF_BUFFER_MILLIS}. */
    public static final long TIMEOUT_MILLIS = 30000L;
    public static final long CUTOFF_BUFFER_MILLIS = 500L;
    public static final long MIN_TIMEOUT_MILLIS = 1000L;
    /** Entry-Gate error codes that mean "no availability": PKFARE gets status 0 with empty lists. */
    public static final List<String> NO_RESULT_ERROR_CODES = List.of();

    /* ------------------------------------------------------------------------
     * AIRSHOPPING RQ
     * --------------------------------------------------------------------- */
    public static final List<String> FARE_PREFERENCES = List.of(VDC_PUBLIC_FARE_REMARK, VDC_PRIVATE_FARE_REMARK);
    /** {@code ShopResultPreference} for {@code returnTagPrice=N}: the lowest fare per flight. */
    public static final String SHOP_RESULT_PREFERENCE = "BEST";
    /** {@code ShopResultPreference} for {@code returnTagPrice=Y}: every branded fare per flight. */
    public static final String SHOP_RESULT_PREFERENCE_TAGGED = "OPTIMIZED";
    /** {@code ResponseParameters.SortOrder}, in priority order. */
    public static final List<String> SORT_ORDER = List.of("PRICE", "STOP", "DEPARTURE_TIME");

    /* ------------------------------------------------------------------------
     * SEARCH RS
     * --------------------------------------------------------------------- */
    /** {@code ticketList[].ticketIssueTime} in minutes; VDC has no such value. */
    public static final int TICKET_ISSUE_TIME = 120;
    /** Product type when the FareGroup remarks carry neither PUBL nor PVT. */
    public static final String DEFAULT_PRODUCT_TYPE = PK_PUBLIC_FARE;

    /* ------------------------------------------------------------------------
     * SEARCH RESPONSE CACHE (per pod)
     * --------------------------------------------------------------------- */
    /** Off by default: when false the cache commands are not part of the SEARCH chain. */
    public static final boolean SEARCH_CACHE_ENABLED = false;
    public static final long SEARCH_CACHE_TTL_SECONDS = 120L;
    /** Upper bound on cached shopping results + flights across all cached responses. */
    public static final long SEARCH_CACHE_MAX_WEIGHT = 50000L;
}
