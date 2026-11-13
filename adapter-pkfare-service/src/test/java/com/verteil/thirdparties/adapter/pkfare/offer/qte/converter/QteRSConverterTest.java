package com.verteil.thirdparties.adapter.pkfare.offer.qte.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.verteil.schema.pkfare.QteChangeInfo;
import com.verteil.schema.pkfare.QteFreeBaggage;
import com.verteil.schema.pkfare.QteRefundInfo;
import com.verteil.schema.pkfare.QteRequest;
import com.verteil.schema.pkfare.QteResponse;
import com.verteil.schema.pkfare.QteRouting;
import com.verteil.schema.pkfare.QteSegment;
import com.verteil.schemas.ndc16_1.FlightCOSCoreType;
import com.verteil.schemas.ndc16_1.FlightPriceRS;
import com.verteil.schemas.ndc16_1.ShoppingResponseIDType;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.FareRuleConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.FreeBaggageConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.ItineraryConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.OfferFareConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.SegmentDetailsConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.converter.TicketPriceConverter;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData.DataPassenger;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferDataCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VERTEIL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_FLIGHTPRICE;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.PKFARE_MAPPER;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildQteContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.flightPriceRS;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.qteRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Maps the trimmed Entry-Gate FlightPriceRS (Air India COK-BOM-DXB / DXB-BOM-COK, 2 ADT + 1 CHD + 1 INF) against
 * the matching PKFARE QTE request. The sample carries no SeatsLeft and no ShoppingResponseID owner; seat variants
 * set SeatsLeft in the test.
 */
class QteRSConverterTest {

    private static final String SRID = "LSbPSOlari--6t34oGYyTa7POI7WaD6ZKvWU2OOtC08-AI";
    private static final String PRICED_OFFER_ID = "1H2AIZ_EUVLQCMJX4BMUYSHXCVEX6PIMLMN";

    private final OfferDataCodec codec = new OfferDataCodec();
    private QteRSConverter converter;

    @BeforeEach
    void setUp() {
        converter = new QteRSConverter(new ItineraryConverter(), new TicketPriceConverter(), new FreeBaggageConverter(),
                new FareRuleConverter(), new OfferFareConverter(), new SegmentDetailsConverter(), new QteSegmentConverter(),
                new QteOfferDataFactory(), codec);
    }

    @Test
    void mapsStatusSessionAndSeats() {
        QteResponse response = convert(qteRequest(), flightPriceRS());

        assertThat(response.getStatus()).isZero();
        assertThat(response.getMsg()).isEqualTo("success");
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getErrorMsg()).isNull();
        assertThat(response.getSessionId()).isEqualTo(SRID);
        assertThat(response.getMaxSeats()).isEqualTo(3);   // no SeatsLeft: 2 ADT + 1 CHD
    }

    @Test
    void mapsOutboundAndReturnSegments() {
        QteRouting routing = convert(qteRequest(), flightPriceRS()).getRouting();

        assertThat(routing.getFromSegments())
                .extracting(QteSegment::getDepAirport, QteSegment::getDepTime, QteSegment::getArrAirport, QteSegment::getArrTime,
                        QteSegment::getMarketingCarrier, QteSegment::getFlightNumber, QteSegment::getTravelDirection, QteSegment::getFlightSeq)
                .containsExactly(
                        tuple("COK", "202611180750", "BOM", "202611181015", "AI", 9896, 1, "1"),
                        tuple("BOM", "202611181630", "DXB", "202611181835", "AI", 911, 1, "2"));
        assertThat(routing.getRetSegments())
                .extracting(QteSegment::getDepAirport, QteSegment::getArrAirport, QteSegment::getFlightNumber,
                        QteSegment::getTravelDirection, QteSegment::getFlightSeq)
                .containsExactly(tuple("DXB", "BOM", 984, 2, "1"), tuple("BOM", "COK", 2855, 2, "2"));
        assertThat(allSegments(routing)).extracting(QteSegment::getRbd).containsExactly("W", "G", "G", "G");
        assertThat(allSegments(routing)).extracting(QteSegment::getCabinClass).containsOnly("Y");
        assertThat(allSegments(routing)).extracting(QteSegment::getFlightRefNum).containsOnlyNulls();
    }

    @Test
    void mapsSegmentDetailsCodeShareAndOperatingFlightNumberFallback() {
        QteSegment first = convert(qteRequest(), flightPriceRS()).getRouting().getFromSegments().getFirst();

        assertThat(first.getDuration()).isEqualTo(145);
        assertThat(first.getAircraftCode()).isEqualTo("320");
        assertThat(first.getDepTerminal()).isEqualTo("1");
        assertThat(first.getArrTerminal()).isEqualTo("2");
        assertThat(first.getCodeShare()).isEqualTo("true");
        assertThat(first.getOperatingCarrier()).isEqualTo("IX");
        // the sample's OperatingCarrier has no FlightNumber: the marketing one is sent
        assertThat(first.getOperatingFlightNumber()).isEqualTo(9896);
        assertThat(first.getStopAirport()).isNull();

        QteSegment second = convert(qteRequest(), flightPriceRS()).getRouting().getFromSegments().get(1);
        assertThat(second.getCodeShare()).isEqualTo("false");
        assertThat(second.getOperatingCarrier()).isEqualTo("AI");
        assertThat(second.getOperatingFlightNumber()).isEqualTo(911);
    }

    @Test
    void mapsPerPassengerPricesAndFareAttributes() {
        QteRouting routing = convert(qteRequest(), flightPriceRS()).getRouting();

        assertThat(routing.getPriceList())
                .extracting("passengerType", "fare", "tax", "minFare")
                .containsExactly(
                        tuple(0, new BigDecimal("22025.00"), new BigDecimal("20145.00"), null),
                        tuple(1, new BigDecimal("16220.00"), new BigDecimal("19854.00"), null),
                        tuple(2, new BigDecimal("2165.00"), new BigDecimal("802.00"), null));
        assertThat(routing.getFareBasis()).isEqualTo("GL2YXSI3;GL2YXSI3;GP2YXSI3;GP2YXSI3");
        assertThat(routing.getProductType()).isEqualTo("PRV");
        assertThat(routing.getCurrency()).isEqualTo("INR");
        assertThat(routing.getValidatingCarrier()).isEqualTo("AI");
        assertThat(routing.getTag()).isEqualTo("Value");
        assertThat(routing.getEligibility()).isEmpty();
        assertThat(routing.getOtherFareRules()).extracting("passengerType").containsExactly(0, 1, 2);
        assertThat(routing.getOtherFareRules().getFirst().getDetail())
                .contains("Checked in baggage allowance - Allowed upto 25 kgs", "Seat selection - Chargeable");
    }

    @Test
    void mapsBaggagePerPassengerTypeAndSegment() {
        List<QteFreeBaggage> baggage = convert(qteRequest(), flightPriceRS()).getRouting().getFreeBaggageList();

        assertThat(baggage).hasSize(12);   // 3 passenger types x 4 segments
        assertThat(baggage).filteredOn(bag -> bag.getPassengerType() == 0)
                .extracting(QteFreeBaggage::getTravelDirection, QteFreeBaggage::getFlightSeq, QteFreeBaggage::getBaggagePiece,
                        QteFreeBaggage::getBaggageWeight, QteFreeBaggage::getCarryOnPiece, QteFreeBaggage::getCarryOnWeight)
                .containsExactly(
                        tuple(1, 1, -1, 25, 1, 7),
                        tuple(1, 2, -1, 25, 1, 7),
                        tuple(2, 1, -1, 25, 1, 7),
                        tuple(2, 2, -1, 25, 1, 7));
    }

    @Test
    void mapsRefundAndChangeRulesForRoundTrip() {
        QteRouting routing = convert(qteRequest(), flightPriceRS()).getRouting();

        assertThat(routing.getRefundInfoList())
                .extracting(QteRefundInfo::getPassengerType, QteRefundInfo::getRefundType)
                .containsExactly(tuple(0, 0), tuple(0, 1), tuple(1, 0), tuple(1, 1), tuple(2, 0), tuple(2, 1));
        assertThat(routing.getChangeInfoList())
                .extracting(QteChangeInfo::getPassengerType, QteChangeInfo::getChangeType)
                .containsExactly(tuple(0, 0), tuple(0, 1), tuple(1, 0), tuple(1, 1), tuple(2, 0), tuple(2, 1));
        assertThat(routing.getRefundInfoList()).allSatisfy(info -> assertThat(info.getConditionList()).singleElement()
                .satisfies(condition -> {
                    assertThat(condition.getStart()).isEqualTo("-1");
                    assertThat(condition.getEnd()).isEqualTo("-1");
                    assertThat(condition.getUnit()).isEqualTo("h");
                    assertThat(condition.getCurrency()).isEqualTo("INR");
                    assertThat(condition.getRefundable()).isNotNull();
                }));
    }

    @Test
    void mintsOfferDataFromThePricedOffer() {
        OfferData offerData = codec.decode(convert(qteRequest(), flightPriceRS()).getRouting().getData());

        // the sample's ShoppingResponseID has no Owner: the priced OfferID owner is used
        assertThat(offerData.shoppingResponseId()).isEqualTo(new OfferData.ShoppingResponseRef("AI", SRID));
        assertThat(offerData.offer()).isEqualTo(new OfferData.OfferRef(PRICED_OFFER_ID, "AI"));
        assertThat(offerData.passengers()).extracting(DataPassenger::ptc).containsExactly("ADT", "CHD", "INF");
        assertThat(offerData.passengers()).allSatisfy(passenger -> {
            assertThat(passenger.offerItemId()).startsWith(PRICED_OFFER_ID);
            assertThat(passenger.travelerRefs()).isNotEmpty();
        });
        // the priced segment keys, in the priced itinerary order
        assertThat(offerData.segments()).containsExactly(
                entry("COK|202611180750|BOM|202611181015|AI|9896", "SEG1|GL2YXSI3"),
                entry("BOM|202611181630|DXB|202611181835|AI|911", "SEG2|GL2YXSI3"),
                entry("DXB|202611292340|BOM|202611300430|AI|984", "SEG7|GP2YXSI3"),
                entry("BOM|202611301000|COK|202611301205|AI|2855", "SEG8|GP2YXSI3"));
    }

    @Test
    void fallsBackToTheShopDataSessionIdWithoutShoppingResponseId() {
        FlightPriceRS rs = flightPriceRS();
        rs.setShoppingResponseID(null);

        QteResponse response = convert(qteRequest(), rs);

        assertThat(response.getSessionId()).isEqualTo(SRID);
        assertThat(codec.decode(response.getRouting().getData()).shoppingResponseId())
                .isEqualTo(new OfferData.ShoppingResponseRef("AI", SRID));
    }

    @Test
    void usesTheShoppingResponseIdOwnerWhenGiven() {
        FlightPriceRS rs = flightPriceRS();
        ShoppingResponseIDType shoppingResponseId = rs.getShoppingResponseID();
        shoppingResponseId.setOwner("XX");

        OfferData offerData = codec.decode(convert(qteRequest(), rs).getRouting().getData());

        assertThat(offerData.shoppingResponseId()).isEqualTo(new OfferData.ShoppingResponseRef("XX", SRID));
    }

    @Test
    void takesTheLowestSeatsLeftCappedAtNine() {
        assertThat(convert(qteRequest(), withSeatsLeft(12, 5)).getMaxSeats()).isEqualTo(5);
        assertThat(convert(qteRequest(), withSeatsLeft(12, 15)).getMaxSeats()).isEqualTo(9);
    }

    @Test
    void reportsInsufficientSeatsAsStatusOne() {
        QteResponse response = convert(qteRequest(), withSeatsLeft(9, 2));

        assertThat(response.getStatus()).isEqualTo(1);
        assertThat(response.getMsg()).isEqualTo("Insufficient remaining seats");
        assertThat(response.getMaxSeats()).isEqualTo(2);
        assertThat(response.getSessionId()).isEqualTo(SRID);
        assertThat(response.getRouting()).isNull();
    }

    @Test
    void reportsSoldOutAsStatusTwo() {
        QteResponse response = convert(qteRequest(), withSeatsLeft(9, 0));

        assertThat(response.getStatus()).isEqualTo(2);
        assertThat(response.getMsg()).isEqualTo("All seats have been sold");
        assertThat(response.getMaxSeats()).isZero();
        assertThat(response.getSessionId()).isEqualTo(SRID);
        assertThat(response.getRouting()).isNull();
    }

    @Test
    void rejectsAPricedItineraryThatDiffersFromTheRequest() {
        QteRequest request = qteRequest();
        request.getRouting().getRetSegments().getLast().setFlightNumber(2856);

        assertInvalidFare(() -> convert(request, flightPriceRS()));
    }

    @Test
    void rejectsARoundTripPriceForAOneWayRequest() {
        QteRequest request = qteRequest();
        request.getRouting().getRetSegments().clear();

        assertInvalidFare(() -> convert(request, flightPriceRS()));
    }

    @Test
    void rejectsMissingPassengerTypes() {
        QteRequest request = qteRequest();
        request.setInfantNumber(0);

        assertInvalidFare(() -> convert(request, flightPriceRS()));
    }

    @Test
    void rejectsMissingOrNonPositiveAmounts() {
        assertInvalidFare(() -> convert(qteRequest(), editFirstPriceDetail(priceDetail -> priceDetail.setBaseAmount(null))));
        assertInvalidFare(() -> convert(qteRequest(), editFirstPriceDetail(priceDetail -> priceDetail.setTotalAmount(null))));
        assertInvalidFare(() -> convert(qteRequest(), editFirstPriceDetail(priceDetail ->
                priceDetail.getTotalAmount().getSimpleCurrencyPrice().setValue(BigDecimal.ZERO))));
    }

    @Test
    void fallsBackToTheRequestTagWithoutPriceClasses() {
        FlightPriceRS rs = flightPriceRS();
        rs.getDataLists().setPriceClassList(null);
        QteRequest request = qteRequest();
        request.getRouting().setTag("Flex");

        QteRouting routing = convert(request, rs).getRouting();

        assertThat(routing.getTag()).isEqualTo("Flex");
        assertThat(routing.getOtherFareRules()).allSatisfy(rule -> assertThat(rule.getDetail()).isEmpty());
    }

    @Test
    void mapsTheVdcCabinBackToThePkfareCabin() {
        FlightPriceRS rs = flightPriceRS();
        rs.getPricedFlightOffers().getPricedFlightOffer().getFirst().getOfferPrice().getFirst().getRequestedDate()
                .getAssociations().getFirst().getApplicableFlight().getFlightSegmentReference().getFirst()
                .getClassOfService().getMarketingName().setCabinDesignator("W");

        QteRouting routing = convert(qteRequest(), rs).getRouting();

        assertThat(routing.getFromSegments().getFirst().getCabinClass()).isEqualTo("S");
    }

    @Test
    void serialisesRequiredNullsAndEmptyLists() {
        QteRequest request = qteRequest();
        QteResponse response = convert(request, flightPriceRS());
        response.getRouting().setRetSegments(List.of());

        JsonNode json = PKFARE_MAPPER.valueToTree(response);

        assertThat(json.has("errorCode")).isTrue();
        assertThat(json.get("errorCode").isNull()).isTrue();
        assertThat(json.has("errorMsg")).isTrue();
        assertThat(json.path("routing").path("retSegments").isArray()).isTrue();
        assertThat(json.path("routing").path("retSegments")).isEmpty();
        assertThat(json.path("routing").path("eligibility").asText("missing")).isEmpty();
    }

    private QteResponse convert(QteRequest request, FlightPriceRS rs) {
        PkfareAdapterContext context = buildQteContext(request);
        context.setProviderApiResponse(SERVICE_FLIGHTPRICE, rs);
        context.setOfferData(codec.decode(qteRequest().getRouting().getData()));
        return converter.convert(context);
    }

    private static void assertInvalidFare(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(PkfareAdapterException.class)
                .hasMessage("Invalid fare returned")
                .satisfies(e -> {
                    assertThat(((PkfareAdapterException) e).getErrorCode()).isEqualTo(VERTEIL_ERROR.getCode());
                    assertThat(((PkfareAdapterException) e).getReason()).isEqualTo("INVALID_FARE");
                });
    }

    private static List<QteSegment> allSegments(QteRouting routing) {
        return java.util.stream.Stream.concat(routing.getFromSegments().stream(), routing.getRetSegments().stream()).toList();
    }

    /** SeatsLeft {@code defaultSeats} on every segment reference, {@code lowest} on the first one only. */
    private static FlightPriceRS withSeatsLeft(int defaultSeats, int lowest) {
        FlightPriceRS rs = flightPriceRS();
        List<FlightCOSCoreType.Code> codes = rs.getPricedFlightOffers().getPricedFlightOffer().getFirst().getOfferPrice().stream()
                .flatMap(offerPrice -> offerPrice.getRequestedDate().getAssociations().stream())
                .flatMap(association -> association.getApplicableFlight().getFlightSegmentReference().stream())
                .map(reference -> reference.getClassOfService().getCode())
                .toList();
        codes.forEach(code -> code.setSeatsLeft(defaultSeats));
        codes.getFirst().setSeatsLeft(lowest);
        return rs;
    }

    private static FlightPriceRS editFirstPriceDetail(Consumer<com.verteil.schemas.ndc16_1.OfferPriceLeadDetailType.PriceDetail> edit) {
        FlightPriceRS rs = flightPriceRS();
        edit.accept(rs.getPricedFlightOffers().getPricedFlightOffer().getFirst().getOfferPrice().getFirst()
                .getRequestedDate().getPriceDetail());
        return rs;
    }
}
