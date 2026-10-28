package com.verteil.thirdparties.adapter.pkfare.common.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.CABIN_MAPPING;
import static org.assertj.core.api.Assertions.assertThat;

class CabinMappingTest {

    @Test
    void mapsBothDirectionsIgnoringCaseAndWhitespace() {
        assertThat(CABIN_MAPPING.toVdc(" s ")).contains("W");
        assertThat(CABIN_MAPPING.toPk("w")).contains("S");
        assertThat(CABIN_MAPPING.toVdc("Y")).contains("Y");
        assertThat(CABIN_MAPPING.toVdc("P")).isEmpty();
        assertThat(CABIN_MAPPING.toPk(null)).isEmpty();
    }

    @Test
    void reverseMappingPrefersTheIdenticalCodeWhenCabinsShareAVdcCabin() {
        CabinMapping cabinMapping = CabinMapping.of(Map.of("S", "W", "W", "W", "A", "Y", "B", "Y"));

        assertThat(cabinMapping.toPk("W")).contains("W");
        assertThat(cabinMapping.toPk("Y")).contains("A");
    }

    @Test
    void ignoresBlankRows() {
        Map<String, String> rows = new HashMap<>();
        rows.put("C", "C");
        rows.put("F", null);
        rows.put(" ", "Y");

        assertThat(CabinMapping.of(rows).pkToVdc()).containsOnlyKeys("C");
        assertThat(CabinMapping.of(Map.of()).isEmpty()).isTrue();
    }
}
