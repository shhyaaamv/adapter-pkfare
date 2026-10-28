package com.verteil.thirdparties.adapter.pkfare.common.converter;

import com.verteil.schema.pkfare.SearchResponse;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.AUTHENTICATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.ENTRY_GATE_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.SERVICE_DISABLED;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VALIDATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_MSG_MAX_LENGTH;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_OTHER_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_PROGRAM_EXCEPTION;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_REQUEST_PARAMETER_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_STATUS_SUCCESS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PK_SUCCESS_MSG;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_SEARCH;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

/**
 * PKFARE always receives HTTP 200 with a business {@code status}: 0 success, 3 other failure,
 * 4 request parameter error, 5 program exception; {@code msg} must stay under 64 characters.
 */
@Slf4j
@Component
public class ErrorResponseConverter {

    public Object createErrorResponse(PkfareAdapterException exception, String service) {
        return switch (service) {
            case SERVICE_SEARCH -> searchResponse(toPkfareStatus(exception.getErrorCode()), exception.getMessage());
            default -> searchResponse(PK_STATUS_PROGRAM_EXCEPTION, exception.getMessage());
        };
    }

    /** "No content" answer PKFARE asks for: status 0 with empty lists. */
    public SearchResponse emptySearchResponse() {
        return searchResponse(PK_STATUS_SUCCESS, PK_SUCCESS_MSG);
    }

    public static int toPkfareStatus(String errorCode) {
        if (VALIDATION_FAILURE.getCode().equals(errorCode)) {
            return PK_STATUS_REQUEST_PARAMETER_ERROR;
        }
        if (AUTHENTICATION_FAILURE.getCode().equals(errorCode) || ENTRY_GATE_ERROR.getCode().equals(errorCode)
                || SERVICE_DISABLED.getCode().equals(errorCode)) {
            return PK_STATUS_OTHER_FAILURE;
        }
        return PK_STATUS_PROGRAM_EXCEPTION;
    }

    private static SearchResponse searchResponse(int status, String msg) {
        SearchResponse response = new SearchResponse();
        response.setStatus(status);
        response.setMsg(abbreviate(defaultIfBlank(msg, "failure"), PK_MSG_MAX_LENGTH));
        return response;
    }
}
