package com.verteil.thirdparties.adapter.pkfare.offer.search.command;

import com.verteil.chain.Command;
import com.verteil.configurator.vdc.data.VdcConfiguration;
import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schemas.ndc16_1.AirShoppingRQ;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.offer.search.converter.SearchRQConverter;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.CUTOFF_BUFFER_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_CUTOFF_BUFFER_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.DEFAULT_TIMEOUT_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE_API;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AIRSHOPPING;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.TIMEOUT_MILLIS;

@AllArgsConstructor
@Component
public class SearchRQCmd implements Command<PkfareAdapterContext> {

    private static final long MIN_TIMEOUT_MILLIS = 1000L;

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
     * Entry-Gate timeout = {@code PKFARE_API.TIMEOUT_MILLIS} (AirShopping), capped by PKFARE's {@code cutOffTime}
     * minus a safety buffer: an answer arriving after the buyer's cutoff is discarded by PKFARE anyway.
     */
    private static int timeoutMillis(PkfareAdapterContext context) {
        Optional<VdcConfiguration> configuration = Optional.ofNullable(context.getConfigurations().get(SERVICE_AIRSHOPPING));
        long timeout = configuration.map(vdcConfiguration -> vdcConfiguration.getLong(PKFARE_API, TIMEOUT_MILLIS, (long) DEFAULT_TIMEOUT_MILLIS))
                .orElse((long) DEFAULT_TIMEOUT_MILLIS);
        long buffer = configuration.map(vdcConfiguration -> vdcConfiguration.getLong(PKFARE_API, CUTOFF_BUFFER_MILLIS, DEFAULT_CUTOFF_BUFFER_MILLIS))
                .orElse(DEFAULT_CUTOFF_BUFFER_MILLIS);
        long cappedTimeout = Optional.ofNullable(((SearchRequest) context.getRequest()).getCutOffTime())
                .filter(cutOffTime -> cutOffTime > 0)
                .map(cutOffTime -> Math.min(timeout, Math.max(cutOffTime - buffer, MIN_TIMEOUT_MILLIS)))
                .orElse(timeout);
        return Math.toIntExact(cappedTimeout);
    }
}
