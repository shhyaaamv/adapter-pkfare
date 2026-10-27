package com.verteil.thirdparties.connector.pkfare.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verteil.commons.core.dto.RequestWrapperDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class VerteilHttpMessageConverter extends AbstractHttpMessageConverter<RequestWrapperDto> {

    private final ObjectMapper verteilObjectMapper;

    @Autowired
    public VerteilHttpMessageConverter(@Qualifier("verteilObjectMapper") ObjectMapper verteilObjectMapper) {
        super(new MediaType("application", "json"));
        this.verteilObjectMapper = verteilObjectMapper;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return RequestWrapperDto.class.isAssignableFrom(clazz);
    }

    @Override
    protected RequestWrapperDto readInternal(Class<? extends RequestWrapperDto> clazz, HttpInputMessage inputMessage) throws HttpMessageNotReadableException, IOException {
        RequestWrapperDto requestWrapperDto = verteilObjectMapper.readValue(inputMessage.getBody(), RequestWrapperDto.class);
        requestWrapperDto.setRequest(verteilObjectMapper.convertValue(requestWrapperDto.getRequest(), requestWrapperDto.getRequestType()));
        return requestWrapperDto;
    }

    @Override
    protected void writeInternal(RequestWrapperDto requestWrapperDto, HttpOutputMessage outputMessage) throws HttpMessageNotWritableException, IOException {
        final var outputStream = outputMessage.getBody();
        outputStream.write(verteilObjectMapper.writeValueAsBytes(requestWrapperDto));
        outputStream.close();
    }
}
