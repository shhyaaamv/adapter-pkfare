package com.verteil.thirdparties.adapter.pkfare.client.cache;

/**
 * Immutable snapshot of an Entry-Gate access token. {@code expiresAt} is epoch seconds (an absolute instant).
 * Client-credentials tokens have no refresh token; a new one is simply requested when this one runs out.
 */
public record EntryGateSession(String accessToken, long expiresAt) {

    static EntryGateSession issuedAt(String accessToken, long ttlSeconds, long now) {
        return new EntryGateSession(accessToken, now + ttlSeconds);
    }

    boolean isValid(long nowEpochSeconds, long safetyBufferSeconds) {
        return nowEpochSeconds < expiresAt - safetyBufferSeconds;
    }

    @Override
    public String toString() {
        return "EntryGateSession[expiresAt=" + expiresAt + ", token=<redacted>]";
    }
}
