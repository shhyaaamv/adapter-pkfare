package com.verteil.thirdparties.adapter.pkfare.common.utils;

import java.util.List;

public final class PkfareConstants {
    private PkfareConstants() {
    }

    public static final String PKFARE = "PKFARE";
    public static final String PKFARE_API = "PKFARE_API";
    public static final String HOST = "HOST";
    public static final String PATH = "PATH";
    public static final String AUTH_PATH = "AUTH_PATH";
    public static final String AUTH_SCOPE = "AUTH_SCOPE";
    public static final String USERNAME = "USERNAME";
    public static final String PASSWORD = "PASSWORD";
    public static final String MESSAGE_LOG = "MESSAGE_LOG";
    public static final String ENABLE_SERVICE = "ENABLE_SERVICE";
    public static final String TIMEOUT_MILLIS = "TIMEOUT_MILLIS";
    public static final String PROD_ERROR_MONITORING = "PROD_ERROR_MONITORING.ENABLED";
    public static final String XSID = "X-SID";
    public static final String XCID = "X-CID";
    public static final String XTID = "X-TID";
    public static final String XIATA = "X-IATA";

    public static final String PTC_ADULT = "ADT";
    public static final String PTC_CHILD = "CHD";
    public static final String PTC_CHILD_ALT = "CNN";
    public static final String PTC_INFANT = "INF";

    /* ------------------------------------------------------------------------
     * PKFARE REQUEST / RESPONSE CODES
     * --------------------------------------------------------------------- */
    public static final String TRIP_TYPE_ONE_WAY = "1";
    public static final String TRIP_TYPE_ROUND_TRIP = "2";
    public static final String YES = "Y";
    public static final String PK_DATE_FORMAT = "yyyyMMdd";
    public static final String PK_DATE_TIME_FORMAT = "yyyyMMddHHmm";

    public static final int PK_STATUS_SUCCESS = 0;
    public static final int PK_STATUS_OTHER_FAILURE = 3;
    public static final int PK_STATUS_REQUEST_PARAMETER_ERROR = 4;
    public static final int PK_STATUS_PROGRAM_EXCEPTION = 5;
    public static final int PK_MSG_MAX_LENGTH = 63;
    public static final String PK_SUCCESS_MSG = "success";

    public static final int PK_PASSENGER_ADULT = 0;
    public static final int PK_PASSENGER_CHILD = 1;
    public static final int PK_PASSENGER_INFANT = 2;

    public static final int PK_DIRECTION_OUTBOUND = 1;
    public static final int PK_DIRECTION_RETURN = 2;
    public static final int PK_DEFAULT_TICKET_SEQ = 1;
    public static final int PK_MAX_SEAT_COUNT = 9;
    public static final int PK_REFUND_CHANGE_ENTIRE_UNUSED = 0;
    public static final int PK_REFUND_CHANGE_PARTIALLY_USED = 1;
    public static final int PK_NO_BAGGAGE_PIECE = 0;
    public static final int PK_NOT_APPLICABLE = -1;
    public static final String PK_NO_TIME_LIMIT = "-1";
    public static final String PK_TIME_UNIT_HOUR = "h";
    public static final String PK_PUBLIC_FARE = "PUB";
    public static final String PK_PRIVATE_FARE = "PRV";
    public static final String FARE_BASIS_SEPARATOR = ";";

    public static final String DEFAULT_CABIN = "Y";

    public static final String VDC_PUBLIC_FARE_REMARK = "PUBL";
    public static final String VDC_PRIVATE_FARE_REMARK = "PVT";
    public static final String VDC_PENALTY_CANCEL = "Cancel";
    public static final String VDC_PENALTY_REFUND = "Refund";
    public static final String VDC_PENALTY_CHANGE = "Change";
    public static final String VDC_AMOUNT_APPLICATION_MAX = "MAX";
    public static final String OD_KEY_PREFIX = "OD";

    /* ------------------------------------------------------------------------
     * API SERVICE NAMES
     * --------------------------------------------------------------------- */
    // Services exposed to PKFARE (chain bean names)
    public static final String SERVICE_SEARCH = "Search";
    // Entry-Gate (VDC) services the adapter calls
    public static final String SERVICE_AIRSHOPPING = "AirShopping";
    public static final String SERVICE_AUTH = "Auth";

    /* ------------------------------------------------------------------------
     * ENTRY-GATE HTTP
     * --------------------------------------------------------------------- */
    public static final String OAUTH_BEARER_HEADER = "Bearer ";
    public static final String BASIC_AUTH_HEADER = "Basic ";
    public static final String OAUTH_GRANT_TYPE = "grant_type";
    public static final String OAUTH_SCOPE = "scope";
    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    public static final String DEFAULT_AUTH_SCOPE = "api";
    public static final String ENTRY_GATE_SERVICE_HEADER = "Service";
    public static final String ENTRY_GATE_OFFICE_ID_HEADER = "officeId";
    public static final String ENTRY_GATE_TRANSACTION_ID_HEADER = "VDC_TransactionId";

    /* ------------------------------------------------------------------------
     * CONFIGURATOR NAMES (config group PKFARE_API)
     * --------------------------------------------------------------------- */
    public static final String VENDOR_CODES = "VENDOR_CODES";
    public static final String IP_WHITELIST = "IP_WHITELIST";
    public static final String IP_WHITELIST_ENABLED = "IP_WHITELIST_ENABLED";
    public static final String OFFICE_ID = "OFFICE_ID";
    public static final String FARE_PREFERENCES = "FARE_PREFERENCES";
    public static final String SHOP_RESULT_PREFERENCE = "SHOP_RESULT_PREFERENCE";
    public static final String SHOP_RESULT_PREFERENCE_TAGGED = "SHOP_RESULT_PREFERENCE_TAGGED";
    public static final String SORT_ORDER = "SORT_ORDER";
    public static final String CUTOFF_BUFFER_MILLIS = "CUTOFF_BUFFER_MILLIS";
    public static final String NO_RESULT_ERROR_CODES = "NO_RESULT_ERROR_CODES";
    public static final String DEFAULT_SEAT_COUNT = "DEFAULT_SEAT_COUNT";
    public static final String TICKET_ISSUE_TIME = "TICKET_ISSUE_TIME";
    public static final String DEFAULT_PRODUCT_TYPE = "DEFAULT_PRODUCT_TYPE";
    public static final String SEARCH_CACHE_TTL_SECONDS = "SEARCH_CACHE_TTL_SECONDS";
    public static final String TOKEN_TTL_SECONDS = "TOKEN_TTL_SECONDS";
    // cabin_brand_configs (config group CABIN_BRAND, carrier PKF, cabin_brand_code = PKFARE cabin).
    // The carrier column is varchar(3), so PKFARE does not fit.
    public static final String CABIN_CARRIER = "PKF";
    public static final String CABIN_BRAND = "CABIN_BRAND";
    public static final String MAPPED_CABIN = "MAPPED_CABIN";

    public static final List<String> DEFAULT_FARE_PREFERENCES = List.of(VDC_PUBLIC_FARE_REMARK, VDC_PRIVATE_FARE_REMARK);
    public static final List<String> DEFAULT_SORT_ORDER = List.of("PRICE", "STOP", "DEPARTURE_TIME");
    public static final String DEFAULT_SHOP_RESULT_PREFERENCE = "BEST";
    public static final String DEFAULT_SHOP_RESULT_PREFERENCE_TAGGED = "OPTIMIZED";
    public static final long DEFAULT_CUTOFF_BUFFER_MILLIS = 500L;
    public static final int DEFAULT_TICKET_ISSUE_TIME = 120;
    public static final int DEFAULT_TIMEOUT_MILLIS = 30000;

    /* ------------------------------------------------------------------------
     * ERROR AND WARNING MESSAGES
     * --------------------------------------------------------------------- */
    public static final String UNAVAILABLE_REASON_CODE = "UNAVAILABLE";
    public static final String UNAUTHORIZED_REASON_CODE = "UNAUTHORIZED";
    public static final String UNIMPLEMENTED_REASON_CODE = "UNIMPLEMENTED";
    public static final String PROCESS_FAILURE_REASON_CODE = "PROCESS_FAILURE";
    public static final String VALIDATION_FAILURE_REASON_CODE = "VALIDATION_FAILURE";
    public static final String NO_RESULT_REASON_CODE = "NO_RESULT";
    public static final String ERROR_MSG_SERVICE_NOT_IMPLEMENTED = " service is not enabled";
    public static final String ERROR_MSG_INVALID_VENDOR = "Invalid vendorCode";
    public static final String ERROR_MSG_IP_NOT_WHITELISTED = "Request IP is not whitelisted";
    public static final String ERROR_MSG_AUTH_REJECTED = "Entry-Gate rejected the client credentials";
    public static final String ERROR_MSG_EMPTY_RESPONSE = "Empty response from Entry-Gate";
    public static final String ERROR_MSG_CABIN_MAPPING_MISSING = "CABIN_BRAND.MAPPED_CABIN is not configured for carrier PKF";
    public static final String CONNECTION_ERROR_MSG = "Unable to connect to Entry-Gate. Please try again after sometime";
    public static final String TOKEN_WAIT_TIMEOUT_MSG = "Timed out waiting for Entry-Gate token to be published by another instance";
    // PKFARE msg must stay under 64 characters, so the internal-error text is short enough to keep the txn id.
    public static final String PK_INTERNAL_ERROR_MSG = "Internal error, txn: ";
    public static final String STANDARD_ERROR_MESSAGE =
            "We are sorry, there is an internal error processing your request. Please contact support team with transaction id: ";

    /* ------------------------------------------------------------------------
     * CACHE CONSTANTS
     * --------------------------------------------------------------------- */
    public static final String REDIS_CACHE_KEY_LOCK = "lock";
    public static final String REDIS_CACHE_KEY_SEPARATOR = ":";
    public static final String REDIS_CACHE_KEY_SESSION = "session";
    public static final String REDIS_CACHE_KEY_PREFIX = "pkfare-adapter";
    public static final String REDIS_LOCK_RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    public static final long TOKEN_EXPIRY_BUFFER_SECONDS = 30L;
    public static final long DEFAULT_TOKEN_TTL_SECONDS = 30 * 60L;
    public static final long SERVICE_STATUS_CACHE_TTL_MINUTES = 5L;
    public static final long CABIN_MAPPING_CACHE_TTL_MINUTES = 5L;
    public static final long SEARCH_CACHE_MAX_ENTRIES = 2000L;

    public static final String LOCK_POLL_MILLIS = "LOCK_POLL_MILLIS";
    public static final String TOKEN_LOCK_WAIT_MILLIS = "TOKEN_LOCK_WAIT_MILLIS";
    public static final String TOKEN_LOCK_LEASE_MILLIS = "TOKEN_LOCK_LEASE_MILLIS";

    /* ------------------------------------------------------------------------
     * SYMBOL AND SPECIAL CHARACTER CONSTANTS
     * --------------------------------------------------------------------- */
    public static final String EMPTY = "";
    public static final String COLON = ":";
    public static final String PIPE = "|";
}
