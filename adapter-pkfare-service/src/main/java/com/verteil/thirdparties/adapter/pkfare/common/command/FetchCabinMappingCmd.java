package com.verteil.thirdparties.adapter.pkfare.common.command;

import com.verteil.chain.Command;
import com.verteil.thirdparties.adapter.pkfare.common.AdapterCommonService;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.common.model.CabinMapping;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.INTERNAL_ERROR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_CABIN_MAPPING_MISSING;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PROCESS_FAILURE_REASON_CODE;

/** Puts the configured PKFARE <-> VDC cabin mapping on the context; fails when none is configured. */
@AllArgsConstructor
@Component
public class FetchCabinMappingCmd implements Command<PkfareAdapterContext> {

    private final AdapterCommonService adapterCommonService;

    @Override
    public boolean execute(PkfareAdapterContext context) {
        CabinMapping cabinMapping = adapterCommonService.cabinMapping();
        if (cabinMapping.isEmpty()) {
            throw new PkfareAdapterException(INTERNAL_ERROR.getCode(), ERROR_MSG_CABIN_MAPPING_MISSING, PROCESS_FAILURE_REASON_CODE);
        }
        context.setCabinMapping(cabinMapping);
        return CONTINUE_PROCESSING;
    }
}
