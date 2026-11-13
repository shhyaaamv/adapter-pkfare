package com.verteil.thirdparties.adapter.pkfare.offer.common.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.common.json.JsonMappers;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VALIDATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_INVALID_ROUTING_DATA;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VALIDATION_FAILURE_REASON_CODE;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * {@link OfferData} as PKFARE's {@code routing.data}: standard Base64 of its UTF-8 JSON (PKFARE mapper,
 * so null fields are left out and unknown ones ignored on the way back).
 */
@Component
public class OfferDataCodec {

    private final ObjectMapper objectMapper = JsonMappers.pkfare();

    public String encode(OfferData offerData) {
        try {
            return Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(offerData));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise the offer data", e);
        }
    }

    /**
     * Checks the format only: whether the offer data holds what a service needs is up to that service.
     *
     * @throws PkfareAdapterException VALIDATION_FAILURE when {@code data} is blank, not Base64 or not offer data
     */
    public OfferData decode(String data) {
        if (isBlank(data)) {
            throw invalidData(null);
        }
        return read(base64Decode(data));
    }

    private static byte[] base64Decode(String data) {
        try {
            return Base64.getDecoder().decode(data.trim());
        } catch (IllegalArgumentException e) {
            throw invalidData(e);
        }
    }

    private OfferData read(byte[] json) {
        try {
            return Optional.ofNullable(objectMapper.readValue(new String(json, StandardCharsets.UTF_8), OfferData.class))
                    .orElseThrow(() -> invalidData(null));
        } catch (JsonProcessingException e) {
            throw invalidData(e);
        }
    }

    private static PkfareAdapterException invalidData(Throwable cause) {
        return new PkfareAdapterException(VALIDATION_FAILURE.getCode(), ERROR_MSG_INVALID_ROUTING_DATA, cause,
                VALIDATION_FAILURE_REASON_CODE);
    }
}
