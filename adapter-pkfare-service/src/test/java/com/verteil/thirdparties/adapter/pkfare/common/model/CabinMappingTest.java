package com.verteil.thirdparties.adapter.pkfare.common.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CabinMappingTest {

    @Test
    void mapsBothDirectionsIgnoringCaseAndWhitespace() {
        assertThat(CabinMapping.DEFAULT.toVdc(" s ")).contains("W");
        assertThat(CabinMapping.DEFAULT.toPk("w")).contains("S");
        assertThat(CabinMapping.DEFAULT.toVdc("Y")).contains("Y");
        assertThat(CabinMapping.DEFAULT.toVdc("P")).isEmpty();
        assertThat(CabinMapping.DEFAULT.toPk(null)).isEmpty();
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
