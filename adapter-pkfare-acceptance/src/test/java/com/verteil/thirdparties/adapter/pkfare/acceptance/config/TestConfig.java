package com.verteil.thirdparties.adapter.pkfare.acceptance.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.verteil.thirdparties.adapter.pkfare.acceptance.client.PkfareClient;
import com.verteil.thirdparties.adapter.pkfare.acceptance.client.WireMockAdminClient;
import com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceOutput;
import com.verteil.thirdparties.adapter.pkfare.acceptance.model.SearchDataContainer;
import io.cucumber.spring.ScenarioScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.ADAPTER_URL;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.OUTPUT_DIR;
import static com.verteil.thirdparties.adapter.pkfare.acceptance.common.AcceptanceConstants.WIREMOCK_URL;

@Configuration
public class TestConfig {

    /** Plain Jackson: the suite compares JSON trees, so no NDC/PKFARE model classes are involved. */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder().build();
    }

    @Bean
    public PkfareClient pkfareClient(HttpClient httpClient) {
        return new PkfareClient(httpClient, ADAPTER_URL);
    }

    @Bean
    public WireMockAdminClient wireMockAdminClient(ObjectMapper objectMapper, HttpClient httpClient) {
        return new WireMockAdminClient(objectMapper, httpClient, WIREMOCK_URL);
    }

    @Bean
    public AcceptanceOutput acceptanceOutput(ObjectMapper objectMapper) {
        return new AcceptanceOutput(objectMapper, OUTPUT_DIR);
    }

    @Bean
    @ScenarioScope
    public SearchDataContainer searchDataContainer() {
        return new SearchDataContainer();
    }
}
