
package com.verteil.thirdparties.connector.pkfare.offer.flightprice.command;

import com.verteil.chain.Command;
import com.verteil.thirdparties.connector.pkfare.common.PkfareConnectorContext;
import com.verteil.thirdparties.connector.pkfare.offer.flightprice.converter.FlightPriceRQConverter;
import org.springframework.stereotype.Component;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@Component
public class FlightPriceRQCmd implements Command<PkfareConnectorContext> {

    private final FlightPriceRQConverter flightPriceRQConverter;

    @Override
    public boolean execute(PkfareConnectorContext connectorContext) {
        // Implementation here
        return true;
    }
}
