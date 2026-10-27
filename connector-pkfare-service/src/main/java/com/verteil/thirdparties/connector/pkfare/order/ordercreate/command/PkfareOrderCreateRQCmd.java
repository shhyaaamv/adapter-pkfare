
package com.verteil.thirdparties.connector.pkfare.order.ordercreate.command;

import com.verteil.chain.Command;
import com.verteil.thirdparties.connector.pkfare.common.PkfareConnectorContext;
import com.verteil.thirdparties.connector.pkfare.order.ordercreate.converter.PkfareOrderCreateRQConverter;
import org.springframework.stereotype.Component;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@Component
public class PkfareOrderCreateRQCmd implements Command<PkfareConnectorContext> {

    private final PkfareOrderCreateRQConverter orderCreateRQConverter;


    @Override
    public boolean execute(PkfareConnectorContext connectorContext) {
        // Implementation here
        return true;
    }
}
