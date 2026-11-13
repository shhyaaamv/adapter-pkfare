package com.verteil.thirdparties.adapter.pkfare.offer.qte.command;

import com.verteil.schema.pkfare.QteRequest;
import com.verteil.schemas.ndc16_1.FlightPriceRQ;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferDataCodec;
import com.verteil.thirdparties.adapter.pkfare.offer.qte.converter.QteRQConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;


import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_INVALID_ROUTING_DATA;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_FLIGHTPRICE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_QTE;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildQteContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.qteRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QteRQCmdTest {

    private final OfferDataCodec codec = new OfferDataCodec();
    private final QteRQCmd cmd = new QteRQCmd(codec, new QteRQConverter());

    @Test
    void storesOfferDataAndFlightPriceRQUnderTheEntryGateService() {
        PkfareAdapterContext context = buildQteContext(qteRequest());

        assertThat(cmd.execute(context)).isFalse();

        assertThat(context.getProviderApiService()).isEqualTo(SERVICE_FLIGHTPRICE);
        assertThat(context.getProviderApiRequest(SERVICE_FLIGHTPRICE)).isInstanceOf(FlightPriceRQ.class);
        assertThat(context.getProviderApiRequest(SERVICE_QTE)).isNull();
        assertThat(context.getOfferData().offer().id()).isEqualTo("1H2AIZ_GEX1FB4YZK0ZTRJE4UER2X8P064Z");
    }

    @ParameterizedTest(name = "cutOffTime {0} -> {1}")
    @CsvSource(nullValues = "null", value = {
            "28000, 27500",   // capped by PKFARE cutoff minus the 500 ms buffer
            "48000, 30000",   // the 30 s timeout is already shorter
            "0,     30000",   // no cutoff sent
            "null,  30000",
            "800,   1000"     // never below 1 s
    })
    void capsTimeoutByCutoff(Integer cutOffTime, int expected) {
        QteRequest request = qteRequest();
        request.setCutOffTime(cutOffTime);
        PkfareAdapterContext context = buildQteContext(request);

        cmd.execute(context);

        assertThat(context.getTimeoutMillis()).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not base64 !", "e30="})   // e30= is "{}": decodes, but lacks everything FlightPriceRQ needs
    void rejectsUnusableRoutingData(String data) {
        QteRequest request = qteRequest();
        request.getRouting().setData(data);

        assertInvalidRoutingData(buildQteContext(request));
    }

    @Test
    void rejectsAMissingRequestAsBadRequest() {
        assertInvalidRoutingData(buildQteContext(null));
    }

    private void assertInvalidRoutingData(PkfareAdapterContext context) {
        assertThatThrownBy(() -> cmd.execute(context))
                .isInstanceOfSatisfying(PkfareAdapterException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("VALIDATION_FAILURE");
                    assertThat(exception.getMessage()).isEqualTo(ERROR_MSG_INVALID_ROUTING_DATA);
                });
        assertThat(context.getProviderApiRequest(SERVICE_FLIGHTPRICE)).isNull();
    }
}
