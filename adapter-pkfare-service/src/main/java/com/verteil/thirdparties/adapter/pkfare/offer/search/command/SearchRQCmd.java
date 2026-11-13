package com.verteil.thirdparties.adapter.pkfare.offer.search.command;

import com.verteil.chain.Command;
import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schemas.ndc16_1.AirShoppingRQ;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.offer.search.converter.SearchRQConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.AirShoppingConstants.CUTOFF_BUFFER_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.AirShoppingConstants.MIN_TIMEOUT_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.AirShoppingConstants.TIMEOUT_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AIRSHOPPING;

@RequiredArgsConstructor
@Component
public class SearchRQCmd implements Command<PkfareAdapterContext> {

    private final SearchRQConverter searchRQConverter;

    @Override
    public boolean execute(PkfareAdapterContext context) {
        AirShoppingRQ airShoppingRQ = searchRQConverter.convert(context);
        context.setTimeoutMillis(timeoutMillis(context));
        context.setProviderApiRequest(SERVICE_AIRSHOPPING, airShoppingRQ);
        context.setProviderApiService(SERVICE_AIRSHOPPING);
        return CONTINUE_PROCESSING;
    }

    /**
     * Entry-Gate timeout = {@code TIMEOUT_MILLIS}, capped by PKFARE's {@code cutOffTime} minus
     * {@code CUTOFF_BUFFER_MILLIS}: an answer arriving after the buyer's cutoff is discarded by PKFARE anyway.
     */
    private static int timeoutMillis(PkfareAdapterContext context) {
        long cappedTimeout = Optional.ofNullable(((SearchRequest) context.getRequest()).getCutOffTime())
                .filter(cutOffTime -> cutOffTime > 0)
                .map(cutOffTime -> Math.min(TIMEOUT_MILLIS, Math.max(cutOffTime - CUTOFF_BUFFER_MILLIS, MIN_TIMEOUT_MILLIS)))
                .orElse(TIMEOUT_MILLIS);
        return Math.toIntExact(cappedTimeout);
    }
}
