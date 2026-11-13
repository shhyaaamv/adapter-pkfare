package com.verteil.thirdparties.adapter.pkfare.controller;

import com.verteil.schema.pkfare.QteResponse;
import com.verteil.schema.pkfare.SearchResponse;
import com.verteil.thirdparties.adapter.pkfare.common.converter.ErrorResponseConverter;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PkfareExceptionHandlerTest {

    private final PkfareExceptionHandler handler = new PkfareExceptionHandler(new ErrorResponseConverter());

    @Test
    void answersQteWithQteResponse() {
        Object response = handler.handleUnreadableRequest(unreadable(), new MockHttpServletRequest("POST", "/qte"));

        assertThat(response).isInstanceOf(QteResponse.class);
        QteResponse qteResponse = (QteResponse) response;
        assertThat(qteResponse.getStatus()).isEqualTo(4);
        assertThat(qteResponse.getMsg()).isEqualTo("Request Parameters error");
        assertThat(qteResponse.getErrorCode()).isNull();
        assertThat(qteResponse.getErrorMsg()).isNull();
    }

    @Test
    void answersOtherPathsWithSearchResponse() {
        Object response = handler.handleUnreadableRequest(unreadable(), new MockHttpServletRequest("POST", "/search"));

        assertThat(response).isInstanceOf(SearchResponse.class);
        SearchResponse searchResponse = (SearchResponse) response;
        assertThat(searchResponse.getStatus()).isEqualTo(4);
        assertThat(searchResponse.getMsg()).isEqualTo("Request Parameters error");
        assertThat(searchResponse.getShoppingResultList()).isEmpty();
    }

    private static HttpMessageNotReadableException unreadable() {
        return new HttpMessageNotReadableException("JSON parse error", new MockHttpInputMessage(new byte[0]));
    }
}
