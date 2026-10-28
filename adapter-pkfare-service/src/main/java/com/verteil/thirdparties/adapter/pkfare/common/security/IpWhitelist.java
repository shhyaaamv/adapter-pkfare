package com.verteil.thirdparties.adapter.pkfare.common.security;

import com.google.common.net.InetAddresses;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.util.stream.IntStream;
import java.util.Collection;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Matches a client IP against whitelist entries that are either literal addresses ({@code 203.0.113.7}) or CIDR
 * blocks ({@code 203.0.113.0/24}, {@code 2001:db8::/32}). Only IP literals are accepted — no host names, so no DNS
 * lookups happen on the request path. An empty whitelist matches nothing (fail closed).
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IpWhitelist {

    public static boolean isAllowed(String clientIp, Collection<String> whitelist) {
        if (isBlank(clientIp) || whitelist == null || whitelist.isEmpty() || !InetAddresses.isInetAddress(clientIp.trim())) {
            return false;
        }
        InetAddress client = InetAddresses.forString(clientIp.trim());
        return whitelist.stream()
                .filter(entry -> !isBlank(entry))
                .anyMatch(entry -> matches(client, entry.trim()));
    }

    private static boolean matches(InetAddress client, String entry) {
        int slash = entry.indexOf('/');
        String address = slash < 0 ? entry : entry.substring(0, slash);
        if (!InetAddresses.isInetAddress(address)) {
            log.warn("Ignoring invalid IP whitelist entry '{}'", entry);
            return false;
        }
        byte[] network = InetAddresses.forString(address).getAddress();
        byte[] candidate = client.getAddress();
        if (network.length != candidate.length) {
            return false;
        }
        int prefix;
        try {
            prefix = slash < 0 ? network.length * 8 : Integer.parseInt(entry.substring(slash + 1));
        } catch (NumberFormatException _) {
            log.warn("Ignoring invalid IP whitelist entry '{}'", entry);
            return false;
        }
        if (prefix < 0 || prefix > network.length * 8) {
            log.warn("Ignoring invalid IP whitelist entry '{}'", entry);
            return false;
        }
        int fullBytes = prefix / 8;
        if (IntStream.range(0, fullBytes).anyMatch(i -> network[i] != candidate[i])) {
            return false;
        }
        int remainingBits = prefix % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = (0xFF << (8 - remainingBits)) & 0xFF;
        return (network[fullBytes] & mask) == (candidate[fullBytes] & mask);
    }
}
