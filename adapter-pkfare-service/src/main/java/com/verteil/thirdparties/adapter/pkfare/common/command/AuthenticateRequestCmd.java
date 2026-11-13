package com.verteil.thirdparties.adapter.pkfare.common.command;

import com.verteil.chain.Command;
import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.common.security.IpWhitelist;
import com.verteil.thirdparties.adapter.pkfare.config.PkfareProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.AUTHENTICATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_INVALID_VENDOR;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.ERROR_MSG_IP_NOT_WHITELISTED;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.UNAUTHORIZED_REASON_CODE;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Authenticates the PKFARE caller before anything is sent to Entry-Gate:
 * <ol>
 *   <li>{@code vendorCode} must be listed in {@code pkfare.vendor-codes}.</li>
 *   <li>unless {@code pkfare.ip-whitelist.enabled} is {@code false}, the caller IP must match
 *   {@code pkfare.ip-whitelist.addresses} (IPs or CIDRs). An empty whitelist rejects everything.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticateRequestCmd implements Command<PkfareAdapterContext> {

    private final PkfareProperties pkfareProperties;

    @Override
    public boolean execute(PkfareAdapterContext context) {
        String vendorCode = context.getVendorCode();
        if (isBlank(vendorCode) || pkfareProperties.getVendorCodes().stream().noneMatch(vendorCode.trim()::equals)) {
            log.warn("Rejected PKFARE request: vendorCode '{}' is not configured", vendorCode);
            throw new PkfareAdapterException(AUTHENTICATION_FAILURE.getCode(), ERROR_MSG_INVALID_VENDOR, UNAUTHORIZED_REASON_CODE);
        }
        if (pkfareProperties.getIpWhitelist().isEnabled()) {
            validateClientIp(context, vendorCode);
        }
        return CONTINUE_PROCESSING;
    }

    private void validateClientIp(PkfareAdapterContext context, String vendorCode) {
        if (!IpWhitelist.isAllowed(context.getClientIp(), pkfareProperties.getIpWhitelist().getAddresses())) {
            log.warn("Rejected PKFARE request for vendorCode '{}': IP '{}' is not whitelisted", vendorCode, context.getClientIp());
            throw new PkfareAdapterException(AUTHENTICATION_FAILURE.getCode(), ERROR_MSG_IP_NOT_WHITELISTED, UNAUTHORIZED_REASON_CODE);
        }
    }
}
