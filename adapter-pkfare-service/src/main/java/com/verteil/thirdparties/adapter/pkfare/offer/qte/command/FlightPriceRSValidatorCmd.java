package com.verteil.thirdparties.adapter.pkfare.offer.qte.command;

import com.verteil.chain.Command;
import com.verteil.schemas.ndc16_1.ErrorType;
import com.verteil.schemas.ndc16_1.ErrorsType;
import com.verteil.schemas.ndc16_1.FlightPriceRS;
import com.verteil.schemas.ndc16_1.PricedFlightOffer;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.VERTEIL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_EMPTY_RESPONSE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.MSG_NO_FARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.NO_FARE_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_FLIGHTPRICE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAVAILABLE_REASON_CODE;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

/**
 * Validates the Entry-Gate FlightPriceRS before mapping.
 * <ul>
 *   <li>no response -> Entry-Gate error (reason UNAVAILABLE)</li>
 *   <li>a priced offer present -> continue; errors alongside it are only logged, and when several offers come
 *   back the first one is used</li>
 *   <li>no priced offer with errors -> Entry-Gate error carrying the first error (msg ShortText else value, reason
 *   Code else Reason)</li>
 *   <li>no priced offer and no errors -> "no fare" (reason NO_FARE, PKFARE status 201)</li>
 * </ul>
 */
@Slf4j
@Component
public class FlightPriceRSValidatorCmd implements Command<PkfareAdapterContext> {

    @Override
    public boolean execute(PkfareAdapterContext context) {
        var response = (FlightPriceRS) context.getProviderApiResponse(SERVICE_FLIGHTPRICE);
        if (isNull(response)) {
            throw new PkfareAdapterException(VERTEIL_ERROR.getCode(), ERROR_MSG_EMPTY_RESPONSE, UNAVAILABLE_REASON_CODE);
        }
        List<ErrorType> errors = Optional.ofNullable(response.getErrors())
                .map(ErrorsType::getError)
                .orElse(List.of());
        List<PricedFlightOffer> offers = pricedOffers(response);
        if (!offers.isEmpty()) {
            logAnomalies(offers, errors);
            return CONTINUE_PROCESSING;
        }
        if (errors.isEmpty()) {
            log.warn("FlightPriceRS has no priced offer and no errors");
            throw new PkfareAdapterException(VERTEIL_ERROR.getCode(), MSG_NO_FARE, NO_FARE_REASON_CODE);
        }
        ErrorType error = errors.getFirst();
        log.warn("FlightPriceRS returned errors without a priced offer: {}", describe(error));
        throw new PkfareAdapterException(VERTEIL_ERROR.getCode(), defaultIfBlank(error.getShortText(), error.getValue()),
                defaultIfBlank(error.getCode(), error.getReason()));
    }

    private static List<PricedFlightOffer> pricedOffers(FlightPriceRS response) {
        return Optional.ofNullable(response.getPricedFlightOffers())
                .map(FlightPriceRS.PricedFlightOffers::getPricedFlightOffer)
                .orElse(List.of());
    }

    private static void logAnomalies(List<PricedFlightOffer> offers, List<ErrorType> errors) {
        if (offers.size() > 1) {
            log.warn("FlightPriceRS has {} priced offers, using the first", offers.size());
        }
        if (!errors.isEmpty()) {
            log.warn("FlightPriceRS has a priced offer and {} error(s), first: {}", errors.size(), describe(errors.getFirst()));
        }
    }

    private static String describe(ErrorType error) {
        return "code=" + error.getCode() + ", value=" + error.getValue() + ", shortText=" + error.getShortText() + ", owner=" + error.getOwner();
    }
}
