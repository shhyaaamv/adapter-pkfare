package com.verteil.thirdparties.adapter.pkfare.common.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IpWhitelistTest {

    @ParameterizedTest(name = "{0} in [{1}] -> {2}")
    @CsvSource(delimiter = '|', value = {
            "203.0.113.10 | 203.0.113.10                | true",
            "203.0.113.11 | 203.0.113.10                | false",
            "203.0.113.77 | 203.0.113.0/24              | true",
            "203.0.114.1  | 203.0.113.0/24              | false",
            "10.1.2.3     | 10.0.0.0/8                  | true",
            "10.1.2.3     | 10.0.0.0/9                  | true",
            "10.200.2.3   | 10.0.0.0/9                  | false",
            "8.8.8.8      | 0.0.0.0/0                   | true",
            "2001:db8::1  | 2001:db8::/32               | true",
            "2001:db9::1  | 2001:db8::/32               | false",
            "203.0.113.10 | 2001:db8::/32               | false",
            "203.0.113.10 | not-an-ip;203.0.113.0/33    | false",
            "203.0.113.10 | not-an-ip;203.0.113.10      | true",
            "localhost    | 127.0.0.1                   | false",
            "''           | 203.0.113.10                | false"
    })
    void matchesLiteralAddressesAndCidrBlocks(String clientIp, String whitelist, boolean expected) {
        assertThat(IpWhitelist.isAllowed(clientIp, Arrays.asList(whitelist.split(";")))).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void emptyWhitelistRejectsEverything() {
        assertThat(IpWhitelist.isAllowed("203.0.113.10", List.of())).isFalse();
        assertThat(IpWhitelist.isAllowed("203.0.113.10", null)).isFalse();
    }
}
