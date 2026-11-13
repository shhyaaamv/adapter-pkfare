package com.verteil.thirdparties.adapter.pkfare.common.converter;

import com.verteil.schema.pkfare.QteResponse;
import com.verteil.schema.pkfare.SearchResponse;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.AUTHENTICATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VERTEIL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.SERVICE_DISABLED;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VALIDATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.INVALID_DATE_RANGE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.INVALID_FARE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.MSG_INVALID_DATE_RANGE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.MSG_INVALID_FARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.MSG_NO_FARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.MSG_UPSTREAM_TIMEOUT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.NO_FARE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.NO_RESULT_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_ERROR_MSG_MAX_LENGTH;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_MSG_MAX_LENGTH;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_INVALID_DATE_RANGE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_INVALID_FARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_NO_FARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_OTHER_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_PROGRAM_EXCEPTION;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_REQUEST_PARAMETER_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_SUCCESS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_UPSTREAM_TIMEOUT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_SUCCESS_MSG;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PROCESS_FAILURE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_QTE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_SEARCH;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAUTHORIZED_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAVAILABLE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNIMPLEMENTED_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VALIDATION_FAILURE_REASON_CODE;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * PKFARE always receives HTTP 200 with a business {@code status}: 0 success, 3 other failure,
 * 4 request parameter error, 5 program exception; {@code msg} must stay under 64 characters.
 * QTE adds 7 (Entry-Gate timeout), 111 (invalid date range), 201 (no fare) and 202 (invalid fare), plus
 * {@code errorCode}/{@code errorMsg} carrying the Entry-Gate error when the failure came from FlightPriceRS errors.
 */
@Slf4j
@Component
public class ErrorResponseConverter {

    private record QteStatus(int status, String defaultMsg) {}

    /** QTE statuses picked by the exception reason, ahead of the generic error-code mapping. */
    private static final Map<String, QteStatus> QTE_REASON_STATUS = Map.of(
            INVALID_DATE_RANGE_REASON_CODE, new QteStatus(PK_STATUS_INVALID_DATE_RANGE, MSG_INVALID_DATE_RANGE),
            NO_FARE_REASON_CODE, new QteStatus(PK_STATUS_NO_FARE, MSG_NO_FARE),
            INVALID_FARE_REASON_CODE, new QteStatus(PK_STATUS_INVALID_FARE, MSG_INVALID_FARE)
    );

    /** Reasons the adapter sets itself; any other VERTEIL_ERROR reason is an Entry-Gate error code. */
    private static final Set<String> ADAPTER_REASON_CODES = Set.of(UNAVAILABLE_REASON_CODE, UNAUTHORIZED_REASON_CODE,
            UNIMPLEMENTED_REASON_CODE, PROCESS_FAILURE_REASON_CODE, VALIDATION_FAILURE_REASON_CODE, NO_RESULT_REASON_CODE,
            INVALID_DATE_RANGE_REASON_CODE, NO_FARE_REASON_CODE, INVALID_FARE_REASON_CODE);

    public Object createErrorResponse(PkfareAdapterException exception, String service) {
        return switch (service) {
            case SERVICE_SEARCH -> mapSearchResponse(toPkfareStatus(exception.getErrorCode()), exception.getMessage());
            case SERVICE_QTE -> mapQteErrorResponse(exception);
            default -> mapSearchResponse(PK_STATUS_PROGRAM_EXCEPTION, exception.getMessage());
        };
    }

    /** "No content" answer PKFARE asks for: status 0 with empty lists. */
    public SearchResponse mapEmptySearchResponse() {
        return mapSearchResponse(PK_STATUS_SUCCESS, PK_SUCCESS_MSG);
    }

    /** QTE answer without an Entry-Gate error, e.g. the seat statuses 1/2. */
    public QteResponse mapQteResponse(int status, String msg) {
        return mapQteResponse(status, msg, null, null);
    }

    public static int toPkfareStatus(String errorCode) {
        if (VALIDATION_FAILURE.getCode().equals(errorCode)) {
            return PK_STATUS_REQUEST_PARAMETER_ERROR;
        }
        if (AUTHENTICATION_FAILURE.getCode().equals(errorCode) || VERTEIL_ERROR.getCode().equals(errorCode)
                || SERVICE_DISABLED.getCode().equals(errorCode)) {
            return PK_STATUS_OTHER_FAILURE;
        }
        return PK_STATUS_PROGRAM_EXCEPTION;
    }

    private QteResponse mapQteErrorResponse(PkfareAdapterException exception) {
        if (isUpstreamTimeout(exception)) {
            return mapQteResponse(PK_STATUS_UPSTREAM_TIMEOUT, MSG_UPSTREAM_TIMEOUT);
        }
        return Optional.ofNullable(exception.getReason())
                .map(QTE_REASON_STATUS::get)
                .map(reasonStatus -> mapReasonResponse(reasonStatus, exception))
                .orElseGet(() -> mapQteFailureResponse(exception));
    }

    private QteResponse mapReasonResponse(QteStatus reasonStatus, PkfareAdapterException exception) {
        return mapQteResponse(reasonStatus.status(), defaultIfBlank(exception.getMessage(), reasonStatus.defaultMsg()));
    }

    /** VDC errors keep their code/text in errorCode/errorMsg; everything else maps like SEARCH. */
    private QteResponse mapQteFailureResponse(PkfareAdapterException exception) {
        if (isEntryGateError(exception)) {
            return mapQteResponse(PK_STATUS_OTHER_FAILURE, exception.getMessage(), exception.getReason(),
                    abbreviate(exception.getMessage(), PK_ERROR_MSG_MAX_LENGTH));
        }
        return mapQteResponse(toPkfareStatus(exception.getErrorCode()), exception.getMessage());
    }

    private static boolean isUpstreamTimeout(PkfareAdapterException exception) {
        return VERTEIL_ERROR.getCode().equals(exception.getErrorCode())
                && ExceptionUtils.indexOfType(exception, SocketTimeoutException.class) >= 0;
    }

    private static boolean isEntryGateError(PkfareAdapterException exception) {
        return VERTEIL_ERROR.getCode().equals(exception.getErrorCode())
                && isNotBlank(exception.getReason())
                && !ADAPTER_REASON_CODES.contains(exception.getReason());
    }

    private static QteResponse mapQteResponse(int status, String msg, String errorCode, String errorMsg) {
        QteResponse response = new QteResponse();
        response.setStatus(status);
        response.setMsg(mapPkfareMsg(msg));
        response.setErrorCode(errorCode);
        response.setErrorMsg(errorMsg);
        return response;
    }

    private static SearchResponse mapSearchResponse(int status, String msg) {
        SearchResponse response = new SearchResponse();
        response.setStatus(status);
        response.setMsg(mapPkfareMsg(msg));
        return response;
    }

    private static String mapPkfareMsg(String msg) {
        return abbreviate(defaultIfBlank(msg, "failure"), PK_MSG_MAX_LENGTH);
    }
}
