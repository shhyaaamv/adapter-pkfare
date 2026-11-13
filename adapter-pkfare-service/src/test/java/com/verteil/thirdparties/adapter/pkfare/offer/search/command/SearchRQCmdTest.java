package com.verteil.thirdparties.adapter.pkfare.offer.search.command;

import com.verteil.schemas.ndc16_1.AirShoppingRQ;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.offer.search.converter.SearchRQConverter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AIRSHOPPING;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.searchRequest;
import static org.assertj.core.api.Assertions.assertThat;

class SearchRQCmdTest {

    private final SearchRQCmd cmd = new SearchRQCmd(new SearchRQConverter());

    @ParameterizedTest(name = "cutOffTime {0} -> {1}")
    @CsvSource(nullValues = "null", value = {
            "28000, 27500",   // capped by PKFARE cutoff minus the 500 ms buffer
            "40000, 30000",   // the 30 s timeout is already shorter
            "0,     30000",   // no cutoff sent
            "null,  30000",
            "800,   1000"     // never below 1 s
    })
    void storesRequestAndCapsTimeoutByCutoff(Integer cutOffTime, int expected) {
        var request = searchRequest();
        request.setCutOffTime(cutOffTime);
        PkfareAdapterContext context = buildContext(request);

        assertThat(cmd.execute(context)).isFalse();
        assertThat(context.getProviderApiService()).isEqualTo(SERVICE_AIRSHOPPING);
        assertThat(context.getProviderApiRequest(SERVICE_AIRSHOPPING)).isInstanceOf(AirShoppingRQ.class);
        assertThat(context.getTimeoutMillis()).isEqualTo(expected);
    }
}
