package com.verteil.thirdparties.adapter.pkfare.acceptance.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.APPLICATION_JSON;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.CONTENT_TYPE;

/**
 * WireMock admin API: the request journal is how the suite sees what the adapter sent to Entry-Gate.
 * See https://wiremock.org/docs/standalone/admin-api-reference/.
 */
@AllArgsConstructor
public class WireMockAdminClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String wiremockUrl;

    public void resetRequestJournal() {
        send(HttpRequest.newBuilder().uri(URI.create(wiremockUrl + "/__admin/requests")).DELETE().build());
    }

    /** Requests received for {@code method urlPath}, oldest first; each has {@code headers} and the raw {@code body}. */
    public List<JsonNode> findRequests(String method, String urlPath) {
        try {
            String criteria = objectMapper.writeValueAsString(Map.of("method", method, "urlPath", urlPath));
            HttpResponse<String> response = send(HttpRequest.newBuilder()
                    .uri(URI.create(wiremockUrl + "/__admin/requests/find"))
                    .header(CONTENT_TYPE, APPLICATION_JSON)
                    .POST(HttpRequest.BodyPublishers.ofString(criteria))
                    .build());
            return objectMapper.readTree(response.body()).path("requests").valueStream().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("WireMock admin call " + request.uri() + " failed with HTTP " + response.statusCode());
            }
            return response;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
