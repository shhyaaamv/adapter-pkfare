package com.verteil.thirdparties.adapter.pkfare.common.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.text.SimpleDateFormat;

/**
 * The two wire formats this adapter speaks.
 * <ul>
 *   <li>{@link #ndc()}: VDC NDC 16.1 JSON exchanged with Entry-Gate. Same settings as the connectors'
 *   {@code verteilObjectMapper} (JAXB introspector, NON_EMPTY), plus lexical XSD handling of
 *   {@link XMLGregorianCalendar} so date-only values go out as {@code 2026-10-30} and date-times in either
 *   form come back.</li>
 *   <li>{@link #pkfare()}: PKFARE JSON. Plain Jackson honouring the generated models' annotations and
 *   NON_NULL, because PKFARE requires required-but-empty lists (e.g. {@code shoppingResultList: []}) to be
 *   present, which NON_EMPTY would drop.</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonMappers {

    public static ObjectMapper ndc() {
        final var objectMapper = new ObjectMapper()
                .configure(MapperFeature.USE_ANNOTATIONS, true)
                .registerModule(new JavaTimeModule())
                .registerModule(xmlGregorianCalendarModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.setAnnotationIntrospector(new JaxbAnnotationIntrospector(objectMapper.getTypeFactory(), false));
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"));
        return objectMapper;
    }

    public static ObjectMapper pkfare() {
        final var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return objectMapper;
    }

    private static SimpleModule xmlGregorianCalendarModule() {
        final DatatypeFactory datatypeFactory;
        try {
            datatypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException("No XML DatatypeFactory available", e);
        }
        var module = new SimpleModule("XmlGregorianCalendarLexical");
        module.addSerializer(XMLGregorianCalendar.class, new JsonSerializer<>() {
            @Override
            public void serialize(XMLGregorianCalendar value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.toXMLFormat());
            }
        });
        module.addDeserializer(XMLGregorianCalendar.class, new JsonDeserializer<>() {
            @Override
            public XMLGregorianCalendar deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
                String text = parser.getValueAsString();
                if (text == null || text.isBlank()) {
                    return null;
                }
                try {
                    return datatypeFactory.newXMLGregorianCalendar(text.trim());
                } catch (IllegalArgumentException e) {
                    return (XMLGregorianCalendar) ctx.handleWeirdStringValue(XMLGregorianCalendar.class, text, e.getMessage());
                }
            }
        });
        return module;
    }
}
