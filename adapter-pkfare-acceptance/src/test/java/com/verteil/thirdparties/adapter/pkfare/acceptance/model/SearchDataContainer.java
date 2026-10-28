package com.verteil.thirdparties.adapter.pkfare.acceptance.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

import java.nio.file.Path;

/** Per-scenario state: what PKFARE sent, what Entry-Gate received, what PKFARE got back. */
@Data
public class SearchDataContainer {
    private Path outputDir;
    private ObjectNode searchRequest;
    private int httpStatus;
    private JsonNode searchResponse;
}
