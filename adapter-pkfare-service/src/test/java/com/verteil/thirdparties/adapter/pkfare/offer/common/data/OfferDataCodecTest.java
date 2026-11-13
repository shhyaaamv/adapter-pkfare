package com.verteil.thirdparties.adapter.pkfare.offer.common.data;

import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.OfferRef;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.ShoppingResponseRef;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.DataPassenger;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.DataSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferDataCodecTest {

    private static final String OFFER_DATA_JSON = """
            {
              "shoppingResponseId": {"owner": "AI", "value": "LSbPSOlari--6t34oGYyTa7POI7WaD6ZKvWU2OOtC08-AI"},
              "offer": {"id": "1H2AIZ_GEX1FB4YZK0ZTRJE4UER2X8P064Z", "owner": "AI"},
              "passengers": [{"offerItemId": "1H2AIZ_GEX1FB4YZK0ZTRJE4UER2X8P064Z-1-2", "ptc": "ADT",
                              "travelerRefs": ["AI-PAX1", "AI-PAX2"]}],
              "segments": {"COK|202611180750|BOM|202611181015|AI|9896": "AI-SEG15|GL2YXSI3",
                           "BOM|202611181630|DXB|202611181835|AI|911": "AI-SEG4"}
            }
            """;

    private final OfferDataCodec codec = new OfferDataCodec();

    @Test
    void decodesTheDocumentedOfferDataShape() {
        OfferData offerData = codec.decode(base64(OFFER_DATA_JSON));

        assertThat(offerData.shoppingResponseId()).isEqualTo(new ShoppingResponseRef("AI", "LSbPSOlari--6t34oGYyTa7POI7WaD6ZKvWU2OOtC08-AI"));
        assertThat(offerData.offer()).isEqualTo(new OfferRef("1H2AIZ_GEX1FB4YZK0ZTRJE4UER2X8P064Z", "AI"));
        assertThat(offerData.passengers()).containsExactly(
                new DataPassenger("1H2AIZ_GEX1FB4YZK0ZTRJE4UER2X8P064Z-1-2", "ADT", List.of("AI-PAX1", "AI-PAX2")));
        assertThat(offerData.segments().keySet()).containsExactly(
                "COK|202611180750|BOM|202611181015|AI|9896", "BOM|202611181630|DXB|202611181835|AI|911");
        assertThat(offerData.segment("COK|202611180750|BOM|202611181015|AI|9896"))
                .contains(new DataSegment("AI-SEG15", "GL2YXSI3"));
        assertThat(offerData.segment("BOM|202611181630|DXB|202611181835|AI|911"))
                .contains(new DataSegment("AI-SEG4", null));
        assertThat(offerData.segment("DXB|202611292340|BOM|202611300430|AI|984")).isEmpty();
    }

    @Test
    void keepsTheSegmentOrderThroughARoundTrip() {
        Map<String, String> segments = new LinkedHashMap<>();
        List.of("Z", "A", "M", "B").forEach(matchKey -> segments.put(matchKey, "SEG-" + matchKey));
        OfferData offerData = OfferData.builder().segments(segments).build();

        assertThat(codec.decode(codec.encode(offerData)).segments().keySet()).containsExactly("Z", "A", "M", "B");
    }

    @Test
    void encodesTheSegmentValueAsSegmentKeyPipeFareBasis() {
        assertThat(new DataSegment("AI-SEG15", "GL2YXSI3").encode()).isEqualTo("AI-SEG15|GL2YXSI3");
        assertThat(new DataSegment("AI-SEG15", " ").encode()).isEqualTo("AI-SEG15");
        assertThat(new DataSegment("AI-SEG15", null).encode()).isEqualTo("AI-SEG15");
        assertThat(DataSegment.decode("AI-SEG15|")).isEqualTo(new DataSegment("AI-SEG15", null));
        assertThat(DataSegment.decode("|GL2YXSI3")).isEqualTo(new DataSegment(null, "GL2YXSI3"));
        assertThat(DataSegment.decode("")).isEqualTo(new DataSegment(null, null));
    }

    @Test
    void hasNoSegmentWithoutASegmentKey() {
        OfferData offerData = OfferData.builder().segments(Map.of("K1", "|GL2YXSI3", "K2", "")).build();

        assertThat(offerData.segment("K1")).isEmpty();
        assertThat(offerData.segment("K2")).isEmpty();
        assertThat(OfferData.builder().build().segment("K1")).isEmpty();
    }

    @Test
    void encodesStandardBase64OfTheDocumentedJsonAndRoundTrips() throws Exception {
        OfferData offerData = codec.decode(base64(OFFER_DATA_JSON));

        String data = codec.encode(offerData);

        String json = new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
        JSONAssert.assertEquals(OFFER_DATA_JSON, json, JSONCompareMode.STRICT);
        assertThat(codec.decode(data)).isEqualTo(offerData);
    }

    @Test
    void leavesOutNullFields() {
        OfferData offerData = OfferData.builder()
                .segments(Map.of("K1", "SEG1"))
                .build();

        String json = new String(Base64.getDecoder().decode(codec.encode(offerData)), StandardCharsets.UTF_8);

        assertThat(json).isEqualTo("{\"segments\":{\"K1\":\"SEG1\"}}");
    }

    @Test
    void ignoresUnknownFields() {
        // also offer data of the earlier format, which had version and stage
        OfferData offerData = codec.decode(base64("{\"version\":1,\"stage\":\"SHOP\",\"offer\":{\"id\":\"O1\",\"owner\":\"AI\"},\"future\":{\"x\":1}}"));

        assertThat(offerData.offer()).isEqualTo(new OfferRef("O1", "AI"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "not base64!", "bnVsbA==", "W10=", "eyJ2ZXJzaW9uIjox", "bm90IGpzb24="})
    void rejectsInvalidData(String data) {
        // bnVsbA== null | W10= [] | truncated JSON | "not json"
        assertThatThrownBy(() -> codec.decode(data))
                .isInstanceOfSatisfying(PkfareAdapterException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo("VALIDATION_FAILURE");
                    assertThat(e.getMessage()).isEqualTo("Invalid routing.data");
                    assertThat(e.getReason()).isEqualTo("VALIDATION_FAILURE");
                });
    }

    private static String base64(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
