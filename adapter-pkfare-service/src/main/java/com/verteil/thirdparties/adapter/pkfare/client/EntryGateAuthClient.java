package com.verteil.thirdparties.adapter.pkfare.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verteil.thirdparties.adapter.pkfare.client.model.AccessTokenResponse;
import com.verteil.thirdparties.adapter.pkfare.client.model.EntryGateCredentials;
import com.verteil.thirdparties.adapter.pkfare.client.model.HttpCallResult;
import com.verteil.thirdparties.adapter.pkfare.common.exception.EntryGateHttpException;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import com.verteil.thirdparties.adapter.pkfare.config.VerteilProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VERTEIL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.BASIC_AUTH_HEADER;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_AUTH_REJECTED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.GRANT_TYPE_CLIENT_CREDENTIALS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.OAUTH_GRANT_TYPE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.OAUTH_SCOPE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AUTH;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.OAUTH_INVALID_CLIENT;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAVAILABLE_REASON_CODE;
import static org.apache.commons.lang3.StringUtils.contains;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Obtains an Entry-Gate bearer token with the OAuth2 client-credentials grant:
 * {@code POST {host}{auth-path}} (e.g. {@code https://qaapi.sandbox.verteil.com/oauth2/token}) with HTTP Basic
 * {@code username:password} (client id / client secret, from AWS Secrets Manager) and the form body
 * {@code grant_type=client_credentials&scope={auth-scope}}. Only when the token endpoint reports invalid client
 * credentials is the AWS secret re-read; if it was rotated, the call is retried once with the new credentials.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntryGateAuthClient {

    private final ThirdPartyConnector thirdPartyConnector;
    private final VerteilProperties verteilProperties;
    private final EntryGateCredentialsProvider credentialsProvider;
    private final ObjectMapper pkfareObjectMapper = JsonMappers.pkfare();

    public AccessTokenResponse login() {
        return readTokenResponse(callAuthServer());
    }

    private HttpPost buildTokenRequest(EntryGateCredentials credentials) {
        HttpPost post = new HttpPost(verteilProperties.getHost() + verteilProperties.getAuthPath());
        String basic = Base64.getEncoder().encodeToString((credentials.username() + ":" + credentials.password())
                .getBytes(StandardCharsets.UTF_8));
        post.addHeader(AUTHORIZATION, BASIC_AUTH_HEADER + basic);
        post.addHeader(ACCEPT, APPLICATION_JSON_VALUE);
        post.setEntity(new UrlEncodedFormEntity(List.of(
                new BasicNameValuePair(OAUTH_GRANT_TYPE, GRANT_TYPE_CLIENT_CREDENTIALS),
                new BasicNameValuePair(OAUTH_SCOPE, verteilProperties.getAuthScope())
        ), StandardCharsets.UTF_8));
        post.setConfig(thirdPartyConnector.buildRequestConfig(verteilProperties.getTimeoutMillis()));
        return post;
    }

    /** Invalid credentials: re-read the secret and retry once if it was rotated. Other failures are not retried here. */
    private HttpCallResult callAuthServer() {
        try {
            return requestToken();
        } catch (PkfareAdapterException e) {
            if (!isInvalidCredentials(e) || !credentialsProvider.refresh()) {
                throw asCredentialsRejected(e);
            }
            log.warn("Entry-Gate rejected the client credentials, retrying with the rotated AWS secret");
            try {
                return requestToken();
            } catch (PkfareAdapterException retryFailure) {
                throw asCredentialsRejected(retryFailure);
            }
        }
    }

    private HttpCallResult requestToken() {
        return thirdPartyConnector.callSupplier(buildTokenRequest(credentialsProvider.get()), PKFARE, SERVICE_AUTH);
    }

    /** RFC 6749 §5.2: wrong client id/secret is HTTP 401, or HTTP 400 with {@code "error":"invalid_client"}. */
    private static boolean isInvalidCredentials(PkfareAdapterException e) {
        return e instanceof EntryGateHttpException http && (http.getStatusCode() == HttpStatus.UNAUTHORIZED.value()
                || (http.getStatusCode() == HttpStatus.BAD_REQUEST.value() && contains(http.getBody(), OAUTH_INVALID_CLIENT)));
    }

    /**
     * Rejected credentials are re-thrown with a different reason so {@link EntryGateClient} does not mistake them for
     * an expired bearer token and retry.
     */
    private PkfareAdapterException asCredentialsRejected(PkfareAdapterException e) {
        if (!isInvalidCredentials(e)) {
            return e;
        }
        log.error("Entry-Gate auth server rejected the client credentials of user [{}]", credentialsProvider.get().username());
        return new PkfareAdapterException(VERTEIL_ERROR.getCode(), ERROR_MSG_AUTH_REJECTED, e, UNAVAILABLE_REASON_CODE);
    }

    private AccessTokenResponse readTokenResponse(HttpCallResult httpCallResult) {
        try {
            AccessTokenResponse response = pkfareObjectMapper.readValue(httpCallResult.body(), AccessTokenResponse.class);
            if (response == null || isBlank(response.accessToken())) {
                throw new PkfareAdapterException(VERTEIL_ERROR.getCode(), "Verteil token response has no access_token", UNAVAILABLE_REASON_CODE);
            }
            return response;
        } catch (JsonProcessingException e) {
            throw new PkfareAdapterException(VERTEIL_ERROR.getCode(), "Unreadable Verteil token response", e, UNAVAILABLE_REASON_CODE);
        }
    }
}
