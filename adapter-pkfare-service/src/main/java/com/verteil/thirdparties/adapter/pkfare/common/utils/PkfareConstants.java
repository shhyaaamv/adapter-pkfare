package com.verteil.thirdparties.adapter.pkfare.common.utils;

import java.time.Duration;
import java.util.Map;

public final class PkfareConstants {
    private PkfareConstants() {
    }

    public static final String PKFARE = "PKFARE";
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
    /** PKFARE cabin -> VDC cabin; premium economy is S on PKFARE and W on VDC. */
    public static final Map<String, String> CABIN_MAPPING = Map.of("F", "F", "C", "C", "S", "W", "Y", "Y");

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
    public static final String ENTRY_GATE_SERVICE_HEADER = "Service";
    public static final String ENTRY_GATE_TRANSACTION_ID_HEADER = "VDC_TransactionId";
    public static final String ENTRY_GATE_CIRCUIT_BREAKER = "entryGate";
    public static final String SUPPLIER_RESPONSE_TIME_LOGGER = "SupplierResponseTimeLogger";
    public static final String SUPPLIER_DURATION_METRIC = "supplier_transaction_duration";
    public static final String SUCCESS_RESPONSE = "SUCCESS";
    public static final String ERROR_RESPONSE = "ERROR";

    /* ------------------------------------------------------------------------
     * ERROR AND WARNING MESSAGES
     * --------------------------------------------------------------------- */
    public static final String UNAVAILABLE_REASON_CODE = "UNAVAILABLE";
    public static final String UNAUTHORIZED_REASON_CODE = "UNAUTHORIZED";
    /** OAuth2 token error for a wrong client id/secret (RFC 6749 §5.2). */
    public static final String OAUTH_INVALID_CLIENT = "invalid_client";
    public static final String UNIMPLEMENTED_REASON_CODE = "UNIMPLEMENTED";
    public static final String PROCESS_FAILURE_REASON_CODE = "PROCESS_FAILURE";
    public static final String VALIDATION_FAILURE_REASON_CODE = "VALIDATION_FAILURE";
    public static final String NO_RESULT_REASON_CODE = "NO_RESULT";
    public static final String ERROR_MSG_SERVICE_NOT_IMPLEMENTED = " service is not enabled";
    public static final String ERROR_MSG_INVALID_VENDOR = "Invalid vendorCode";
    public static final String ERROR_MSG_IP_NOT_WHITELISTED = "Request IP is not whitelisted";
    public static final String ERROR_MSG_AUTH_REJECTED = "Verteil rejected the client credentials";
    public static final String ERROR_MSG_EMPTY_RESPONSE = "Empty response from Verteil";
    public static final String CONNECTION_ERROR_MSG = "Unable to connect to Verteil. Please try again after sometime";
    public static final String TOKEN_WAIT_TIMEOUT_MSG = "Timed out waiting for Verteil token to be published by another instance";
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
    /** Minimum time between two re-reads of the Entry-Gate credentials secret after a rejected login. */
    public static final Duration CREDENTIALS_REFRESH_INTERVAL = Duration.ofMinutes(1);
    public static final long SEARCH_CACHE_MAX_ENTRIES = 2000L;

    /* ------------------------------------------------------------------------
     * SYMBOL AND SPECIAL CHARACTER CONSTANTS
     * --------------------------------------------------------------------- */
    public static final String EMPTY = "";
    public static final String COLON = ":";
    public static final String PIPE = "|";
}
