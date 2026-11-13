package com.verteil.thirdparties.adapter.pkfare.offer.common.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentMatchKeyTest {

    @Test
    void joinsTrimmedUpperCasedPartsWithPipes() {
        assertThat(SegmentMatchKey.of(" cok", "202611180750", "BOM ", "202611181015", "ai", 9896))
                .isEqualTo("COK|202611180750|BOM|202611181015|AI|9896");
    }

    @Test
    void writesMissingPartsAsEmpty() {
        assertThat(SegmentMatchKey.of("COK", null, "BOM", "202611181015", "AI", null))
                .isEqualTo("COK||BOM|202611181015|AI|");
    }
}
