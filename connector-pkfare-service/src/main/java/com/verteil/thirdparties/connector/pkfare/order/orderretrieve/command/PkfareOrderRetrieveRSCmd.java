
package com.verteil.thirdparties.connector.pkfare.order.orderretrieve.command;

import com.verteil.chain.Command;
import com.verteil.thirdparties.connector.pkfare.common.PkfareConnectorContext;
import com.verteil.thirdparties.connector.pkfare.order.orderretrieve.converter.PkfareOrderRetrieveRSConverter;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Component
public class PkfareOrderRetrieveRSCmd implements Command<PkfareConnectorContext> {

    private final PkfareOrderRetrieveRSConverter orderRetrieveRSConverter;

    @Override
    public boolean execute(PkfareConnectorContext connectorContext) {
        // Implementation here
        return true;
    }
}
