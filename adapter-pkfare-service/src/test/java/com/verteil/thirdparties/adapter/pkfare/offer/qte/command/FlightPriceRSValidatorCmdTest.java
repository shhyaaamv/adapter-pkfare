package com.verteil.thirdparties.adapter.pkfare.offer.qte.command;

import com.verteil.schemas.ndc16_1.ErrorType;
import com.verteil.schemas.ndc16_1.ErrorsType;
import com.verteil.schemas.ndc16_1.FlightPriceRS;
import com.verteil.schemas.ndc16_1.PricedFlightOffer;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import org.junit.jupiter.api.Test;


import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VERTEIL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_FLIGHTPRICE;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildQteContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.flightPriceRS;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.qteRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlightPriceRSValidatorCmdTest {

    private final FlightPriceRSValidatorCmd cmd = new FlightPriceRSValidatorCmd();

    @Test
    void continuesWhenAPricedOfferIsPresent() {
        PkfareAdapterContext context = context(flightPriceRS());

        assertThat(cmd.execute(context)).isFalse();
        assertThat(context.getResponse()).isNull();
    }

    @Test
    void continuesWhenErrorsComeWithAPricedOffer() {
        FlightPriceRS response = flightPriceRS();
        response.setErrors(errors("911", null, "Partial warning", null));

        assertThat(cmd.execute(context(response))).isFalse();
    }

    @Test
    void continuesWithSeveralPricedOffers() {
        FlightPriceRS response = flightPriceRS();
        response.getPricedFlightOffers().getPricedFlightOffer().add(new PricedFlightOffer());

        assertThat(cmd.execute(context(response))).isFalse();
    }

    @Test
    void failsAsUnavailableWhenEntryGateReturnedNothing() {
        assertException(context(null), "Empty response from Verteil", "UNAVAILABLE");
    }

    @Test
    void failsWithTheFirstErrorWhenThereIsNoPricedOffer() {
        FlightPriceRS response = new FlightPriceRS();
        response.setErrors(errors("SC-1001", null, "Offer expired", "ignored"));

        assertException(context(response), "Offer expired", "SC-1001");
    }

    @Test
    void fallsBackToErrorValueAndReason() {
        FlightPriceRS response = new FlightPriceRS();
        response.setErrors(errors(null, "OFFER_NOT_FOUND", null, "Offer not found"));

        assertException(context(response), "Offer not found", "OFFER_NOT_FOUND");
    }

    @Test
    void reportsNoFareWithoutPricedOfferOrErrors() {
        assertException(context(new FlightPriceRS()), "No fare returned", "NO_FARE");

        FlightPriceRS emptyOffers = new FlightPriceRS();
        emptyOffers.setPricedFlightOffers(new FlightPriceRS.PricedFlightOffers());
        assertException(context(emptyOffers), "No fare returned", "NO_FARE");
    }

    private static void assertException(PkfareAdapterContext context, String msg, String reason) {
        assertThatThrownBy(() -> new FlightPriceRSValidatorCmd().execute(context))
                .isInstanceOf(PkfareAdapterException.class)
                .hasMessage(msg)
                .satisfies(e -> {
                    assertThat(((PkfareAdapterException) e).getErrorCode()).isEqualTo(VERTEIL_ERROR.getCode());
                    assertThat(((PkfareAdapterException) e).getReason()).isEqualTo(reason);
                });
    }

    private static ErrorsType errors(String code, String reason, String shortText, String value) {
        ErrorType error = new ErrorType();
        error.setCode(code);
        error.setReason(reason);
        error.setShortText(shortText);
        error.setValue(value);
        ErrorsType errors = new ErrorsType();
        errors.getError().add(error);
        return errors;
    }

    private static PkfareAdapterContext context(FlightPriceRS response) {
        PkfareAdapterContext context = buildQteContext(qteRequest());
        context.setProviderApiResponse(SERVICE_FLIGHTPRICE, response);
        return context;
    }
}
