package com.verteil.thirdparties.adapter.pkfare.common;

import com.verteil.thirdparties.adapter.pkfare.common.model.CabinMapping;
import com.verteil.thirdparties.connector.common.chain.ConnectorContext;

public class PkfareAdapterContext extends ConnectorContext {
    protected static final String KEY_VENDOR_CODE = "$.vendorCode";
    protected static final String KEY_CLIENT_IP = "$.clientIp";
    protected static final String KEY_TIMEOUT_MILLIS = "$.timeoutMillis";
    protected static final String KEY_CACHE_KEY = "$.cacheKey";
    protected static final String KEY_CACHE_HIT = "$.cacheHit";
    protected static final String KEY_CABIN_MAPPING = "$.cabinMapping";

    public String getVendorCode() {
        return (String) context.get(KEY_VENDOR_CODE);
    }

    public void setVendorCode(String vendorCode) {
        context.put(KEY_VENDOR_CODE, vendorCode);
    }

    public String getClientIp() {
        return (String) context.get(KEY_CLIENT_IP);
    }

    public void setClientIp(String clientIp) {
        context.put(KEY_CLIENT_IP, clientIp);
    }

    /** Effective Entry-Gate timeout for this request, already capped by the PKFARE buyer cutoff. */
    public Integer getTimeoutMillis() {
        return (Integer) context.get(KEY_TIMEOUT_MILLIS);
    }

    public void setTimeoutMillis(Integer timeoutMillis) {
        context.put(KEY_TIMEOUT_MILLIS, timeoutMillis);
    }

    public String getCacheKey() {
        return (String) context.get(KEY_CACHE_KEY);
    }

    public void setCacheKey(String cacheKey) {
        context.put(KEY_CACHE_KEY, cacheKey);
    }

    public CabinMapping getCabinMapping() {
        return (CabinMapping) context.get(KEY_CABIN_MAPPING);
    }

    public void setCabinMapping(CabinMapping cabinMapping) {
        context.put(KEY_CABIN_MAPPING, cabinMapping);
    }

    public boolean isCacheHit() {
        return Boolean.TRUE.equals(context.get(KEY_CACHE_HIT));
    }

    public void setCacheHit(boolean cacheHit) {
        context.put(KEY_CACHE_HIT, cacheHit);
    }
}
