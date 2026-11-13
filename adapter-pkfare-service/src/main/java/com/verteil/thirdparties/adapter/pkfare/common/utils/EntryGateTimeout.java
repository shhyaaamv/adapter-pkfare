package com.verteil.thirdparties.adapter.pkfare.common.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * Entry-Gate call timeout for one request.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EntryGateTimeout {

    private static final long MIN_TIMEOUT_MILLIS = 1000L;

    /**
     * {@code timeoutMillis}, capped by PKFARE's {@code cutOffTime} minus {@code cutoffBufferMillis} (never below 1 s):
     * an answer arriving after the buyer's cutoff is discarded by PKFARE anyway. A missing or non-positive cutoff
     * leaves {@code timeoutMillis}.
     */
    public static int resolve(long timeoutMillis, long cutoffBufferMillis, Integer cutOffTime) {
        long cappedTimeout = Optional.ofNullable(cutOffTime)
                .filter(cutOff -> cutOff > 0)
                .map(cutOff -> Math.min(timeoutMillis, Math.max(cutOff - cutoffBufferMillis, MIN_TIMEOUT_MILLIS)))
                .orElse(timeoutMillis);
        return Math.toIntExact(cappedTimeout);
    }
}
