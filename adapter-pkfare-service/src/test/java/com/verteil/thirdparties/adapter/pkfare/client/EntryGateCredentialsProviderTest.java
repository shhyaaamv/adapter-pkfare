package com.verteil.thirdparties.adapter.pkfare.client;

import com.verteil.thirdparties.adapter.pkfare.client.model.EntryGateCredentials;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntryGateCredentialsProviderTest {

    private static final String SECRET_NAME = "qa/adapter-pkfare/entry-gate";

    private final SecretsManagerClient secretsManagerClient = mock(SecretsManagerClient.class);

    @Test
    void readsTheSecretAtStartup() {
        returnSecret("{\"username\":\"client\",\"password\":\"secret\",\"note\":\"ignored\"}");

        EntryGateCredentials credentials = new EntryGateCredentialsProvider(secretsManagerClient, SECRET_NAME).get();

        assertThat(credentials).isEqualTo(new EntryGateCredentials("client", "secret"));
        assertThat(credentials.toString()).doesNotContain("secret");
    }

    @Test
    void refusesToStartWithoutUsernameAndPassword() {
        returnSecret("{\"username\":\"client\"}");

        assertThatThrownBy(() -> new EntryGateCredentialsProvider(secretsManagerClient, SECRET_NAME))
                .hasMessage("AWS secret qa/adapter-pkfare/entry-gate has no username or password");
    }

    @Test
    void refusesToStartWhenTheSecretIsNotJson() {
        returnSecret("client:secret");

        assertThatThrownBy(() -> new EntryGateCredentialsProvider(secretsManagerClient, SECRET_NAME))
                .hasMessageContaining("is not a JSON object");
    }

    @Test
    void picksUpARotatedSecretAtMostOncePerInterval() {
        returnSecret("{\"username\":\"client\",\"password\":\"secret\"}");
        EntryGateCredentialsProvider provider = new EntryGateCredentialsProvider(secretsManagerClient, SECRET_NAME);
        returnSecret("{\"username\":\"client\",\"password\":\"rotated\"}");

        assertThat(provider.refresh()).isTrue();
        assertThat(provider.get().password()).isEqualTo("rotated");
        assertThat(provider.refresh()).isFalse();   // within the interval: AWS is not called again
        verify(secretsManagerClient, times(2)).getSecretValue(any(GetSecretValueRequest.class));
    }

    @Test
    void reportsAnUnchangedSecret() {
        returnSecret("{\"username\":\"client\",\"password\":\"secret\"}");
        EntryGateCredentialsProvider provider = new EntryGateCredentialsProvider(secretsManagerClient, SECRET_NAME);

        assertThat(provider.refresh()).isFalse();
    }

    @Test
    void keepsTheCurrentCredentialsWhenTheSecretCannotBeReRead() {
        returnSecret("{\"username\":\"client\",\"password\":\"secret\"}");
        EntryGateCredentialsProvider provider = new EntryGateCredentialsProvider(secretsManagerClient, SECRET_NAME);
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(SecretsManagerException.builder().message("throttled").build());

        assertThat(provider.refresh()).isFalse();
        assertThat(provider.get().password()).isEqualTo("secret");
    }

    private void returnSecret(String secret) {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString(secret).build());
    }
}
