package com.verteil.thirdparties.connector.pkfare.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.text.SimpleDateFormat;

@Configuration
public class MessageConversionConfig {
    @Bean
    @Primary
    public ObjectMapper verteilObjectMapper() {
        final var objectMapper = new ObjectMapper()
                .configure(MapperFeature.USE_ANNOTATIONS, true)
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        final var typeFactory = objectMapper.getTypeFactory();
        final var annotationIntrospect = new JaxbAnnotationIntrospector(typeFactory, false);
        objectMapper.setAnnotationIntrospector(annotationIntrospect);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        final var dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        objectMapper.setDateFormat(dateFormat);
        return objectMapper;
    }
}
