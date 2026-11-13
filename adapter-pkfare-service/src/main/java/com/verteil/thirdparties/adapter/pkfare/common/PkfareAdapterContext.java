package com.verteil.thirdparties.adapter.pkfare.common;

import com.verteil.chain.Context;
import com.verteil.thirdparties.adapter.pkfare.offer.common.data.OfferData;

import java.util.HashMap;
import java.util.Map;

/**
 * Chain context of one PKFARE request. Data moves between commands only through these typed accessors. Entry-Gate
 * requests and responses are keyed by the Entry-Gate service name (e.g. {@code AirShopping}).
 */
public class PkfareAdapterContext extends HashMap<String, Object> implements Context {
    private static final String KEY_SERVICE = "$.service";
    private static final String KEY_REQUEST = "$.request";
    private static final String KEY_RESPONSE = "$.response";
    private static final String KEY_TXN_ID = "$.txnId";
    private static final String KEY_VENDOR_CODE = "$.vendorCode";
    private static final String KEY_CLIENT_IP = "$.clientIp";
    private static final String KEY_PROVIDER_API_SERVICE = "$.providerApiService";
    private static final String KEY_PROVIDER_API_REQUESTS = "$.providerApiRequests";
    private static final String KEY_PROVIDER_API_RESPONSES = "$.providerApiResponses";
    private static final String KEY_TIMEOUT_MILLIS = "$.timeoutMillis";
    private static final String KEY_CACHE_KEY = "$.cacheKey";
    private static final String KEY_CACHE_HIT = "$.cacheHit";
    private static final String KEY_OFFER_DATA = "$.offerData";

    /** PKFARE service, e.g. {@code Search}. */
    public String getService() {
        return (String) get(KEY_SERVICE);
    }

    public void setService(String service) {
        put(KEY_SERVICE, service);
    }

    /** PKFARE request. */
    public Object getRequest() {
        return get(KEY_REQUEST);
    }

    public void setRequest(Object request) {
        put(KEY_REQUEST, request);
    }

    /** PKFARE response. */
    public Object getResponse() {
        return get(KEY_RESPONSE);
    }

    public void setResponse(Object response) {
        put(KEY_RESPONSE, response);
    }

    public String getTxnId() {
        return (String) get(KEY_TXN_ID);
    }

    public void setTxnId(String txnId) {
        put(KEY_TXN_ID, txnId);
    }

    public String getVendorCode() {
        return (String) get(KEY_VENDOR_CODE);
    }

    public void setVendorCode(String vendorCode) {
        put(KEY_VENDOR_CODE, vendorCode);
    }

    public String getClientIp() {
        return (String) get(KEY_CLIENT_IP);
    }

    public void setClientIp(String clientIp) {
        put(KEY_CLIENT_IP, clientIp);
    }

    /** Entry-Gate service the next {@code InvokeEntryGateCmd} calls, e.g. {@code AirShopping}. */
    public String getProviderApiService() {
        return (String) get(KEY_PROVIDER_API_SERVICE);
    }

    public void setProviderApiService(String providerApiService) {
        put(KEY_PROVIDER_API_SERVICE, providerApiService);
    }

    public Object getProviderApiRequest(String providerApiService) {
        return providerMap(KEY_PROVIDER_API_REQUESTS).get(providerApiService);
    }

    public void setProviderApiRequest(String providerApiService, Object request) {
        providerMap(KEY_PROVIDER_API_REQUESTS).put(providerApiService, request);
    }

    public Object getProviderApiResponse(String providerApiService) {
        return providerMap(KEY_PROVIDER_API_RESPONSES).get(providerApiService);
    }

    public void setProviderApiResponse(String providerApiService, Object response) {
        providerMap(KEY_PROVIDER_API_RESPONSES).put(providerApiService, response);
    }

    /** Effective Entry-Gate timeout for this request, already capped by the PKFARE buyer cutoff. */
    public Integer getTimeoutMillis() {
        return (Integer) get(KEY_TIMEOUT_MILLIS);
    }

    public void setTimeoutMillis(Integer timeoutMillis) {
        put(KEY_TIMEOUT_MILLIS, timeoutMillis);
    }

    public String getCacheKey() {
        return (String) get(KEY_CACHE_KEY);
    }

    public void setCacheKey(String cacheKey) {
        put(KEY_CACHE_KEY, cacheKey);
    }

    /** The decoded {@code routing.data} of the inbound request. */
    public OfferData getOfferData() {
        return (OfferData) get(KEY_OFFER_DATA);
    }

    public void setOfferData(OfferData offerData) {
        put(KEY_OFFER_DATA, offerData);
    }

    public boolean isCacheHit() {
        return Boolean.TRUE.equals(get(KEY_CACHE_HIT));
    }

    public void setCacheHit(boolean cacheHit) {
        put(KEY_CACHE_HIT, cacheHit);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> providerMap(String key) {
        return (Map<String, Object>) computeIfAbsent(key, _ -> new HashMap<String, Object>());
    }
}
