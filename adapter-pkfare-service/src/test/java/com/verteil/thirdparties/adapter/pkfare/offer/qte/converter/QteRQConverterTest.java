package com.verteil.thirdparties.adapter.pkfare.offer.qte.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.verteil.schema.pkfare.QteRequest;
import com.verteil.schema.pkfare.QteSegment;
import com.verteil.schemas.ndc16_1.FlightPriceRQ;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferDataCodec;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.INVALID_DATE_RANGE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.MSG_INVALID_DATE_RANGE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VALIDATION_FAILURE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VDC_CHANNEL;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.NDC_MAPPER;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildQteContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.qteRequest;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.readString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QteRQConverterTest {

    private final OfferDataCodec codec = new OfferDataCodec();
    private final QteRQConverter converter = new QteRQConverter();

    @Test
    void mapsTheSampleRequestToTheExpectedFlightPriceRQ() throws Exception {
        String json = NDC_MAPPER.writeValueAsString(convert(qteRequest()));

        // NON_EXTENSIBLE: no extra fields, array order lenient (travelers)
        JSONAssert.assertEquals(readString("qte/VDC_FlightPriceRQ_expected.json"), json, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void sendsDateTimesAndTheConstantChannelWithoutOfferRefsOrMetadata() {
        JsonNode rq = NDC_MAPPER.valueToTree(convert(qteRequest()));

        JsonNode flight = rq.at("/Query/OriginDestination/0/Flight/0");
        assertThat(flight.at("/Departure/Date").asText()).isEqualTo("2026-11-18T07:50:00.000");
        assertThat(flight.at("/Departure/Time").asText()).isEqualTo("07:50");
        assertThat(flight.at("/Arrival/Date").asText()).isEqualTo("2026-11-18T10:15:00.000");
        assertThat(rq.at("/Query/Offers/Offer/0/OfferID/Channel").asText()).isEqualTo(VDC_CHANNEL).isEqualTo("DIRECT_CONNECT");
        assertThat(rq.at("/Query/Offers/Offer/0/refs").isMissingNode()).isTrue();
        assertThat(rq.has("Metadata")).isFalse();
        assertThat(flight.at("/CabinType/Name").isMissingNode()).isTrue();
    }

    @Test
    void takesCarrierCabinAndRbdFromTheRequestOnly() {
        QteRequest request = qteRequest();
        QteSegment segment = request.getRouting().getFromSegments().get(1);
        segment.setOperatingCarrier(null);
        segment.setCabinClass(null);
        segment.setRbd(" G ");
        segment.setAircraftCode(" ");

        FlightPriceRQ rq = convert(request);

        var flight = rq.getQuery().getOriginDestination().get(0).getFlight().get(1);
        assertThat(flight.getSegmentKey()).isEqualTo("AI-SEG4");
        assertThat(flight.getOperatingCarrier()).isNull();
        assertThat(flight.getCabinType()).isNull();
        assertThat(flight.getClassOfService().getCode().getValue()).isEqualTo("G");
        assertThat(flight.getEquipment()).isNull();
    }

    @Test
    void mapsPremiumEconomyCabinToVdc() {
        QteRequest request = qteRequest();
        request.getRouting().getFromSegments().get(0).setCabinClass("S");

        FlightPriceRQ rq = convert(request);

        assertThat(rq.getQuery().getOriginDestination().get(0).getFlight().get(0).getCabinType().getCode()).isEqualTo("W");
    }

    @Test
    void leavesTheFareListOutWhenTheOfferDataHasNoFareBasis() {
        QteRequest request = qteRequest();
        OfferData offerData = codec.decode(request.getRouting().getData());
        Map<String, String> segmentKeysOnly = new LinkedHashMap<>();
        offerData.segments().forEach((matchKey, value) -> segmentKeysOnly.put(matchKey, value.substring(0, value.indexOf('|'))));
        PkfareAdapterContext context = buildQteContext(request);
        context.setOfferData(withSegments(offerData, segmentKeysOnly));

        FlightPriceRQ rq = converter.convert(context);

        assertThat(rq.getMetadata()).isNull();
        assertThat(rq.getDataLists().getFareList()).isNull();
        assertThat(rq.getQuery().getOffers().getOffer().get(0).getRefs()).isEmpty();
        assertThat(rq.getQuery().getOriginDestination().get(0).getFlight().get(0).getClassOfService().getRefs()).isEmpty();
    }

    @Test
    void oneWayRequestWithOneWayOfferDataHasOneOriginDestination() {
        QteRequest request = qteRequest();
        request.setTripType("1");
        request.getRouting().setRetSegments(new ArrayList<>());
        OfferData offerData = codec.decode(request.getRouting().getData());
        PkfareAdapterContext context = buildQteContext(request);
        Map<String, String> outbound = new LinkedHashMap<>();
        offerData.segments().entrySet().stream().limit(2).forEach(entry -> outbound.put(entry.getKey(), entry.getValue()));
        context.setOfferData(withSegments(offerData, outbound));

        FlightPriceRQ rq = converter.convert(context);

        assertThat(rq.getQuery().getOriginDestination()).hasSize(1);
        assertThat(rq.getDataLists().getFareList().getFareGroup()).hasSize(2);
    }

    @Test
    void rejectsAMissingRequest() {
        PkfareAdapterContext context = buildQteContext(null);

        assertValidationFailure(() -> converter.convert(context), "Empty QTE request");
    }

    @Test
    void rejectsInvalidPassengerNumbers() {
        assertRejected(request -> request.setAdultNumber(0), "adultNumber");
        assertRejected(request -> request.setAdultNumber(10), "adultNumber");
        assertRejected(request -> request.setChildNumber(10), "childNumber");
        assertRejected(request -> request.setInfantNumber(3), "infantNumber cannot exceed adultNumber");
    }

    @Test
    void rejectsAnInconsistentTrip() {
        assertRejected(request -> request.setTripType("3"), "tripType");
        assertRejected(request -> request.getRouting().setFromSegments(new ArrayList<>()), "fromSegments");
        assertRejected(request -> request.getRouting().setRetSegments(new ArrayList<>()), "retSegments is required");
        assertRejected(request -> request.setTripType("1"), "retSegments must be empty");
    }

    @Test
    void rejectsIncompleteOrMalformedSegments() {
        assertRejected(request -> firstSegment(request).setDepAirport(" "), "required");
        assertRejected(request -> firstSegment(request).setFlightNumber(null), "required");
        assertRejected(request -> firstSegment(request).setRbd(" "), "rbd are required");
        assertRejected(request -> firstSegment(request).setRbd(null), "rbd are required");
        assertRejected(request -> firstSegment(request).setDepTime("2026111807"), "depTime");
        assertRejected(request -> firstSegment(request).setArrTime("202611311015"), "arrTime");
        assertRejected(request -> firstSegment(request).setCabinClass("Z"), "cabinClass");
    }

    @Test
    void rejectsPassengersOtherThanTheShoppedOnes() {
        assertRejected(request -> request.setAdultNumber(3), "Passenger numbers do not match routing.data");
        assertRejected(request -> request.setInfantNumber(0), "Passenger numbers do not match routing.data");
    }

    @Test
    void rejectsSegmentsNotInTheOfferData() {
        assertRejected(request -> firstSegment(request).setFlightNumber(9897), "Unknown segment COK|202611180750|BOM|202611181015|AI|9897");
        assertRejected(request -> request.getRouting().getRetSegments().remove(1), "Segments do not match routing.data");
    }

    @Test
    void rejectsOfferDataWithoutSegmentsOrWithABlankSegmentKey() {
        QteRequest request = qteRequest();
        OfferData offerData = codec.decode(request.getRouting().getData());
        Map<String, String> blankKey = new LinkedHashMap<>(offerData.segments());
        blankKey.put("BOM|202611181630|DXB|202611181835|AI|911", "|GL2YXSI3");

        assertValidationFailure(() -> convert(request, withSegments(offerData, null)), "Invalid routing.data");
        assertValidationFailure(() -> convert(request, withSegments(offerData, blankKey)), "Unknown segment BOM|202611181630|DXB|202611181835|AI|911");
    }

    @Test
    void rejectsASegmentMovedToTheOtherDirectionOutOfOrderAsInvalidDateRange() {
        // fromSegments COK-BOM, BOM-DXB, BOM-COK / retSegments DXB-BOM: not the offer data's order
        assertDateRange(request -> request.getRouting().getFromSegments().add(request.getRouting().getRetSegments().remove(1)));
    }

    @Test
    void rejectsSegmentsInAnotherOrderThanTheOfferDataAsInvalidDateRange() {
        QteRequest request = qteRequest();
        OfferData offerData = codec.decode(request.getRouting().getData());
        List<Map.Entry<String, String>> entries = new ArrayList<>(offerData.segments().entrySet());
        Map<String, String> returnFirst = new LinkedHashMap<>();
        Stream.concat(entries.subList(2, 4).stream(), entries.subList(0, 2).stream())
                .forEach(entry -> returnFirst.put(entry.getKey(), entry.getValue()));

        assertThatThrownBy(() -> convert(request, withSegments(offerData, returnFirst)))
                .isInstanceOfSatisfying(PkfareAdapterException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(INVALID_DATE_RANGE_REASON_CODE));
    }

    @Test
    void rejectsADuplicateSegmentAsInvalidDateRange() {
        assertDateRange(request -> request.getRouting().getRetSegments().set(1, request.getRouting().getRetSegments().get(0)));
    }

    @Test
    void rejectsASegmentDepartingBeforeThePreviousArrivalAsInvalidDateRange() {
        // BOM-DXB moved to depart 09:00 although COK-BOM only lands at BOM 10:15
        assertDateRange(request -> request.getRouting().getFromSegments().get(1).setDepTime("202611180900"));
    }

    @Test
    void rejectsSwappedSegmentsAsInvalidDateRange() {
        assertDateRange(request -> {
            List<QteSegment> fromSegments = request.getRouting().getFromSegments();
            fromSegments.add(fromSegments.remove(0));
        });
    }

    private FlightPriceRQ convert(QteRequest request) {
        return convert(request, codec.decode(request.getRouting().getData()));
    }

    private FlightPriceRQ convert(QteRequest request, OfferData offerData) {
        PkfareAdapterContext context = buildQteContext(request);
        context.setOfferData(offerData);
        return converter.convert(context);
    }

    private void assertRejected(Consumer<QteRequest> change, String message) {
        QteRequest request = qteRequest();
        change.accept(request);
        assertValidationFailure(() -> convert(request), message);
    }

    private void assertDateRange(Consumer<QteRequest> change) {
        QteRequest request = qteRequest();
        change.accept(request);
        assertThatThrownBy(() -> convert(request))
                .isInstanceOfSatisfying(PkfareAdapterException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("VALIDATION_FAILURE");
                    assertThat(exception.getReason()).isEqualTo(INVALID_DATE_RANGE_REASON_CODE);
                    assertThat(exception.getMessage()).isEqualTo(MSG_INVALID_DATE_RANGE);
                });
    }

    private static void assertValidationFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String message) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(PkfareAdapterException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("VALIDATION_FAILURE");
                    assertThat(exception.getReason()).isEqualTo(VALIDATION_FAILURE_REASON_CODE);
                    assertThat(exception.getMessage()).contains(message);
                });
    }

    private static OfferData withSegments(OfferData offerData, Map<String, String> segments) {
        return new OfferData(offerData.shoppingResponseId(), offerData.offer(), offerData.passengers(), segments);
    }

    private static QteSegment firstSegment(QteRequest request) {
        return request.getRouting().getFromSegments().get(0);
    }
}
