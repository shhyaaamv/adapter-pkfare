package com.verteil.thirdparties.adapter.pkfare.offer.search.command;

import com.verteil.chain.Command;
import com.verteil.schema.pkfare.SearchRequest;
import com.verteil.schemas.ndc16_1.AirShoppingRQ;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.utils.EntryGateTimeout;
import com.verteil.thirdparties.adapter.pkfare.offer.search.converter.SearchRQConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.AirShoppingConstants.CUTOFF_BUFFER_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.AirShoppingConstants.TIMEOUT_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_AIRSHOPPING;

@RequiredArgsConstructor
@Component
public class SearchRQCmd implements Command<PkfareAdapterContext> {

    private final SearchRQConverter searchRQConverter;

    @Override
    public boolean execute(PkfareAdapterContext context) {
        AirShoppingRQ airShoppingRQ = searchRQConverter.convert(context);
        context.setTimeoutMillis(EntryGateTimeout.resolve(TIMEOUT_MILLIS, CUTOFF_BUFFER_MILLIS,
                ((SearchRequest) context.getRequest()).getCutOffTime()));
        context.setProviderApiRequest(SERVICE_AIRSHOPPING, airShoppingRQ);
        context.setProviderApiService(SERVICE_AIRSHOPPING);
        return CONTINUE_PROCESSING;
    }
}
