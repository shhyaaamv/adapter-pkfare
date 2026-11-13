package com.verteil.thirdparties.adapter.pkfare.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PKFARE access settings under {@code pkfare.*}, set per environment in {@code application-<profile>.yaml}.
 * Service behaviour (e.g. AirShopping preferences) is fixed in code, see {@code AirShoppingConstants}.
 */
@Data
@ConfigurationProperties("pkfare")
public class PkfareProperties {

    /** PKFARE service (lower case, e.g. {@code search}) -> served or not. Services not listed are not served. */
    private Map<String, Boolean> enabledServices = new LinkedHashMap<>();
    /** Accepted PKFARE {@code vendorCode}s. */
    private List<String> vendorCodes = new ArrayList<>();
    private IpWhitelist ipWhitelist = new IpWhitelist();
    private MessageLog messageLog = new MessageLog();

    public boolean isServiceEnabled(String service) {
        return Boolean.TRUE.equals(enabledServices.get(service.toLowerCase(Locale.ROOT)));
    }

    @Data
    public static class IpWhitelist {
        private boolean enabled = true;
        /** PKFARE source IPs / CIDRs. Empty rejects every caller while the check is enabled. */
        private List<String> addresses = new ArrayList<>();
    }

    @Data
    public static class MessageLog {
        private boolean enabled = true;
        /** With {@code enabled} off, still log the PKFARE request/response of failed requests. */
        private boolean prodErrorMonitoring = false;
        private List<String> maskElements = new ArrayList<>();
        private List<String> maskAttributes = new ArrayList<>();
    }
}
