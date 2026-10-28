package com.verteil.thirdparties.adapter.pkfare.acceptance.client;

import lombok.AllArgsConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.APPLICATION_JSON;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.CONTENT_TYPE;

/** Calls the adapter the way PKFARE does: JSON in, JSON out, always HTTP 200. */
@AllArgsConstructor
public class PkfareClient {

    private final HttpClient httpClient;
    private final String adapterUrl;

    public HttpResponse<String> post(String path, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(adapterUrl + path))
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
