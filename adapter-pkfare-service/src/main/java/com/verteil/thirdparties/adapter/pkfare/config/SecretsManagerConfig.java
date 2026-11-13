package com.verteil.thirdparties.adapter.pkfare.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;

import java.net.URI;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * AWS Secrets Manager client, authenticated by the default AWS credentials chain (the pod's IAM role).
 * {@code aws.secretManager.url} overrides the endpoint (acceptance tests point it at WireMock).
 */
@Configuration
public class SecretsManagerConfig {

    @Bean(destroyMethod = "close")
    public SecretsManagerClient secretsManagerClient(@Value("${aws.secretManager.region}") String region,
                                                     @Value("${aws.secretManager.url:}") String url) {
        SecretsManagerClientBuilder builder = SecretsManagerClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());
        Optional.ofNullable(trimToNull(url)).map(URI::create).ifPresent(builder::endpointOverride);
        return builder.build();
    }
}
