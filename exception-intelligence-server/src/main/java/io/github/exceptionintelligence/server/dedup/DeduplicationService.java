package io.github.exceptionintelligence.server.dedup;

import io.github.exceptionintelligence.server.config.ServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Deduplication service that tracks seen exception fingerprints.
 * Backed by in-memory LRU cache (default) or Redis (when configured).
 */
public class DeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(DeduplicationService.class);
    private static final int MAX_MEMORY_ENTRIES = 10_000;

    private final ServerProperties.DeduplicationProperties config;
    private final Map<String, Instant> memoryStore;
    private final StringRedisTemplate redisTemplate;

    /** Constructor for memory-backed deduplication. */
    public DeduplicationService(ServerProperties.DeduplicationProperties config) {
        this(config, null);
    }

    /** Constructor for Redis-backed deduplication. */
    public DeduplicationService(ServerProperties.DeduplicationProperties config,
                                 StringRedisTemplate redisTemplate) {
        this.config = config;
        this.redisTemplate = redisTemplate;
        this.memoryStore = new LinkedHashMap<>(MAX_MEMORY_ENTRIES, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Instant> eldest) {
                return size() > MAX_MEMORY_ENTRIES;
            }
        };
    }

    /**
     * Checks if a fingerprint was already processed within the TTL window.
     * If not, marks it as seen and returns {@code false}.
     *
     * @return {@code true} if duplicate (skip processing), {@code false} if new
     */
    public synchronized boolean checkAndMark(String fingerprint) {
        if (!config.isEnabled()) return false;

        if ("redis".equalsIgnoreCase(config.getStore()) && redisTemplate != null) {
            return checkAndMarkRedis(fingerprint);
        }
        return checkAndMarkMemory(fingerprint);
    }

    private boolean checkAndMarkRedis(String fingerprint) {
        String key = "exc-intel:dedup:" + fingerprint;
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofMinutes(config.getTtlMinutes()));
        return isNew == null || !isNew;
    }

    private boolean checkAndMarkMemory(String fingerprint) {
        Instant now = Instant.now();
        Instant seenAt = memoryStore.get(fingerprint);

        if (seenAt != null) {
            long ageMinutes = Duration.between(seenAt, now).toMinutes();
            if (ageMinutes < config.getTtlMinutes()) {
                return true;
            }
        }

        memoryStore.put(fingerprint, now);
        return false;
    }
}
