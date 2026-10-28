package com.verteil.thirdparties.adapter.pkfare.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * The HTTP API speaks PKFARE JSON, so the primary ObjectMapper (used by Spring MVC) is the PKFARE one.
 * VDC NDC JSON for Entry-Gate uses the separate {@code ndcObjectMapper} bean.
 */
@Configuration
public class MessageConversionConfig {
    @Bean
    @Primary
    public ObjectMapper pkfareObjectMapper() {
        return JsonMappers.pkfare();
    }
}
