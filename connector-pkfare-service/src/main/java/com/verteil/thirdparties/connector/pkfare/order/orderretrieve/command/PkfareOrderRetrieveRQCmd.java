
package com.verteil.thirdparties.connector.pkfare.order.orderretrieve.command;

import com.verteil.chain.Command;
import com.verteil.thirdparties.connector.pkfare.common.PkfareConnectorContext;
import com.verteil.thirdparties.connector.pkfare.order.orderretrieve.converter.PkfareOrderRetrieveRQConverter;
import org.springframework.stereotype.Component;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@Component
public class PkfareOrderRetrieveRQCmd implements Command<PkfareConnectorContext> {

    private final PkfareOrderRetrieveRQConverter orderRetrieveRQConverter;


    @Override
    public boolean execute(PkfareConnectorContext connectorContext) {
        // Implementation here
        return true;
    }
}
