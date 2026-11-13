package com.verteil.thirdparties.adapter.pkfare.common.command;

import com.verteil.thirdparties.adapter.pkfare.common.PkfareAdapterContext;
import com.verteil.thirdparties.adapter.pkfare.common.exception.PkfareAdapterException;
import com.verteil.thirdparties.adapter.pkfare.config.PkfareProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.verteil.thirdparties.adapter.pkfare.common.exception.ErrorType.AUTHENTICATION_FAILURE;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.buildContext;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.pkfareProperties;
import static com.verteil.thirdparties.adapter.pkfare.utils.TestUtils.searchRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticateRequestCmdTest {

    private final PkfareProperties properties = pkfareProperties();
    private final AuthenticateRequestCmd cmd = new AuthenticateRequestCmd(properties);

    @Test
    void acceptsConfiguredVendorFromWhitelistedIp() {
        configure(List.of("OTHER", "SHA-TEST"), true, List.of("198.51.100.0/24", "203.0.113.10"));

        assertThat(cmd.execute(context())).isFalse();
    }

    @Test
    void rejectsUnknownVendor() {
        configure(List.of("OTHER"), true, List.of("203.0.113.10"));

        assertThatThrownBy(() -> cmd.execute(context()))
                .isInstanceOf(PkfareAdapterException.class)
                .hasMessage("Invalid vendorCode")
                .extracting("errorCode").isEqualTo(AUTHENTICATION_FAILURE.getCode());
    }

    @Test
    void rejectsMissingVendor() {
        configure(List.of("SHA-TEST"), true, List.of("203.0.113.10"));
        var request = searchRequest();
        request.setVendorCode(null);

        assertThatThrownBy(() -> cmd.execute(buildContext(request))).hasMessage("Invalid vendorCode");
    }

    @Test
    void rejectsIpOutsideWhitelist() {
        configure(List.of("SHA-TEST"), true, List.of("198.51.100.0/24"));

        assertThatThrownBy(() -> cmd.execute(context()))
                .isInstanceOf(PkfareAdapterException.class)
                .hasMessage("Request IP is not whitelisted");
    }

    @Test
    void rejectsEverythingWhenWhitelistIsNotConfigured() {
        configure(List.of("SHA-TEST"), true, List.of());

        assertThatThrownBy(() -> cmd.execute(context())).hasMessage("Request IP is not whitelisted");
    }

    @Test
    void checksIpByDefault() {
        assertThatThrownBy(() -> cmd.execute(context())).hasMessage("Request IP is not whitelisted");
    }

    @Test
    void skipsIpCheckWhenWhitelistIsDisabled() {
        configure(List.of("SHA-TEST"), false, List.of());

        assertThat(cmd.execute(context())).isFalse();
    }

    @Test
    void stillRejectsUnknownVendorWhenWhitelistIsDisabled() {
        configure(List.of("OTHER"), false, List.of());

        assertThatThrownBy(() -> cmd.execute(context())).hasMessage("Invalid vendorCode");
    }

    private void configure(List<String> vendorCodes, boolean ipWhitelistEnabled, List<String> addresses) {
        properties.setVendorCodes(vendorCodes);
        properties.getIpWhitelist().setEnabled(ipWhitelistEnabled);
        properties.getIpWhitelist().setAddresses(addresses);
    }

    private static PkfareAdapterContext context() {
        return buildContext(searchRequest());
    }
}
