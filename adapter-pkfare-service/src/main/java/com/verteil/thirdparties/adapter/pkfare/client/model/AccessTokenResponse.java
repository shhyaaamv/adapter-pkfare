package com.verteil.thirdparties.adapter.pkfare.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth2 client-credentials token response of the Verteil auth server fronting Entry-Gate.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccessTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("scope") String scope
) {
}
