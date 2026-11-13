package com.verteil.thirdparties.adapter.pkfare.common.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.verteil.schema.pkfare.QteResponse;
import com.verteil.schema.pkfare.SearchResponse;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_QTE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_SEARCH;
import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseConverterTest {

    private final ErrorResponseConverter converter = new ErrorResponseConverter();

    @ParameterizedTest(name = "{0} -> status {1}")
    @CsvSource({
            "VALIDATION_FAILURE, 4",
            "AUTHENTICATION_FAILURE, 3",
            "SERVICE_DISABLED, 3",
            "VERTEIL_ERROR, 3",
            "INTERNAL_ERROR, 5"
    })
    void mapsErrorTypesToPkfareStatus(String errorCode, int status) {
        SearchResponse response = (SearchResponse) converter.createErrorResponse(new PkfareAdapterException(errorCode, "failed"), SERVICE_SEARCH);

        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getMsg()).isEqualTo("failed");
        assertThat(response.getShoppingResultList()).isEmpty();
        assertThat(response.getFlightList()).isEmpty();
    }

    @Test
    void keepsMessageUnder64Characters() {
        SearchResponse response = (SearchResponse) converter.createErrorResponse(
                new PkfareAdapterException("VERTEIL_ERROR", "x".repeat(200)), SERVICE_SEARCH);

        assertThat(response.getMsg()).hasSizeLessThan(64);
    }

    @ParameterizedTest(name = "QTE {0}/{1} -> status {2}")
    @CsvSource(nullValues = "null", value = {
            "VALIDATION_FAILURE, VALIDATION_FAILURE, 4",
            "VALIDATION_FAILURE, INVALID_DATE_RANGE, 111",
            "AUTHENTICATION_FAILURE, UNAUTHORIZED, 3",
            "SERVICE_DISABLED, UNIMPLEMENTED, 3",
            "VERTEIL_ERROR, UNAVAILABLE, 3",
            "VERTEIL_ERROR, NO_FARE, 201",
            "VERTEIL_ERROR, INVALID_FARE, 202",
            "INTERNAL_ERROR, INVALID_FARE, 202",
            "INTERNAL_ERROR, null, 5"
    })
    void mapsQteErrorsToPkfareStatus(String errorCode, String reason, int status) {
        QteResponse response = qte(new PkfareAdapterException(errorCode, "failed", reason));

        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getMsg()).isEqualTo("failed");
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getErrorMsg()).isNull();
        assertThat(response.getRouting()).isNull();
    }

    @ParameterizedTest(name = "QTE reason {0} without message -> {1}")
    @CsvSource({
            "INVALID_DATE_RANGE, Invalid date range",
            "NO_FARE, No fare returned",
            "INVALID_FARE, Invalid fare returned"
    })
    void defaultsQteReasonMessages(String reason, String msg) {
        assertThat(qte(new PkfareAdapterException("VALIDATION_FAILURE", null, reason)).getMsg()).isEqualTo(msg);
    }

    @Test
    void mapsEntryGateTimeoutToStatus7() {
        var timeout = new IOException("wrapped", new SocketTimeoutException("Read timed out"));
        QteResponse response = qte(new PkfareAdapterException("VERTEIL_ERROR", "Unable to connect", timeout, "UNAVAILABLE"));

        assertThat(response.getStatus()).isEqualTo(7);
        assertThat(response.getMsg()).isEqualTo("Upstream timeout");
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    void ignoresTimeoutCauseOutsideEntryGateErrors() {
        var response = qte(new PkfareAdapterException("INTERNAL_ERROR", "boom", new SocketTimeoutException(), null));

        assertThat(response.getStatus()).isEqualTo(5);
    }

    @Test
    void carriesEntryGateErrorInErrorCodeAndErrorMsg() {
        String text = "No fares available for the requested itinerary ".repeat(10);
        QteResponse response = qte(new PkfareAdapterException("VERTEIL_ERROR", text, "911"));

        assertThat(response.getStatus()).isEqualTo(3);
        assertThat(response.getMsg()).hasSizeLessThan(64);
        assertThat(response.getErrorCode()).isEqualTo("911");
        assertThat(response.getErrorMsg()).hasSize(299).startsWith("No fares available");
    }

    @Test
    void qteResponseWithoutEntryGateError() {
        QteResponse response = converter.mapQteResponse(1, "Insufficient remaining seats");

        assertThat(response.getStatus()).isEqualTo(1);
        assertThat(response.getMsg()).isEqualTo("Insufficient remaining seats");
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getErrorMsg()).isNull();
    }

    @Test
    void serialisesQteErrorWithNullErrorFields() {
        JsonNode json = JsonMappers.pkfare().valueToTree(qte(new PkfareAdapterException("VALIDATION_FAILURE", "bad")));

        assertThat(json.get("status").asInt()).isEqualTo(4);
        assertThat(json.has("errorCode")).isTrue();
        assertThat(json.get("errorCode").isNull()).isTrue();
        assertThat(json.has("errorMsg")).isTrue();
    }

    private QteResponse qte(PkfareAdapterException exception) {
        return (QteResponse) converter.createErrorResponse(exception, SERVICE_QTE);
    }
}
