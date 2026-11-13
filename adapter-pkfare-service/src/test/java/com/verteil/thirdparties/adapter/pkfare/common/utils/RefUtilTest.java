package com.verteil.thirdparties.adapter.pkfare.common.utils;

import com.verteil.schemas.ndc16_1.ListOfFlightSegmentType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RefUtilTest {

    @Test
    void readsTheKeyOfAStringRef() {
        assertThat(RefUtil.key("AI-SEG1")).isEqualTo("AI-SEG1");
    }

    @Test
    void readsTheKeyOfAnOfferRefObject() {
        assertThat(RefUtil.key(Map.of("Ref", "VDC-AIRLINE-SRID"))).isEqualTo("VDC-AIRLINE-SRID");
    }

    @Test
    void readsTheKeyOfAResolvedObject() {
        var segment = new ListOfFlightSegmentType();
        segment.setSegmentKey("AI-SEG9");

        assertThat(RefUtil.key(segment)).isEqualTo("AI-SEG9");
    }

    @Test
    void readsKeysOfMixedRefsSkippingNulls() {
        List<Object> refs = Arrays.asList("A", Map.of("Ref", "B"), null);

        assertThat(RefUtil.keys(refs)).containsExactly("A", "B");
        assertThat(RefUtil.keys(null)).isEmpty();
    }
}
