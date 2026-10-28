package com.verteil.thirdparties.adapter.pkfare.offer.search.command;

import com.verteil.schema.pkfare.SearchResponse;
import com.verteil.schemas.ndc16_1.AirShoppingRS;
import com.verteil.schemas.ndc16_1.ErrorType;
import com.verteil.schemas.ndc16_1.ErrorsType;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.converter.ErrorResponseConverter;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.ENTRY_GATE_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AIRSHOPPING;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.airShoppingRS;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.searchRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AirShoppingRSValidatorCmdTest {

    private final AirShoppingRSValidatorCmd cmd = new AirShoppingRSValidatorCmd(new ErrorResponseConverter());

    @Test
    void continuesWhenOffersArePresent() {
        assertThat(cmd.execute(context(airShoppingRS(), Map.of()))).isFalse();
    }

    @Test
    void returnsEmptySuccessWhenThereAreNoOffersAndNoErrors() {
        PkfareAdapterContext context = context(new AirShoppingRS(), Map.of());

        assertThat(cmd.execute(context)).isTrue();
        assertEmptySuccess(context);
    }

    @Test
    void treatsConfiguredErrorCodesAsNoResult() {
        PkfareAdapterContext context = context(errorResponse("911", "No fares available"), Map.of("NO_RESULT_ERROR_CODES", "404,911"));

        assertThat(cmd.execute(context)).isTrue();
        assertEmptySuccess(context);
    }

    @Test
    void failsOnOtherErrorsWithoutOffers() {
        PkfareAdapterContext context = context(errorResponse("INTERNAL_ERROR", "Airline unavailable"), Map.of("NO_RESULT_ERROR_CODES", "911"));

        assertThatThrownBy(() -> cmd.execute(context))
                .isInstanceOf(PkfareAdapterException.class)
                .hasMessage("Airline unavailable")
                .extracting("errorCode").isEqualTo(ENTRY_GATE_ERROR.getCode());
    }

    @Test
    void failsWhenEntryGateReturnedNothing() {
        assertThatThrownBy(() -> cmd.execute(context(null, Map.of())))
                .isInstanceOf(PkfareAdapterException.class)
                .extracting("errorCode").isEqualTo(ENTRY_GATE_ERROR.getCode());
    }

    private static void assertEmptySuccess(PkfareAdapterContext context) {
        SearchResponse response = (SearchResponse) context.getResponse();
        assertThat(response.getStatus()).isZero();
        assertThat(response.getShoppingResultList()).isEmpty();
        assertThat(response.getFlightList()).isEmpty();
    }

    private static AirShoppingRS errorResponse(String code, String shortText) {
        ErrorType error = new ErrorType();
        error.setCode(code);
        error.setValue(code);
        error.setShortText(shortText);
        ErrorsType errors = new ErrorsType();
        errors.getError().add(error);
        AirShoppingRS response = new AirShoppingRS();
        response.setErrors(errors);
        return response;
    }

    private static PkfareAdapterContext context(AirShoppingRS response, Map<String, String> config) {
        PkfareAdapterContext context = buildContext(searchRequest(), config);
        context.setProviderApiResponse(SERVICE_AIRSHOPPING, response);
        return context;
    }
}
