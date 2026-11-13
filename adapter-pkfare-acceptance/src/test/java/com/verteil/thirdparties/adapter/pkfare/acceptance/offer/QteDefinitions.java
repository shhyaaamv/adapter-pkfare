package com.verteil.thirdparties.adapter.pkfare.acceptance.offer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verteil.thirdparties.adapter.pkfare.acceptance.client.PkfareClient;
import com.verteil.thirdparties.adapter.pkfare.acceptance.client.WireMockAdminClient;
import com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceOutput;
import com.verteil.thirdparties.adapter.pkfare.acceptance.common.JsonShape;
import com.verteil.thirdparties.adapter.pkfare.acceptance.model.QteDataContainer;
import com.verteil.thirdparties.adapter.pkfare.acceptance.model.SearchDataContainer;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.AllArgsConstructor;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.verteil.thirdparties.adapter.pkfare.acceptance.client.WireMockAdminClient.header;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.APPLICATION_JSON;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.CONTENT_TYPE;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.ENTRY_GATE_FLIGHTPRICE_BODY_FILE;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.ENTRY_GATE_FLIGHTPRICE_RS;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.ENTRY_GATE_REQUEST_PATH;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.FLIGHT_PRICE_SERVICE;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.PKFARE_QTE_RQ_FILE;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.PKFARE_QTE_RQ_DATA_FILE;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.PKFARE_QTE_RS_FILE;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.PKFARE_QTE_RS_DATA_FILE;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.QTE_PATH;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.QTE_SHAPE_REPORT_FILE;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.SERVICE_HEADER;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.VDC_FLIGHTPRICE_RQ_FILE;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.VDC_FLIGHTPRICE_RQ_HEADERS_FILE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * PKFARE QTE -> adapter -> Entry-Gate FlightPrice (WireMock) -> adapter -> PKFARE. The FlightPriceRQ Entry-Gate
 * received is read back from WireMock's request journal; scenario-specific FlightPrice answers are added as
 * priority stubs, which are reset before every scenario.
 */
@AllArgsConstructor
public class QteDefinitions {

    private static final String POST = "POST";
    private static final int STUB_PRIORITY = 1;
    private static final List<String> SEGMENT_FIELDS = List.of("marketingCarrier", "flightNumber", "depAirport", "depTime",
            "arrAirport", "arrTime", "codeShare", "aircraftCode", "operatingCarrier");

    private final ObjectMapper objectMapper;
    private final PkfareClient pkfareClient;
    private final WireMockAdminClient wireMockAdminClient;
    private final AcceptanceOutput acceptanceOutput;
    private final QteDataContainer qteDataContainer;
    private final SearchDataContainer searchDataContainer;

    @Before
    public void startScenario(Scenario scenario) {
        wireMockAdminClient.resetStubs();
        qteDataContainer.setOutputDir(acceptanceOutput.scenarioDir(scenario.getName()));
    }

    /** The offer data goes into {@code routing.data} the way the adapter mints it: standard Base64 of the UTF-8 JSON. */
    @Given("PKFARE QTE request {string} with routing.data from {string}")
    public void loadQteRequest(String requestResource, String offerDataResource) {
        ObjectNode request = (ObjectNode) readResource(requestResource);
        JsonNode offerData = readResource(offerDataResource);
        ((ObjectNode) request.path("routing")).put("data", encode(offerData));
        qteDataContainer.setQteRequest(request);
        qteDataContainer.setOfferData(offerData);
    }

    /**
     * PKFARE prices a shopping result by echoing its {@code data} with the result's flights: the segments come from
     * {@code flightList} in {@code flightRefList} order, cabin and RBD from the flight reference.
     */
    @Given("a PKFARE QTE request for the first shopping result")
    public void buildQteRequestFromSearch() {
        JsonNode searchRequest = searchDataContainer.getSearchRequest();
        JsonNode searchResponse = searchDataContainer.getSearchResponse();
        JsonNode result = searchResponse.path("shoppingResultList").path(0);
        assertThat(result.isObject()).as("SEARCH returned a shopping result").isTrue();

        ObjectNode request = objectMapper.createObjectNode();
        Stream.of("adultNumber", "childNumber", "infantNumber", "cutOffTime", "tripType", "vendorCode")
                .forEach(field -> request.set(field, searchRequest.path(field)));
        request.put("referenceId", "274446433125889918");
        ObjectNode routing = request.putObject("routing");
        routing.put("data", result.path("data").asText());
        routing.set("fromSegments", segments(result, flightsByRefNum(searchResponse), 1));
        routing.set("retSegments", segments(result, flightsByRefNum(searchResponse), 2));
        routing.set("tag", result.path("tag"));

        qteDataContainer.setQteRequest(request);
        qteDataContainer.setOfferData(decode(result.path("data").asText()));
    }

    @Given("the QTE request field {string} is {string}")
    public void changeQteRequest(String field, String value) {
        qteDataContainer.getQteRequest().put(field, value);
    }

    @Given("the QTE request field {string} is {int}")
    public void changeQteRequest(String field, int value) {
        qteDataContainer.getQteRequest().put(field, value);
    }

    @Given("the QTE routing.data is {string}")
    public void changeRoutingData(String data) {
        ((ObjectNode) qteDataContainer.getQteRequest().path("routing")).put("data", data);
    }

    @Given("the first two QTE fromSegments are swapped")
    public void swapFromSegments() {
        ArrayNode fromSegments = (ArrayNode) qteDataContainer.getQteRequest().path("routing").path("fromSegments");
        JsonNode first = fromSegments.get(0);
        fromSegments.set(0, fromSegments.get(1));
        fromSegments.set(1, first);
    }

    @Given("Entry-Gate answers FlightPrice with {string}")
    public void stubFlightPriceBodyFile(String bodyFileName) {
        addFlightPriceStub(response -> response.put("bodyFileName", bodyFileName));
    }

    @Given("Entry-Gate answers FlightPrice after {int} ms")
    public void stubDelayedFlightPrice(int delayMillis) {
        addFlightPriceStub(response -> response.put("bodyFileName", ENTRY_GATE_FLIGHTPRICE_BODY_FILE)
                .put("fixedDelayMilliseconds", delayMillis));
    }

    /** The VDC sample carries no SeatsLeft: the stub is the sample with SeatsLeft on every priced segment. */
    @Given("Entry-Gate answers FlightPrice with {int} seats left")
    public void stubSeatsLeft(int seatsLeft) {
        JsonNode flightPriceRS = readResource(ENTRY_GATE_FLIGHTPRICE_RS);
        List<ObjectNode> codes = pricedClassOfServiceCodes(flightPriceRS);
        assertThat(codes).as("ClassOfService.Code on the priced offer's segments").isNotEmpty();
        codes.forEach(code -> code.put("SeatsLeft", seatsLeft));
        addFlightPriceStub(response -> response.set("jsonBody", flightPriceRS));
    }

    @When("PKFARE calls QTE")
    public void callQte() throws JsonProcessingException {
        var outputDir = qteDataContainer.getOutputDir();
        acceptanceOutput.writeJson(outputDir, PKFARE_QTE_RQ_FILE, qteDataContainer.getQteRequest());
        Optional.ofNullable(qteDataContainer.getOfferData())
                .ifPresent(offerData -> acceptanceOutput.writeJson(outputDir, PKFARE_QTE_RQ_DATA_FILE, offerData));

        var response = pkfareClient.post(QTE_PATH, objectMapper.writeValueAsString(qteDataContainer.getQteRequest()));
        qteDataContainer.setHttpStatus(response.statusCode());
        qteDataContainer.setQteResponse(objectMapper.readTree(response.body()));

        // Written before any assertion runs, so the files are there to inspect even when a scenario fails.
        lastFlightPriceRequest().ifPresent(request -> {
            acceptanceOutput.writeJson(outputDir, VDC_FLIGHTPRICE_RQ_FILE, body(request));
            acceptanceOutput.writeJson(outputDir, VDC_FLIGHTPRICE_RQ_HEADERS_FILE, request.path("headers"));
        });
        acceptanceOutput.writeJson(outputDir, PKFARE_QTE_RS_FILE, qteDataContainer.getQteResponse());
        Optional.of(qteDataContainer.getQteResponse().path("routing").path("data").asText())
                .filter(data -> !data.isEmpty())
                .ifPresent(data -> acceptanceOutput.writeJson(outputDir, PKFARE_QTE_RS_DATA_FILE, decode(data)));
    }

    @Then("PKFARE receives QTE status {int} with message {string}")
    public void assertStatus(int status, String message) {
        assertThat(qteDataContainer.getHttpStatus()).as("HTTP status (PKFARE always gets 200)").isEqualTo(200);
        JsonNode response = qteDataContainer.getQteResponse();
        assertThat(response.path("status").asInt(-1)).as("status").isEqualTo(status);
        assertThat(response.path("msg").asText()).as("msg").isEqualTo(message);
    }

    @Then("the QTE response has maxSeats {int} and no routing")
    public void assertSeatsOnly(int maxSeats) {
        JsonNode response = qteDataContainer.getQteResponse();
        assertThat(response.path("maxSeats").asInt(-1)).as("maxSeats").isEqualTo(maxSeats);
        assertThat(response.path("sessionId").asText()).as("sessionId").isNotBlank();
        assertThat(response.path("routing").isMissingNode() || response.path("routing").isNull()).as("routing absent").isTrue();
    }

    @Then("the QTE response has errorCode {string}")
    public void assertErrorCode(String errorCode) {
        JsonNode response = qteDataContainer.getQteResponse();
        assertThat(response.path("errorCode").asText()).as("errorCode").isEqualTo(errorCode);
        assertThat(response.path("errorMsg").asText()).as("errorMsg").isNotBlank();
    }

    @Then("Entry-Gate receives no FlightPriceRQ")
    public void assertNoFlightPriceRequest() {
        assertThat(flightPriceRequests()).isEmpty();
    }

    /** {@code thirdpartyid} carries the airline code: the FlightPriceRQ offer owner. */
    @Then("Entry-Gate receives one FlightPriceRQ with header Service {string} and thirdpartyid the offer owner")
    public void assertFlightPriceHeaders(String service) {
        List<JsonNode> requests = flightPriceRequests();
        assertThat(requests).hasSize(1);
        JsonNode headers = requests.getFirst().path("headers");
        assertThat(header(headers, SERVICE_HEADER)).isEqualTo(service);
        String offerOwner = body(requests.getFirst()).path("Query").path("Offers").path("Offer").path(0)
                .path("OfferID").path("Owner").asText();
        assertThat(offerOwner).as("FlightPriceRQ OfferID owner").isNotBlank();
        assertThat(header(headers, "thirdpartyid")).as("thirdpartyid header").isEqualTo(offerOwner);
        assertThat(header(headers, "Authorization")).isEqualTo("Bearer acceptance-token");
        assertThat(header(headers, "VDC_TransactionId")).isNotBlank();
    }

    /** Travelers, offer items and data-list entries may come in any order; nothing may be added or left out. */
    @Then("the FlightPriceRQ matches the sample {string}")
    public void assertFlightPriceRequest(String resource) throws JSONException {
        JSONAssert.assertEquals(readResource(resource).toString(), lastFlightPriceRequestBody().toString(),
                JSONCompareMode.NON_EXTENSIBLE);
    }

    /** The offer data SEARCH minted, not a hand-made one, drives the FlightPriceRQ. */
    @Then("the FlightPriceRQ carries the offer, ShoppingResponseID and segment keys of the routing.data")
    public void assertFlightPriceRequestFromOfferData() {
        JsonNode offerData = qteDataContainer.getOfferData();
        JsonNode flightPriceRQ = lastFlightPriceRequestBody();
        JsonNode offer = flightPriceRQ.path("Query").path("Offers").path("Offer").path(0);

        assertThat(offer.path("OfferID").path("value").asText()).as("OfferID")
                .isEqualTo(offerData.path("offer").path("id").asText());
        assertThat(offer.path("OfferID").path("Owner").asText()).as("OfferID owner")
                .isEqualTo(offerData.path("offer").path("owner").asText());
        assertThat(offer.path("OfferID").path("Channel").asText()).as("OfferID channel").isEqualTo("DIRECT_CONNECT");
        assertThat(texts(offer.path("OfferItemIDs").path("OfferItemID"), item -> item.path("value")))
                .as("OfferItemIDs")
                .containsExactlyInAnyOrderElementsOf(texts(offerData.path("passengers"), passenger -> passenger.path("offerItemId")));
        assertThat(flightPriceRQ.path("ShoppingResponseID").path("ResponseID").path("value").asText()).as("ShoppingResponseID")
                .isEqualTo(offerData.path("shoppingResponseId").path("value").asText());
        assertThat(flightPriceRQ.path("ShoppingResponseID").path("Owner").asText()).as("ShoppingResponseID owner")
                .isEqualTo(offerData.path("shoppingResponseId").path("owner").asText());
        // offer data segments: match key -> "segmentKey|fareBasis", in itinerary order
        assertThat(segmentKeys(flightPriceRQ)).as("Flight SegmentKeys")
                .isNotEmpty()
                .containsExactlyElementsOf(offerData.path("segments").valueStream()
                        .map(segment -> segment.asText().split("\\|")[0])
                        .toList());
        assertThat(flightPriceRQ.has("Metadata")).as("FlightPriceRQ Metadata").isFalse();
    }

    @Then("the QTE response has {int} fromSegments, {int} retSegments and {int} prices")
    public void assertRoutingSizes(int fromSegments, int retSegments, int prices) {
        JsonNode routing = qteDataContainer.getQteResponse().path("routing");
        assertThat(routing.path("fromSegments")).as("fromSegments").hasSize(fromSegments);
        assertThat(routing.path("retSegments")).as("retSegments").hasSize(retSegments);
        assertThat(routing.path("priceList")).as("priceList").hasSize(prices);
    }

    @Then("the QTE response has every field of the sample {string}")
    public void assertResponseShape(String resource) {
        JsonShape.Diff diff = JsonShape.compare(readResource(resource), qteDataContainer.getQteResponse());
        acceptanceOutput.writeText(qteDataContainer.getOutputDir(), QTE_SHAPE_REPORT_FILE, diff.report());
        assertThat(diff.matches()).as(diff.report()).isTrue();
    }

    /** QTE answers with new offer data for Order, built from the priced offer, not an echo of the request's. */
    @Then("the QTE routing.data is offer data for the priced offer")
    public void assertPricedOfferData() {
        JsonNode offerData = decode(qteDataContainer.getQteResponse().path("routing").path("data").asText());
        JsonNode flightPriceRS = readResource(ENTRY_GATE_FLIGHTPRICE_RS);
        JsonNode pricedOffer = flightPriceRS.path("PricedFlightOffers").path("PricedFlightOffer").path(0);

        assertThat(offerData.path("offer").path("id").asText()).as("offer data OfferID")
                .isEqualTo(pricedOffer.path("OfferID").path("value").asText());
        assertThat(offerData.path("shoppingResponseId").path("value").asText()).as("offer data ShoppingResponseID")
                .isEqualTo(flightPriceRS.path("ShoppingResponseID").path("ResponseID").path("value").asText());
        assertThat(offerData.path("segments")).as("offer data segments").hasSize(4);
    }

    private void addFlightPriceStub(Function<ObjectNode, ObjectNode> response) {
        ObjectNode stub = objectMapper.createObjectNode().put("priority", STUB_PRIORITY);
        ObjectNode request = stub.putObject("request").put("urlPath", ENTRY_GATE_REQUEST_PATH).put("method", POST);
        request.putObject("headers").putObject(SERVICE_HEADER).put("equalTo", FLIGHT_PRICE_SERVICE);
        ObjectNode stubResponse = stub.putObject("response").put("status", 200);
        stubResponse.putObject("headers").put(CONTENT_TYPE, APPLICATION_JSON);
        response.apply(stubResponse);
        wireMockAdminClient.addStub(stub);
    }

    private static List<ObjectNode> pricedClassOfServiceCodes(JsonNode flightPriceRS) {
        return flightPriceRS.path("PricedFlightOffers").path("PricedFlightOffer").valueStream()
                .flatMap(offer -> offer.path("OfferPrice").valueStream())
                .flatMap(offerPrice -> offerPrice.path("RequestedDate").path("Associations").valueStream())
                .flatMap(association -> association.path("ApplicableFlight").path("FlightSegmentReference").valueStream())
                .map(reference -> reference.path("ClassOfService").path("Code"))
                .filter(JsonNode::isObject)
                .map(ObjectNode.class::cast)
                .toList();
    }

    private static Map<Integer, JsonNode> flightsByRefNum(JsonNode searchResponse) {
        return searchResponse.path("flightList").valueStream()
                .collect(Collectors.toMap(flight -> flight.path("flightRefNum").asInt(), Function.identity()));
    }

    private ArrayNode segments(JsonNode result, Map<Integer, JsonNode> flights, int travelDirection) {
        ArrayNode segments = objectMapper.createArrayNode();
        result.path("flightRefList").valueStream()
                .filter(reference -> reference.path("travelDirection").asInt() == travelDirection)
                .sorted(Comparator.comparingInt(reference -> reference.path("flightSeq").asInt()))
                .map(reference -> segment(reference, flights.get(reference.path("flightRefNum").asInt())))
                .forEach(segments::add);
        return segments;
    }

    private ObjectNode segment(JsonNode reference, JsonNode flight) {
        ObjectNode segment = objectMapper.createObjectNode();
        SEGMENT_FIELDS.stream()
                .filter(flight::has)
                .forEach(field -> segment.set(field, flight.path(field)));
        segment.set("cabinClass", reference.path("cabinClass"));
        segment.set("rbd", reference.path("rbd"));
        return segment;
    }

    private static List<String> segmentKeys(JsonNode flightPriceRQ) {
        return flightPriceRQ.path("Query").path("OriginDestination").valueStream()
                .flatMap(originDestination -> originDestination.path("Flight").valueStream())
                .map(flight -> flight.path("SegmentKey").asText())
                .toList();
    }

    private static List<String> texts(JsonNode array, Function<JsonNode, JsonNode> field) {
        return array.valueStream().map(field).map(JsonNode::asText).toList();
    }

    private List<JsonNode> flightPriceRequests() {
        return wireMockAdminClient.findRequests(POST, ENTRY_GATE_REQUEST_PATH, SERVICE_HEADER, FLIGHT_PRICE_SERVICE);
    }

    private Optional<JsonNode> lastFlightPriceRequest() {
        List<JsonNode> requests = flightPriceRequests();
        return requests.isEmpty() ? Optional.empty() : Optional.of(requests.getLast());
    }

    private JsonNode lastFlightPriceRequestBody() {
        return body(lastFlightPriceRequest().orElseThrow(() -> new AssertionError("Entry-Gate received no FlightPriceRQ")));
    }

    /** {@link JsonNode#toString()} is compact JSON, unlike the suite's indenting mapper. */
    private static String encode(JsonNode offerData) {
        return Base64.getEncoder().encodeToString(offerData.toString().getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode decode(String data) {
        try {
            return objectMapper.readTree(new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new AssertionError("routing.data is not Base64 JSON offer data: " + data, e);
        }
    }

    private JsonNode body(JsonNode loggedRequest) {
        try {
            return objectMapper.readTree(loggedRequest.path("body").asText());
        } catch (JsonProcessingException e) {
            throw new AssertionError("Entry-Gate request body is not JSON", e);
        }
    }

    private JsonNode readResource(String resource) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("No classpath resource " + resource);
            }
            return objectMapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
