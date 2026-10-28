package com.verteil.thirdparties.adapter.pkfare.acceptance.offer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verteil.thirdparties.adapter.pkfare.acceptance.client.PkfareClient;
import com.verteil.thirdparties.adapter.pkfare.acceptance.client.WireMockAdminClient;
import com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceOutput;
import com.verteil.thirdparties.adapter.pkfare.acceptance.common.JsonShape;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.ENTRY_GATE_REQUEST_PATH;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.ENTRY_GATE_AIRSHOPPING_RS;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.PKFARE_SEARCH_RQ_FILE;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.PKFARE_SEARCH_RS_FILE;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.SEARCH_PATH;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.SHAPE_REPORT_FILE;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.VDC_AIRSHOPPING_RQ_FILE;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.VDC_AIRSHOPPING_RQ_HEADERS_FILE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * PKFARE SEARCH -> adapter -> Entry-Gate AirShopping (WireMock) -> adapter -> PKFARE. The request Entry-Gate
 * received is read back from WireMock's request journal.
 */
@AllArgsConstructor
public class SearchDefinitions {

    private static final String POST = "POST";

    private final ObjectMapper objectMapper;
    private final PkfareClient pkfareClient;
    private final WireMockAdminClient wireMockAdminClient;
    private final AcceptanceOutput acceptanceOutput;
    private final SearchDataContainer searchDataContainer;

    @Before
    public void startScenario(Scenario scenario) {
        wireMockAdminClient.resetRequestJournal();
        searchDataContainer.setOutputDir(acceptanceOutput.scenarioDir(scenario.getName()));
    }

    @Given("PKFARE search request {string}")
    public void loadSearchRequest(String resource) {
        searchDataContainer.setSearchRequest((ObjectNode) readResource(resource));
    }

    @Given("the search request field {string} is {string}")
    public void changeSearchRequest(String field, String value) {
        searchDataContainer.getSearchRequest().put(field, value);
    }

    @When("PKFARE calls SEARCH")
    public void callSearch() throws JsonProcessingException {
        var outputDir = searchDataContainer.getOutputDir();
        acceptanceOutput.writeJson(outputDir, PKFARE_SEARCH_RQ_FILE, searchDataContainer.getSearchRequest());

        var response = pkfareClient.post(SEARCH_PATH, objectMapper.writeValueAsString(searchDataContainer.getSearchRequest()));
        searchDataContainer.setHttpStatus(response.statusCode());
        searchDataContainer.setSearchResponse(objectMapper.readTree(response.body()));

        // Written before any assertion runs, so the files are there to inspect even when a scenario fails.
        lastAirShoppingRequest().ifPresent(request -> {
            acceptanceOutput.writeJson(outputDir, VDC_AIRSHOPPING_RQ_FILE, body(request));
            acceptanceOutput.writeJson(outputDir, VDC_AIRSHOPPING_RQ_HEADERS_FILE, request.path("headers"));
        });
        acceptanceOutput.writeJson(outputDir, PKFARE_SEARCH_RS_FILE, searchDataContainer.getSearchResponse());
    }

    @Then("PKFARE receives status {int} with message {string}")
    public void assertStatus(int status, String message) {
        assertThat(searchDataContainer.getHttpStatus()).as("HTTP status (PKFARE always gets 200)").isEqualTo(200);
        assertThat(searchDataContainer.getSearchResponse().path("status").asInt(-1)).as("status").isEqualTo(status);
        assertThat(searchDataContainer.getSearchResponse().path("msg").asText()).as("msg").isEqualTo(message);
    }

    @Then("Entry-Gate receives no AirShoppingRQ")
    public void assertNoAirShoppingRequest() {
        assertThat(wireMockAdminClient.findRequests(POST, ENTRY_GATE_REQUEST_PATH)).isEmpty();
    }

    @Then("Entry-Gate receives one AirShoppingRQ with header Service {string} and no ThirdpartyId")
    public void assertAirShoppingHeaders(String service) {
        List<JsonNode> requests = wireMockAdminClient.findRequests(POST, ENTRY_GATE_REQUEST_PATH);
        assertThat(requests).hasSize(1);
        JsonNode headers = requests.getFirst().path("headers");
        assertThat(header(headers, "Service")).isEqualTo(service);
        assertThat(header(headers, "ThirdpartyId")).isNull();
        assertThat(header(headers, "Authorization")).isEqualTo("Bearer acceptance-token");
        assertThat(header(headers, "VDC_TransactionId")).isNotBlank();
    }

    /**
     * PKFARE sends no child ages, so the sample's {@code Age} is not expected. Travelers may come in any order, but
     * the SortOrder sequence is a priority, so it is checked in order on its own.
     */
    @Then("the AirShoppingRQ matches the sample {string} apart from child ages")
    public void assertAirShoppingRequest(String resource) throws JSONException {
        JsonNode sample = readResource(resource);
        sample.path("Travelers").path("Traveler").valueStream()
                .flatMap(traveler -> traveler.path("AnonymousTraveler").valueStream())
                .forEach(anonymous -> ((ObjectNode) anonymous).remove("Age"));
        JsonNode actual = body(lastAirShoppingRequest().orElseThrow(() -> new AssertionError("Entry-Gate received no AirShoppingRQ")));

        JSONAssert.assertEquals(sample.toString(), actual.toString(), JSONCompareMode.NON_EXTENSIBLE);
        assertThat(sortParameters(actual)).as("ResponseParameters.SortOrder priority").isEqualTo(sortParameters(sample));
    }

    @Then("PKFARE receives a shopping result for each of the VDC offers")
    public void assertEveryOfferMapped() {
        assertStatus(0, "success");
        int vdcOffers = readResource(ENTRY_GATE_AIRSHOPPING_RS).path("OffersGroup").path("AirlineOffers").valueStream()
                .mapToInt(airlineOffers -> airlineOffers.path("AirlineOffer").size())
                .sum();
        assertThat(vdcOffers).as("offers in the stubbed AirShoppingRS").isPositive();
        assertThat(searchDataContainer.getSearchResponse().path("shoppingResultList")).as("shoppingResultList").hasSize(vdcOffers);
    }

    @Then("every flightRefNum in the shopping results is a flight in flightList")
    public void assertFlightReferencesResolve() {
        JsonNode response = searchDataContainer.getSearchResponse();
        Set<Integer> flights = response.path("flightList").valueStream()
                .map(flight -> flight.path("flightRefNum").asInt())
                .collect(Collectors.toCollection(TreeSet::new));
        Set<Integer> referenced = response.path("shoppingResultList").valueStream()
                .flatMap(result -> result.path("flightRefList").valueStream())
                .map(reference -> reference.path("flightRefNum").asInt())
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(referenced).isNotEmpty();
        // flightList holds exactly the referenced flights: none missing, none unused.
        assertThat(flights).isEqualTo(referenced);
    }

    @Then("the PKFARE response has every field of the sample {string}")
    public void assertResponseShape(String resource) {
        JsonShape.Diff diff = JsonShape.compare(readResource(resource), searchDataContainer.getSearchResponse());
        acceptanceOutput.writeText(searchDataContainer.getOutputDir(), SHAPE_REPORT_FILE, diff.report());
        assertThat(diff.matches()).as(diff.report()).isTrue();
    }

    private Optional<JsonNode> lastAirShoppingRequest() {
        List<JsonNode> requests = wireMockAdminClient.findRequests(POST, ENTRY_GATE_REQUEST_PATH);
        return requests.isEmpty() ? Optional.empty() : Optional.of(requests.getLast());
    }

    private JsonNode body(JsonNode loggedRequest) {
        try {
            return objectMapper.readTree(loggedRequest.path("body").asText());
        } catch (JsonProcessingException e) {
            throw new AssertionError("Entry-Gate request body is not JSON", e);
        }
    }

    /** WireMock logs a header as a string, or as an array when it was sent more than once. */
    private static String header(JsonNode headers, String name) {
        return headers.propertyStream()
                .filter(field -> field.getKey().equalsIgnoreCase(name))
                .findFirst()
                .map(field -> field.getValue().isArray() ? field.getValue().path(0).asText() : field.getValue().asText())
                .orElse(null);
    }

    private static List<String> sortParameters(JsonNode airShoppingRQ) {
        return airShoppingRQ.path("ResponseParameters").path("SortOrder").valueStream()
                .map(sort -> sort.path("Parameter").asText())
                .toList();
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
