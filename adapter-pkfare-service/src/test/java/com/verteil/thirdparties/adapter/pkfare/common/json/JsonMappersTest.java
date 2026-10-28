package com.verteil.thirdparties.adapter.pkfare.common.json;

import com.verteil.schemas.ndc16_1.AirShoppingRS;
import com.verteil.schemas.ndc16_1.FlightDepartureType;
import com.verteil.thirdparties.adapter.pkfare.common.utils.RefUtil;
import org.junit.jupiter.api.Test;

import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.NDC_MAPPER;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.airShoppingRS;
import static org.assertj.core.api.Assertions.assertThat;

class JsonMappersTest {

    @Test
    void readsEntryGateDateTimesDurationsAndUnresolvedReferences() {
        AirShoppingRS response = airShoppingRS();

        var segment = response.getDataLists().getFlightSegmentList().getFlightSegment().getFirst();
        assertThat(segment.getDeparture().getDate().getYear()).isEqualTo(2026);
        assertThat(segment.getDeparture().getDate().getHour()).isEqualTo(21);
        assertThat(segment.getFlightDetail().getFlightDuration().getValue().getHours()).isEqualTo(3);
        var reference = response.getOffersGroup().getAirlineOffers().getFirst().getAirlineOffer().getFirst()
                .getPricedOffer().getAssociations().getFirst().getApplicableFlight().getFlightSegmentReference().getFirst();
        assertThat(RefUtil.key(reference.getRef())).isEqualTo("AI-SEG9");
    }

    @Test
    void writesDateOnlyValuesInLexicalForm() throws Exception {
        var departure = NDC_MAPPER.readValue("{\"Date\":\"2026-10-20\"}", FlightDepartureType.class);

        assertThat(NDC_MAPPER.writeValueAsString(departure)).isEqualTo("{\"Date\":\"2026-10-20\"}");
    }
}
