package com.verteil.thirdparties.adapter.pkfare.offer.qte;

import com.verteil.chain.Chain;
import com.verteil.chain.impl.ImmutableChain;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.command.AuthenticateRequestCmd;
import com.verteil.thirdparties.adapter.pkfare.common.command.InvokeEntryGateCmd;
import com.verteil.thirdparties.adapter.pkfare.offer.qte.command.FlightPriceRSValidatorCmd;
import com.verteil.thirdparties.adapter.pkfare.offer.qte.command.QteRQCmd;
import com.verteil.thirdparties.adapter.pkfare.offer.qte.command.QteRSCmd;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.SERVICE_QTE;

/**
 * PKFARE QTE -> Entry-Gate FlightPrice. No cache: the price must be live.
 * <pre>
 * AuthenticateRequest (vendor code + IP) -> QteRQ (decode routing.data, validate) -> InvokeEntryGate
 *   -> FlightPriceRSValidator -> QteRS
 * </pre>
 */
@Configuration
public class QteConfiguration {

    @Bean(SERVICE_QTE)
    public Chain<PkfareAdapterContext> qte(AuthenticateRequestCmd authenticateRequestCmd,
                                           QteRQCmd qteRQCmd,
                                           InvokeEntryGateCmd invokeEntryGateCmd,
                                           FlightPriceRSValidatorCmd flightPriceRSValidatorCmd,
                                           QteRSCmd qteRSCmd) {
        return ImmutableChain.<PkfareAdapterContext>newBuilder()
                .add(authenticateRequestCmd)
                .add(qteRQCmd)
                .add(invokeEntryGateCmd)
                .add(flightPriceRSValidatorCmd)
                .add(qteRSCmd)
                .build();
    }
}
