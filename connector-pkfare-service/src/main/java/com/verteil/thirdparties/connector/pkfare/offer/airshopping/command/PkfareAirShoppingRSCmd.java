
package com.verteil.thirdparties.connector.pkfare.offer.airshopping.command;

import com.verteil.chain.Command;
import com.verteil.thirdparties.connector.pkfare.common.PkfareConnectorContext;
import com.verteil.thirdparties.connector.pkfare.offer.airshopping.converter.AirShoppingRSConverter;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Component
public class PkfareAirShoppingRSCmd implements Command<PkfareConnectorContext> {

    private final AirShoppingRSConverter airShoppingRSConverter;

    @Override
    public boolean execute(PkfareConnectorContext connectorContext) {
        // Implementation here
        return true;
    }
}
