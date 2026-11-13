package com.verteil.thirdparties.adapter.pkfare.acceptance.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

import java.nio.file.Path;

/**
 * Per-scenario QTE state: what PKFARE sent (with the decoded {@code routing.data} offer data), what Entry-Gate received,
 * what PKFARE got back.
 */
@Data
public class QteDataContainer {
    private Path outputDir;
    private ObjectNode qteRequest;
    private JsonNode offerData;
    private int httpStatus;
    private JsonNode qteResponse;
}
