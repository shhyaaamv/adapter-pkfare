package com.verteil.thirdparties.adapter.pkfare.common.converter;

import com.verteil.schema.pkfare.SearchResponse;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_SEARCH;
import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseConverterTest {

    private final ErrorResponseConverter converter = new ErrorResponseConverter();

    @ParameterizedTest(name = "{0} -> status {1}")
    @CsvSource({
            "VALIDATION_FAILURE, 4",
            "AUTHENTICATION_FAILURE, 3",
            "SERVICE_DISABLED, 3",
            "ENTRY_GATE_ERROR, 3",
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
                new PkfareAdapterException("ENTRY_GATE_ERROR", "x".repeat(200)), SERVICE_SEARCH);

        assertThat(response.getMsg()).hasSizeLessThan(64);
    }
}
