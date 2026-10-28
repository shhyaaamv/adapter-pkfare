package com.verteil.thirdparties.adapter.pkfare.client;

import com.verteil.thirdparties.adapter.pkfare.client.model.AccessTokenResponse;
import com.verteil.thirdparties.adapter.pkfare.client.model.HttpCallResult;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntryGateAuthClientTest {

    private static final String TOKEN_RS = """
            {"access_token":"abc123","scope":"api","token_type":"Bearer","expires_in":43199}""";

    private final ThirdPartyConnector connector = mock(ThirdPartyConnector.class);
    private final EntryGateAuthClient client = new EntryGateAuthClient(connector);

    @Test
    void requestsTokenWithBasicAuthAndFormBody() throws Exception {
        when(connector.callSupplier(any(), any(), eq("PKFARE"), eq("Auth"))).thenReturn(new HttpCallResult(200, TOKEN_RS, 10));

        AccessTokenResponse response = client.login(configuration(Map.of()));

        assertThat(response.accessToken()).isEqualTo("abc123");
        assertThat(response.expiresIn()).isEqualTo(43199L);
        HttpPost post = capturedRequest();
        assertThat(post.getUri()).hasToString("https://api.example.com/oauth2/token");
        assertThat(post.getFirstHeader("Authorization").getValue())
                .isEqualTo("Basic " + Base64.getEncoder().encodeToString("client:secret".getBytes(StandardCharsets.UTF_8)));
        assertThat(post.getEntity().getContentType()).startsWith("application/x-www-form-urlencoded");
        assertThat(EntityUtils.toString(post.getEntity())).isEqualTo("grant_type=client_credentials&scope=api");
    }

    @Test
    void usesConfiguredScope() throws Exception {
        when(connector.callSupplier(any(), any(), any(), any())).thenReturn(new HttpCallResult(200, TOKEN_RS, 10));

        client.login(configuration(Map.of("AUTH_SCOPE", "api read")));

        HttpPost post = capturedRequest();
        assertThat(EntityUtils.toString(post.getEntity())).isEqualTo("grant_type=client_credentials&scope=api+read");
    }

    @Test
    void rejectedCredentialsAreNotReportedAsAnExpiredToken() {
        when(connector.callSupplier(any(), any(), any(), any()))
                .thenThrow(new PkfareAdapterException("3", "HTTP 401 from Entry-Gate", "UNAUTHORIZED"));

        assertThatThrownBy(() -> client.login(configuration(Map.of())))
                .isInstanceOf(PkfareAdapterException.class)
                .hasMessage("Entry-Gate rejected the client credentials")
                .extracting("reason").isEqualTo("UNAVAILABLE");
    }

    @Test
    void failsWithoutCallingAuthServerWhenCredentialsAreMissing() {
        Map<String, Object> values = new HashMap<>(baseValues());
        values.remove("PASSWORD");

        assertThatThrownBy(() -> client.login(new MapConfiguration(values)))
                .hasMessage("Entry-Gate credentials are not configured");
        verify(connector, never()).callSupplier(any(), any(), any(), any());
    }

    @Test
    void failsWhenTokenResponseHasNoAccessToken() {
        when(connector.callSupplier(any(), any(), any(), any())).thenReturn(new HttpCallResult(200, "{\"token_type\":\"Bearer\"}", 10));

        assertThatThrownBy(() -> client.login(configuration(Map.of())))
                .hasMessage("Entry-Gate token response has no access_token");
    }

    private HttpPost capturedRequest() {
        ArgumentCaptor<HttpPost> captor = ArgumentCaptor.forClass(HttpPost.class);
        verify(connector).callSupplier(captor.capture(), any(), any(), any());
        verify(connector).buildRequestConfig(anyLong());
        return captor.getValue();
    }

    private static Configuration configuration(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>(baseValues());
        values.putAll(overrides);
        return new MapConfiguration(values);
    }

    private static Map<String, Object> baseValues() {
        return Map.of(
                "HOST", "https://api.example.com",
                "AUTH_PATH", "/oauth2/token",
                "USERNAME", "client",
                "PASSWORD", "secret");
    }
}
