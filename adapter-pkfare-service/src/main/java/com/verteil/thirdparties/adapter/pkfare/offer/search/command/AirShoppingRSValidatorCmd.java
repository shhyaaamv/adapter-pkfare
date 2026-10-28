package com.verteil.thirdparties.adapter.pkfare.offer.search.command;

import com.verteil.chain.Command;
import com.verteil.schemas.ndc16_1.AirShoppingRS;
import com.verteil.schemas.ndc16_1.ErrorType;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.converter.ErrorResponseConverter;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.ENTRY_GATE_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_EMPTY_RESPONSE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.NO_RESULT_ERROR_CODES;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE_API;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AIRSHOPPING;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAVAILABLE_REASON_CODE;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

/**
 * Validates the Entry-Gate AirShoppingRS before mapping.
 * <ul>
 *   <li>no response -> Entry-Gate error</li>
 *   <li>offers present -> continue (errors/warnings alongside offers are only logged)</li>
 *   <li>no offers and no errors, or only errors whose code is listed in {@code PKFARE_API.NO_RESULT_ERROR_CODES}
 *   -> "no content": status 0 with empty lists, as PKFARE requires; chain stops</li>
 *   <li>no offers with any other error -> Entry-Gate error carrying the first error text</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AirShoppingRSValidatorCmd implements Command<PkfareAdapterContext> {

    private final ErrorResponseConverter errorResponseConverter;

    @Override
    public boolean execute(PkfareAdapterContext context) {
        var response = (AirShoppingRS) context.getProviderApiResponse(SERVICE_AIRSHOPPING);
        if (isNull(response)) {
            throw new PkfareAdapterException(ENTRY_GATE_ERROR.getCode(), ERROR_MSG_EMPTY_RESPONSE, UNAVAILABLE_REASON_CODE);
        }
        List<ErrorType> errors = Optional.ofNullable(response.getErrors()).map(responseErrors -> responseErrors.getError()).orElse(List.of());
        if (hasOffers(response)) {
            if (!errors.isEmpty()) {
                log.warn("AirShoppingRS has offers and {} error(s), first: {}", errors.size(), describe(errors.getFirst()));
            }
            return CONTINUE_PROCESSING;
        }
        if (errors.isEmpty() || errors.stream().allMatch(error -> isNoResult(error, context))) {
            log.info("AirShoppingRS has no offers{}", errors.isEmpty() ? "" : ": " + describe(errors.getFirst()));
            context.setResponse(errorResponseConverter.emptySearchResponse());
            return PROCESSING_COMPLETE;
        }
        ErrorType error = errors.getFirst();
        log.warn("AirShoppingRS returned errors without offers: {}", describe(error));
        throw new PkfareAdapterException(ENTRY_GATE_ERROR.getCode(), defaultIfBlank(error.getShortText(), error.getValue()),
                defaultIfBlank(error.getCode(), error.getReason()));
    }

    private static boolean hasOffers(AirShoppingRS response) {
        return Optional.ofNullable(response.getOffersGroup())
                .map(offersGroup -> offersGroup.getAirlineOffers().stream().anyMatch(airlineOffers -> !airlineOffers.getAirlineOffer().isEmpty()))
                .orElse(false);
    }

    private static boolean isNoResult(ErrorType error, PkfareAdapterContext context) {
        List<String> noResultCodes = Optional.ofNullable(context.getConfigurations().get(context.getService()))
                .map(configuration -> configuration.getListFromCsv(PKFARE_API, NO_RESULT_ERROR_CODES, List.of()))
                .orElse(List.of());
        return noResultCodes.contains(error.getCode()) || noResultCodes.contains(error.getValue());
    }

    private static String describe(ErrorType error) {
        return "code=" + error.getCode() + ", value=" + error.getValue() + ", shortText=" + error.getShortText() + ", owner=" + error.getOwner();
    }
}
