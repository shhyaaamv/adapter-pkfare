package com.verteil.thirdparties.adapter.pkfare.offer.qte.command;

import com.verteil.chain.Command;
import com.verteil.schema.pkfare.QteRequest;
import com.verteil.schema.pkfare.QteRequestRouting;
import com.verteil.schemas.ndc16_1.FlightPriceRQ;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.common.utils.EntryGateTimeout;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferDataCodec;
import com.verteil.thirdparties.adapter.pkfare.offer.qte.converter.QteRQConverter;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.FlightPriceConstants.CUTOFF_BUFFER_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.FlightPriceConstants.TIMEOUT_MILLIS;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_FLIGHTPRICE;

/**
 * Decodes {@code routing.data} (the {@link OfferData} SEARCH minted) onto the context and builds the Entry-Gate
 * FlightPriceRQ from it and the PKFARE request.
 */
@AllArgsConstructor
@Component
public class QteRQCmd implements Command<PkfareAdapterContext> {

    private final OfferDataCodec offerDataCodec;
    private final QteRQConverter qteRQConverter;

    @Override
    public boolean execute(PkfareAdapterContext context) {
        QteRequest request = (QteRequest) context.getRequest();
        context.setOfferData(offerData(request));
        FlightPriceRQ flightPriceRQ = qteRQConverter.convert(context);
        context.setTimeoutMillis(EntryGateTimeout.resolve(TIMEOUT_MILLIS, CUTOFF_BUFFER_MILLIS, request.getCutOffTime()));
        context.setProviderApiRequest(SERVICE_FLIGHTPRICE, flightPriceRQ);
        context.setProviderApiService(SERVICE_FLIGHTPRICE);
        return CONTINUE_PROCESSING;
    }

    /**
     * @throws PkfareAdapterException VALIDATION_FAILURE when {@code routing.data} is missing or undecodable; whether
     *                                the offer data holds what FlightPriceRQ needs is checked by {@link QteRQConverter}
     */
    private OfferData offerData(QteRequest request) {
        return offerDataCodec.decode(Optional.ofNullable(request)
                .map(QteRequest::getRouting)
                .map(QteRequestRouting::getData)
                .orElse(null));
    }
}
