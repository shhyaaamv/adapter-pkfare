package com.verteil.thirdparties.adapter.pkfare.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verteil.thirdparties.adapter.pkfare.client.model.AccessTokenResponse;
import com.verteil.thirdparties.adapter.pkfare.client.model.HttpCallResult;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.Configuration;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.ENTRY_GATE_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.AUTH_PATH;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.AUTH_SCOPE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.BASIC_AUTH_HEADER;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_AUTH_SCOPE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_TIMEOUT_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_AUTH_REJECTED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.GRANT_TYPE_CLIENT_CREDENTIALS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.HOST;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.OAUTH_GRANT_TYPE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.OAUTH_SCOPE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PASSWORD;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AUTH;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.TIMEOUT_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAUTHORIZED_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAVAILABLE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.USERNAME;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Obtains an Entry-Gate bearer token with the OAuth2 client-credentials grant:
 * {@code POST {HOST}{AUTH_PATH}} (e.g. {@code https://qaapi.sandbox.verteil.com/oauth2/token}) with HTTP Basic {@code USERNAME:PASSWORD}
 * (client id / client secret) and the form body {@code grant_type=client_credentials&scope={AUTH_SCOPE}}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntryGateAuthClient {

    private final ThirdPartyConnector thirdPartyConnector;
    private final ObjectMapper pkfareObjectMapper = JsonMappers.pkfare();

    public AccessTokenResponse login(Configuration configuration) {
        String username = configuration.getString(USERNAME);
        String password = configuration.getString(PASSWORD);
        if (isBlank(username) || isBlank(password)) {
            throw new PkfareAdapterException(ENTRY_GATE_ERROR.getCode(), "Entry-Gate credentials are not configured", UNAUTHORIZED_REASON_CODE);
        }
        HttpPost post = buildTokenRequest(configuration, username, password);
        return readTokenResponse(callAuthServer(post, configuration));
    }

    private HttpPost buildTokenRequest(Configuration configuration, String username, String password) {
        HttpPost post = new HttpPost(configuration.getString(HOST) + configuration.getString(AUTH_PATH));
        String basic = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        post.addHeader(AUTHORIZATION, BASIC_AUTH_HEADER + basic);
        post.addHeader(ACCEPT, APPLICATION_JSON_VALUE);
        post.setEntity(new UrlEncodedFormEntity(List.of(
                new BasicNameValuePair(OAUTH_GRANT_TYPE, GRANT_TYPE_CLIENT_CREDENTIALS),
                new BasicNameValuePair(OAUTH_SCOPE, configuration.getString(AUTH_SCOPE, DEFAULT_AUTH_SCOPE))
        ), StandardCharsets.UTF_8));
        post.setConfig(thirdPartyConnector.buildRequestConfig(configuration.getInteger(TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS)));
        return post;
    }

    /**
     * A 401 from the token endpoint means the client credentials are wrong. It is re-thrown with a different
     * reason so {@link EntryGateClient} does not mistake it for an expired bearer token and retry.
     */
    private HttpCallResult callAuthServer(HttpPost post, Configuration configuration) {
        try {
            return thirdPartyConnector.callSupplier(post, configuration, PKFARE, SERVICE_AUTH);
        } catch (PkfareAdapterException e) {
            if (!UNAUTHORIZED_REASON_CODE.equals(e.getReason())) {
                throw e;
            }
            log.error("Entry-Gate auth server rejected the client credentials of user [{}]", configuration.getString(USERNAME));
            throw new PkfareAdapterException(ENTRY_GATE_ERROR.getCode(), ERROR_MSG_AUTH_REJECTED, e, UNAVAILABLE_REASON_CODE);
        }
    }

    private AccessTokenResponse readTokenResponse(HttpCallResult httpCallResult) {
        try {
            AccessTokenResponse response = pkfareObjectMapper.readValue(httpCallResult.body(), AccessTokenResponse.class);
            if (response == null || isBlank(response.accessToken())) {
                throw new PkfareAdapterException(ENTRY_GATE_ERROR.getCode(), "Entry-Gate token response has no access_token", UNAUTHORIZED_REASON_CODE);
            }
            return response;
        } catch (JsonProcessingException e) {
            throw new PkfareAdapterException(ENTRY_GATE_ERROR.getCode(), "Unreadable Entry-Gate token response", e, UNAUTHORIZED_REASON_CODE);
        }
    }
}
