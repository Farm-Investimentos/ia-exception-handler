package io.github.exceptionintelligence.sdk.ratelimit;

import io.github.exceptionintelligence.sdk.config.SdkProperties;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple token-bucket rate limiter that prevents flooding the server with events.
 */
public class RateLimiter {

    private final SdkProperties.RateLimitProperties config;
    private final AtomicInteger count = new AtomicInteger(0);
    private volatile Instant windowStart = Instant.now();

    public RateLimiter(SdkProperties.RateLimitProperties config) {
        this.config = config;
    }

    /**
     * Returns {@code true} if the event should be processed (under the rate limit).
     */
    public synchronized boolean tryConsume() {
        if (!config.isEnabled()) return true;

        Instant now = Instant.now();
        long secondsElapsed = now.getEpochSecond() - windowStart.getEpochSecond();

        if (secondsElapsed >= 60) {
            count.set(0);
            windowStart = now;
        }

        if (count.get() < config.getMaxEventsPerMinute()) {
            count.incrementAndGet();
            return true;
        }
        return false;
    }
}
