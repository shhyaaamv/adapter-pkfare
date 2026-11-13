package com.verteil.thirdparties.adapter.pkfare.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Entry-Gate OAuth2 client id / client secret, read from the AWS secret {@code aws.secretManager.name}, a JSON object
 * with {@code username} and {@code password}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EntryGateCredentials(String username, String password) {

    @Override
    public String toString() {
        return "EntryGateCredentials[username=" + username + ", password=****]";
    }
}
