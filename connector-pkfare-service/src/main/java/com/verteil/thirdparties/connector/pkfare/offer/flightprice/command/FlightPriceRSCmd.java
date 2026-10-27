
package com.verteil.thirdparties.connector.pkfare.offer.flightprice.command;

import com.verteil.chain.Command;
import com.verteil.thirdparties.connector.pkfare.common.PkfareConnectorContext;
import com.verteil.thirdparties.connector.pkfare.offer.flightprice.converter.FlightPriceRSConverter;
import org.springframework.stereotype.Component;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@Component
public class FlightPriceRSCmd implements Command<PkfareConnectorContext> {

    private final FlightPriceRSConverter flightPriceRSConverter;

    @Override
    public boolean execute(PkfareConnectorContext connectorContext) {
        // Implementation here
        return true;
    }
}
