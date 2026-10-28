package com.verteil.thirdparties.adapter.pkfare.offer.search.command;

import com.verteil.schemas.ndc16_1.AirShoppingRQ;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.offer.search.converter.SearchRQConverter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AIRSHOPPING;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.searchRequest;
import static org.assertj.core.api.Assertions.assertThat;

class SearchRQCmdTest {

    private final SearchRQCmd cmd = new SearchRQCmd(new SearchRQConverter());

    @ParameterizedTest(name = "timeout {0}, cutOffTime {1}, buffer {2} -> {3}")
    @CsvSource(nullValues = "null", value = {
            "30000, 28000, null, 27500",   // capped by PKFARE cutoff minus default 500 ms buffer
            "20000, 28000, null, 20000",   // configured timeout already shorter
            "30000, 28000, 3000, 25000",
            "30000, 0,     null, 30000",   // no cutoff sent
            "30000, null,  null, 30000",
            "30000, 800,   null, 1000",    // never below 1 s
            "null,  null,  null, 30000"    // default timeout
    })
    void storesRequestAndCapsTimeoutByCutoff(String timeout, Integer cutOffTime, String buffer, int expected) {
        var request = searchRequest();
        request.setCutOffTime(cutOffTime);
        Map<String, String> config = new HashMap<>();
        Optional.ofNullable(timeout).ifPresent(value -> config.put("TIMEOUT_MILLIS", value));
        Optional.ofNullable(buffer).ifPresent(value -> config.put("CUTOFF_BUFFER_MILLIS", value));
        PkfareAdapterContext context = buildContext(request, config);

        assertThat(cmd.execute(context)).isFalse();
        assertThat(context.getProviderApiService()).isEqualTo(SERVICE_AIRSHOPPING);
        assertThat(context.getProviderApiRequest(SERVICE_AIRSHOPPING)).isInstanceOf(AirShoppingRQ.class);
        assertThat(context.getTimeoutMillis()).isEqualTo(expected);
    }
}
