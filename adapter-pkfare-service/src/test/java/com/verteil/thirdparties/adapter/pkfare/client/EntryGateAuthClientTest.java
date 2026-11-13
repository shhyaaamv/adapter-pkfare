package com.verteil.thirdparties.adapter.pkfare.client;

import com.verteil.thirdparties.adapter.pkfare.client.model.AccessTokenResponse;
import com.verteil.thirdparties.adapter.pkfare.client.model.EntryGateCredentials;
import com.verteil.thirdparties.adapter.pkfare.client.model.HttpCallResult;
import com.verteil.thirdparties.adapter.pkfare.common.exception.EntryGateHttpException;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.config.VerteilProperties;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.verteilProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntryGateAuthClientTest {

    private static final String TOKEN_RS = """
            {"access_token":"abc123","scope":"api","token_type":"Bearer","expires_in":43199}""";

    private static final EntryGateCredentials CREDENTIALS = new EntryGateCredentials("client", "secret");
    private static final PkfareAdapterException REJECTED = new EntryGateHttpException(401, "", "UNAUTHORIZED");

    private final ThirdPartyConnector connector = mock(ThirdPartyConnector.class);
    private final EntryGateCredentialsProvider credentialsProvider = mock(EntryGateCredentialsProvider.class);
    private final VerteilProperties properties = verteilProperties();
    private final EntryGateAuthClient client = new EntryGateAuthClient(connector, properties, credentialsProvider);

    @BeforeEach
    void setUp() {
        when(credentialsProvider.get()).thenReturn(CREDENTIALS);
    }

    @Test
    void requestsTokenWithBasicAuthAndFormBody() throws Exception {
        when(connector.callSupplier(any(), eq("PKFARE"), eq("Auth"))).thenReturn(new HttpCallResult(200, TOKEN_RS, 10));

        AccessTokenResponse response = client.login();

        assertThat(response.accessToken()).isEqualTo("abc123");
        assertThat(response.expiresIn()).isEqualTo(43199L);
        HttpPost post = capturedRequest();
        assertThat(post.getUri()).hasToString("https://api.example.com/oauth2/token");
        assertThat(post.getFirstHeader("Authorization").getValue())
                .isEqualTo("Basic " + Base64.getEncoder().encodeToString("client:secret".getBytes(StandardCharsets.UTF_8)));
        assertThat(post.getEntity().getContentType()).startsWith("application/x-www-form-urlencoded");
        assertThat(EntityUtils.toString(post.getEntity())).isEqualTo("grant_type=client_credentials&scope=api");
        verify(connector).buildRequestConfig(30000L);
    }

    @Test
    void usesConfiguredScope() throws Exception {
        properties.setAuthScope("api read");
        when(connector.callSupplier(any(), any(), any())).thenReturn(new HttpCallResult(200, TOKEN_RS, 10));

        client.login();

        assertThat(EntityUtils.toString(capturedRequest().getEntity())).isEqualTo("grant_type=client_credentials&scope=api+read");
    }

    @Test
    void rejectedCredentialsAreNotReportedAsAnExpiredToken() {
        when(connector.callSupplier(any(), any(), any())).thenThrow(REJECTED);

        assertThatThrownBy(client::login)
                .isInstanceOf(PkfareAdapterException.class)
                .hasMessage("Verteil rejected the client credentials")
                .extracting("reason").isEqualTo("UNAVAILABLE");
        verify(credentialsProvider).refresh();
        verify(connector, times(1)).callSupplier(any(), any(), any());
    }

    @Test
    void retriesOnceWithRotatedCredentials() throws Exception {
        when(credentialsProvider.refresh()).thenReturn(true);
        when(credentialsProvider.get()).thenReturn(CREDENTIALS, new EntryGateCredentials("client", "rotated"));
        when(connector.callSupplier(any(), any(), any()))
                .thenThrow(REJECTED)
                .thenReturn(new HttpCallResult(200, TOKEN_RS, 10));

        assertThat(client.login().accessToken()).isEqualTo("abc123");

        ArgumentCaptor<HttpPost> captor = ArgumentCaptor.forClass(HttpPost.class);
        verify(connector, times(2)).callSupplier(captor.capture(), any(), any());
        assertThat(captor.getAllValues().getLast().getFirstHeader("Authorization").getValue())
                .isEqualTo("Basic " + Base64.getEncoder().encodeToString("client:rotated".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void treatsInvalidClientBadRequestAsRejectedCredentials() {
        when(credentialsProvider.refresh()).thenReturn(true);
        when(connector.callSupplier(any(), any(), any()))
                .thenThrow(new EntryGateHttpException(400, "{\"error\":\"invalid_client\"}", "UNAVAILABLE"))
                .thenReturn(new HttpCallResult(200, TOKEN_RS, 10));

        assertThat(client.login().accessToken()).isEqualTo("abc123");
        verify(credentialsProvider).refresh();
    }

    @Test
    void doesNotRefreshCredentialsForOtherFailures() {
        when(connector.callSupplier(any(), any(), any()))
                .thenThrow(new EntryGateHttpException(503, "", "UNAVAILABLE"))
                .thenThrow(new EntryGateHttpException(400, "{\"error\":\"invalid_scope\"}", "UNAVAILABLE"))
                .thenThrow(new PkfareAdapterException("3", "Unable to connect to Verteil", "UNAVAILABLE"));

        assertThatThrownBy(client::login).hasMessage("HTTP 503 from Verteil");
        assertThatThrownBy(client::login).hasMessage("HTTP 400 from Verteil");
        assertThatThrownBy(client::login).hasMessage("Unable to connect to Verteil");
        verify(credentialsProvider, never()).refresh();
    }

    @Test
    void failsWhenTokenResponseHasNoAccessToken() {
        when(connector.callSupplier(any(), any(), any())).thenReturn(new HttpCallResult(200, "{\"token_type\":\"Bearer\"}", 10));

        assertThatThrownBy(client::login).hasMessage("Verteil token response has no access_token");
    }

    private HttpPost capturedRequest() {
        ArgumentCaptor<HttpPost> captor = ArgumentCaptor.forClass(HttpPost.class);
        verify(connector).callSupplier(captor.capture(), any(), any());
        return captor.getValue();
    }
}
