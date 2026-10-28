package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.verteil.schema.pkfare.SearchFlight;
import com.verteil.schema.pkfare.SearchFreeBaggage;
import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schema.pkfare.SearchResponse;
import com.verteil.schema.pkfare.SearchShoppingResult;
import com.verteil.schema.pkfare.SearchTicket;
import com.verteil.schemas.ndc16_1.AirShoppingRS;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.OfferData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AIRSHOPPING;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.PKFARE_MAPPER;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.airShoppingRS;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.searchRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Maps a real Entry-Gate AirShoppingRS (Air India COK-DXB round trip, 2 ADT + 1 CHD + 1 INF, trimmed to 3 offers).
 * Expected values were worked out from the fixture independently of the converter.
 */
class SearchRSConverterTest {

    private SearchRSConverter converter;
    private OfferDataConverter offerDataConverter;

    @BeforeEach
    void setUp() {
        offerDataConverter = new OfferDataConverter();
        ShoppingResultConverter shoppingResultConverter = new ShoppingResultConverter(new ItineraryConverter(),
                new TicketPriceConverter(), new FreeBaggageConverter(), new FareRuleConverter(), offerDataConverter);
        converter = new SearchRSConverter(shoppingResultConverter, new FlightConverter());
    }

    @Test
    void mapsEveryOfferAndOnlyReferencedFlights() {
        SearchResponse response = convert(searchRequest(), Map.of());

        assertThat(response.getStatus()).isZero();
        assertThat(response.getMsg()).isEqualTo("success");
        assertThat(response.getShoppingResultList()).hasSize(3);
        // SEG9, SEG6, SEG18, SEG19 (offer 1) + SEG4 (offer 2) + SEG20 (offer 3)
        assertThat(response.getFlightList()).extracting(SearchFlight::getFlightRefNum).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(response.getFlightList()).extracting(SearchFlight::getFlightNumber).containsExactly(2744, 2217, 984, 2787, 2201, 2202);
    }

    @Test
    void ordersSegmentsByDirectionAndSequenceWithCabinAndRbd() {
        SearchShoppingResult result = convert(searchRequest(), Map.of()).getShoppingResultList().getFirst();

        assertThat(result.getFlightRefList())
                .extracting("flightRefNum", "travelDirection", "flightSeq", "ticketSeq", "cabinClass", "rbd", "seatCount")
                .containsExactly(
                        tuple(1, 1, 1, 1, "Y", "Y", 3),
                        tuple(2, 1, 2, 1, "S", "E", 3),   // VDC cabin W -> PKFARE S
                        tuple(3, 2, 1, 1, "C", "Z", 3),
                        tuple(4, 2, 2, 1, "C", "Z", 3));
        assertThat(result.getTag()).isEqualTo("Value");
        assertThat(result.getThreshold()).isZero();
    }

    @Test
    void mapsPerPassengerPricesAndTicketAttributes() {
        SearchTicket ticket = convert(searchRequest(), Map.of()).getShoppingResultList().getFirst().getTicketList().getFirst();

        assertThat(ticket.getPriceList())
                .extracting("passengerType", "fare", "maxFare", "tax")
                .containsExactly(
                        tuple(0, new BigDecimal("57130.00"), new BigDecimal("57130.00"), new BigDecimal("31373.00")),
                        tuple(1, new BigDecimal("45215.00"), new BigDecimal("45215.00"), new BigDecimal("29228.00")),
                        tuple(2, new BigDecimal("14235.00"), new BigDecimal("14235.00"), new BigDecimal("3342.00")));
        assertThat(ticket.getTicketSeq()).isEqualTo(1);
        assertThat(ticket.getValidatingCarrier()).isEqualTo("AI");
        assertThat(ticket.getProductType()).isEqualTo("PRV");
        assertThat(ticket.getCurrency()).isEqualTo("INR");
        assertThat(ticket.getTicketIssueTime()).isEqualTo(120);
        assertThat(ticket.getFareBasis()).isEqualTo("ELVPW2I3;ELVPW2I3;ZP2CWSI3;ZP2CWSI3");
        assertThat(ticket.getTag()).isEqualTo("Value");
        assertThat(ticket.getOtherFareRules()).extracting("passengerType").containsExactly(0, 1, 2);
        assertThat(ticket.getOtherFareRules().getFirst().getDetail()).contains("Seat selection - Free");
    }

    @Test
    void mapsBaggagePerPassengerTypeAndSegment() {
        SearchTicket ticket = convert(searchRequest(), Map.of()).getShoppingResultList().getFirst().getTicketList().getFirst();

        assertThat(ticket.getFreeBaggageList()).hasSize(12);   // 3 passenger types x 4 segments
        assertThat(ticket.getFreeBaggageList())
                .filteredOn(baggage -> baggage.getPassengerType() == 0)
                .extracting(SearchFreeBaggage::getTravelDirection, SearchFreeBaggage::getFlightSeq, SearchFreeBaggage::getBaggagePiece,
                        SearchFreeBaggage::getBaggageWeight, SearchFreeBaggage::getCarryOnPiece, SearchFreeBaggage::getCarryOnWeight)
                .containsExactly(
                        tuple(1, 1, -1, 25, 1, 7),
                        tuple(1, 2, -1, 25, 1, 7),
                        tuple(2, 1, -1, 40, 1, 10),
                        tuple(2, 2, -1, 40, 1, 10));
        // the infant OfferPrice carries no bag association: reported as no baggage
        assertThat(ticket.getFreeBaggageList())
                .filteredOn(baggage -> baggage.getPassengerType() == 2)
                .extracting(SearchFreeBaggage::getBaggagePiece, SearchFreeBaggage::getBaggageWeight, SearchFreeBaggage::getCarryOnPiece)
                .containsOnly(tuple(0, -1, 0));
    }

    @Test
    void mapsRefundAndChangeRulesForRoundTrip() {
        SearchTicket ticket = convert(searchRequest(), Map.of()).getShoppingResultList().getFirst().getTicketList().getFirst();

        // entirely unused: outbound 8000 + return 12000; partially used: return 12000
        assertThat(ticket.getRefundInfoList())
                .filteredOn(info -> info.getPassengerType() == 0)
                .extracting(info -> info.getRefundType(), info -> info.getConditionList().getFirst().getRefundable(),
                        info -> info.getConditionList().getFirst().getRefundFee(), info -> info.getConditionList().getFirst().getCurrency())
                .containsExactly(tuple(0, true, 20000, "INR"), tuple(1, true, 12000, "INR"));
        assertThat(ticket.getChangeInfoList())
                .filteredOn(info -> info.getPassengerType() == 0)
                .extracting(info -> info.getChangeType(), info -> info.getConditionList().getFirst().getChangeable(),
                        info -> info.getConditionList().getFirst().getChangeFee())
                .containsExactly(tuple(0, true, 8000), tuple(1, true, 4000));
        assertThat(ticket.getRefundInfoList().getFirst().getConditionList().getFirst())
                .extracting("start", "end", "unit").containsExactly("-1", "-1", "h");
        assertThat(ticket.getRefundInfoList()).hasSize(6);   // 3 passenger types x 2 refund types
    }

    @Test
    void mapsFlightDetails() {
        SearchFlight flight = convert(searchRequest(), Map.of()).getFlightList().getFirst();

        assertThat(flight.getMarketingCarrier()).isEqualTo("AI");
        assertThat(flight.getFlightNumber()).isEqualTo(2744);
        assertThat(flight.getDepAirport()).isEqualTo("COK");
        assertThat(flight.getArrAirport()).isEqualTo("BOM");
        assertThat(flight.getDepTime()).isEqualTo("202610200850");
        assertThat(flight.getArrTime()).isEqualTo("202610201130");
        assertThat(flight.getDepTerminal()).isEqualTo("1");
        assertThat(flight.getCodeShare()).isFalse();
        assertThat(flight.getOperatingCarrier()).isEqualTo("AI");
        assertThat(flight.getOperatingFlightNumber()).isNull();
        assertThat(flight.getAircraftCode()).isEqualTo("32N");
        assertThat(flight.getDuration()).isEqualTo(160);
        assertThat(flight.getStops()).isEmpty();
    }

    @Test
    void encodesOfferDataForPricing() {
        SearchShoppingResult result = convert(searchRequest(), Map.of()).getShoppingResultList().getFirst();

        OfferData data = offerDataConverter.decode(result.getData());
        assertThat(data.offerId()).isEqualTo("1H2AIZ_0KZ5PZIOJZNJNY5NWKKZS0AOOEQG");
        assertThat(data.owner()).isEqualTo("AI");
        assertThat(data.offerItemIds()).hasSize(3).allMatch(id -> id.startsWith("1H2AIZ_0KZ5PZIOJZNJNY5NWKKZS0AOOEQG-1-"));
        assertThat(data.segmentKeys()).containsExactly("AI-SEG9", "AI-SEG6", "AI-SEG18", "AI-SEG19");
    }

    @Test
    void omitsTagsWhenNotRequestedAndHonoursConfiguredDefaults() {
        SearchRequest request = searchRequest();
        request.setReturnTagPrice("N");

        SearchResponse response = convert(request, Map.of("DEFAULT_SEAT_COUNT", "12", "TICKET_ISSUE_TIME", "30"));

        SearchShoppingResult result = response.getShoppingResultList().getFirst();
        assertThat(result.getTag()).isEmpty();
        assertThat(result.getTicketList().getFirst().getTag()).isEmpty();
        assertThat(result.getTicketList().getFirst().getTicketIssueTime()).isEqualTo(30);
        assertThat(result.getFlightRefList()).extracting("seatCount").containsOnly(9);   // capped at 9
    }

    @Test
    void filtersOffersByRequestedProductTag() {
        SearchRequest request = searchRequest();
        request.setTag("FLEX");

        assertThat(convert(request, Map.of()).getShoppingResultList()).isEmpty();

        request.setTag("value");
        assertThat(convert(request, Map.of()).getShoppingResultList()).hasSize(3);
    }

    @Test
    void skipsOffersThatDoNotMatchTheRequestedPassengers() {
        SearchRequest request = searchRequest();
        request.setInfantNumber(0);   // response prices an infant that was not asked for

        SearchResponse response = convert(request, Map.of());

        assertThat(response.getStatus()).isZero();
        assertThat(response.getShoppingResultList()).isEmpty();
        assertThat(response.getFlightList()).isEmpty();
    }

    @Test
    void serializesRequiredEmptyListsForPkfare() throws Exception {
        SearchRequest request = searchRequest();
        request.setTag("FLEX");

        String json = PKFARE_MAPPER.writeValueAsString(convert(request, Map.of()));

        assertThat(json).isEqualTo("{\"status\":0,\"msg\":\"success\",\"shoppingResultList\":[],\"flightList\":[]}");
    }

    private SearchResponse convert(SearchRequest request, Map<String, String> config) {
        AirShoppingRS airShoppingRS = airShoppingRS();
        PkfareAdapterContext context = buildContext(request, config);
        context.setProviderApiResponse(SERVICE_AIRSHOPPING, airShoppingRS);
        return converter.convert(context);
    }
}
