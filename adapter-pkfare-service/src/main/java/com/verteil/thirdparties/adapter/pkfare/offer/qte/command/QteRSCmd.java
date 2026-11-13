package com.verteil.thirdparties.adapter.pkfare.offer.qte.command;

import com.verteil.chain.Command;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.offer.qte.converter.QteRSConverter;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class QteRSCmd implements Command<PkfareAdapterContext> {

    private final QteRSConverter qteRSConverter;

    @Override
    public boolean execute(PkfareAdapterContext context) {
        context.setResponse(qteRSConverter.convert(context));
        return CONTINUE_PROCESSING;
    }
}
