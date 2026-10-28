package com.verteil.thirdparties.adapter.pkfare.offer.search.command;

import com.verteil.chain.Command;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.offer.search.converter.SearchRSConverter;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class SearchRSCmd implements Command<PkfareAdapterContext> {

    private final SearchRSConverter searchRSConverter;

    @Override
    public boolean execute(PkfareAdapterContext context) {
        context.setResponse(searchRSConverter.convert(context));
        return CONTINUE_PROCESSING;
    }
}
