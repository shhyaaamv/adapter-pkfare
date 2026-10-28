package com.verteil.thirdparties.adapter.pkfare.common.command;

import com.verteil.chain.Command;
import com.verteil.configurator.vdc.data.VdcConfiguration;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.common.security.IpWhitelist;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.AUTHENTICATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_INVALID_VENDOR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_IP_NOT_WHITELISTED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.IP_WHITELIST;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.IP_WHITELIST_ENABLED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE_API;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAUTHORIZED_REASON_CODE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.VENDOR_CODES;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Authenticates the PKFARE caller before anything is sent to Entry-Gate:
 * <ol>
 *   <li>{@code vendorCode} must be listed in {@code PKFARE_API.VENDOR_CODES}.</li>
 *   <li>unless {@code PKFARE_API.IP_WHITELIST_ENABLED} is {@code false} (default {@code true}), the caller IP must
 *   match {@code PKFARE_API.IP_WHITELIST} (IPs or CIDRs). An empty whitelist rejects everything.</li>
 * </ol>
 * All settings are resolved with the vendor code as configurator office code, so they can be set globally or per
 * vendor.
 */
@Slf4j
@Component
public class AuthenticateRequestCmd implements Command<PkfareAdapterContext> {

    @Override
    public boolean execute(PkfareAdapterContext context) {
        VdcConfiguration configuration = context.getConfigurations().get(context.getService());
        if (isNull(configuration)) {
            throw new PkfareAdapterException(AUTHENTICATION_FAILURE.getCode(), ERROR_MSG_INVALID_VENDOR, UNAUTHORIZED_REASON_CODE);
        }
        String vendorCode = context.getVendorCode();
        List<String> vendorCodes = configuration.getListFromCsv(PKFARE_API, VENDOR_CODES, List.of());
        if (isBlank(vendorCode) || vendorCodes.stream().noneMatch(vendorCode.trim()::equals)) {
            log.warn("Rejected PKFARE request: vendorCode '{}' is not configured", vendorCode);
            throw new PkfareAdapterException(AUTHENTICATION_FAILURE.getCode(), ERROR_MSG_INVALID_VENDOR, UNAUTHORIZED_REASON_CODE);
        }
        if (configuration.getBoolean(PKFARE_API, IP_WHITELIST_ENABLED, true)) {
            validateClientIp(context, configuration, vendorCode);
        }
        return CONTINUE_PROCESSING;
    }

    private void validateClientIp(PkfareAdapterContext context, VdcConfiguration configuration, String vendorCode) {
        List<String> whitelist = configuration.getListFromCsv(PKFARE_API, IP_WHITELIST, List.of());
        if (!IpWhitelist.isAllowed(context.getClientIp(), whitelist)) {
            log.warn("Rejected PKFARE request for vendorCode '{}': IP '{}' is not whitelisted", vendorCode, context.getClientIp());
            throw new PkfareAdapterException(AUTHENTICATION_FAILURE.getCode(), ERROR_MSG_IP_NOT_WHITELISTED, UNAUTHORIZED_REASON_CODE);
        }
    }
}
