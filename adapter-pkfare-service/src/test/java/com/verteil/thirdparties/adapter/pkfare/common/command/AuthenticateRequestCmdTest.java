package com.verteil.thirdparties.adapter.pkfare.common.command;

import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.AUTHENTICATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.searchRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticateRequestCmdTest {

    private final AuthenticateRequestCmd cmd = new AuthenticateRequestCmd();

    @Test
    void acceptsConfiguredVendor() {
        PkfareAdapterContext context = buildContext(searchRequest(), Map.of("VENDOR_CODES", "OTHER,SHA-TEST"));

        assertThat(cmd.execute(context)).isFalse();
    }

    @Test
    void rejectsUnknownVendor() {
        PkfareAdapterContext context = buildContext(searchRequest(), Map.of("VENDOR_CODES", "OTHER"));

        assertThatThrownBy(() -> cmd.execute(context))
                .isInstanceOf(PkfareAdapterException.class)
                .hasMessage("Invalid vendorCode")
                .extracting("errorCode").isEqualTo(AUTHENTICATION_FAILURE.getCode());
    }

    @Test
    void rejectsMissingVendor() {
        var request = searchRequest();
        request.setVendorCode(null);
        PkfareAdapterContext context = buildContext(request, Map.of("VENDOR_CODES", "SHA-TEST"));

        assertThatThrownBy(() -> cmd.execute(context)).hasMessage("Invalid vendorCode");
    }
}
