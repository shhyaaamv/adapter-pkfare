package com.verteil.thirdparties.adapter.pkfare.offer.search.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import com.verteil.thirdparties.adapter.pkfare.offer.search.model.OfferData;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.INTERNAL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VALIDATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PROCESS_FAILURE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VALIDATION_FAILURE_REASON_CODE;

/**
 * {@link OfferData} <-> {@code shoppingResultList[].data} (Base64 of compact JSON).
 */
@Component
public class OfferDataConverter {

    private final ObjectMapper objectMapper = JsonMappers.pkfare();

    public String encode(OfferData offerData) {
        try {
            return Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(offerData));
        } catch (JsonProcessingException e) {
            throw new PkfareAdapterException(INTERNAL_ERROR.getCode(), "Unable to encode offer data", e, PROCESS_FAILURE_REASON_CODE);
        }
    }

    /** For the pricing (QTE) flow: reads back what {@link #encode} produced. */
    public OfferData decode(String data) {
        try {
            return objectMapper.readValue(Base64.getDecoder().decode(data), OfferData.class);
        } catch (IOException | IllegalArgumentException e) {
            throw new PkfareAdapterException(VALIDATION_FAILURE.getCode(), "Invalid data", e, VALIDATION_FAILURE_REASON_CODE);
        }
    }
}
