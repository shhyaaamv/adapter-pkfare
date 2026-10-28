package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schemas.ndc16_1.AirShoppingRQ;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.Map;
import java.util.function.Consumer;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VALIDATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.NDC_MAPPER;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.readString;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.searchRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchRQConverterTest {

    private final SearchRQConverter converter = new SearchRQConverter();

    @Test
    void convertsRoundTripSearchToAirShoppingRQ() throws Exception {
        AirShoppingRQ airShoppingRQ = converter.convert(buildContext(searchRequest(), Map.of()));

        JSONAssert.assertEquals(readString("search/VDC_AirShoppingRQ_expected.json"),
                NDC_MAPPER.writeValueAsString(airShoppingRQ), JSONCompareMode.STRICT);
    }

    @Test
    void oneWayPremiumEconomyWithoutTagsUsesConfiguredPreferences() {
        SearchRequest request = searchRequest();
        request.setTripType("1");
        request.setRetDate(null);
        request.setCabinClass("s");
        request.setReturnTagPrice("N");

        AirShoppingRQ airShoppingRQ = converter.convert(buildContext(request, Map.of(
                "FARE_PREFERENCES", "PUBL",
                "SHOP_RESULT_PREFERENCE", "OPTIMIZED",
                "SORT_ORDER", "DEPARTURE_TIME")));

        assertThat(airShoppingRQ.getCoreQuery().getOriginDestinations().getOriginDestination()).hasSize(1);
        assertThat(airShoppingRQ.getPreference().getCabinPreferences().getCabinType().getFirst().getCode()).isEqualTo("W");
        assertThat(airShoppingRQ.getPreference().getFarePreferences().getTypes().getType())
                .extracting("code").containsExactly("PUBL");
        assertThat(airShoppingRQ.getResponseParameters().getShopResultPreference()).isEqualTo(AirShoppingRQ.ShopResultPreference.OPTIMIZED);
        assertThat(airShoppingRQ.getResponseParameters().getSortOrder())
                .extracting(sort -> sort.getParameter()).containsExactly(AirShoppingRQ.Sort.Parameter.DEPARTURE_TIME);
    }

    @Test
    void missingCabinDefaultsToEconomy() {
        SearchRequest request = searchRequest();
        request.setCabinClass(null);

        AirShoppingRQ airShoppingRQ = converter.convert(buildContext(request, Map.of()));

        assertThat(airShoppingRQ.getPreference().getCabinPreferences().getCabinType().getFirst().getCode()).isEqualTo("Y");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "no adults              | adultNumber=0",
            "too many children      | childNumber=10",
            "infants exceed adults  | infantNumber=3",
            "bad departure city     | depCity=CO1",
            "bad trip type          | tripType=3",
            "bad date format        | fromDate=2026-10-20",
            "impossible date        | fromDate=20260231",
            "missing return date    | retDate=",
            "return before outbound | retDate=20261019",
            "unknown cabin          | cabinClass=P"
    })
    void rejectsInvalidRequests(String description, String change) {
        SearchRequest request = searchRequest();
        apply(change).accept(request);

        assertThatThrownBy(() -> converter.convert(buildContext(request, Map.of())))
                .isInstanceOf(PkfareAdapterException.class)
                .extracting("errorCode").isEqualTo(VALIDATION_FAILURE.getCode());
    }

    private static Consumer<SearchRequest> apply(String change) {
        String[] parts = change.split("=", -1);
        String value = parts[1].isEmpty() ? null : parts[1];
        return switch (parts[0]) {
            case "adultNumber" -> r -> r.setAdultNumber(Integer.valueOf(value));
            case "childNumber" -> r -> r.setChildNumber(Integer.valueOf(value));
            case "infantNumber" -> r -> r.setInfantNumber(Integer.valueOf(value));
            case "depCity" -> r -> r.setDepCity(value);
            case "tripType" -> r -> r.setTripType(value);
            case "fromDate" -> r -> r.setFromDate(value);
            case "retDate" -> r -> r.setRetDate(value);
            case "cabinClass" -> r -> r.setCabinClass(value);
            default -> throw new IllegalArgumentException(change);
        };
    }
}
