package io.github.hackermanme.flashapi.ratelimit;

import io.github.hackermanme.flashapi.registry.EntityMetadata;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory sliding-window rate limiter per entity per client IP.
 * Thread-safe, zero allocation on the hot path after warmup.
 */
public final class FlashRateLimiter {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public boolean isAllowed(EntityMetadata meta, String clientIp) {
        if (!meta.rateLimitEnabled()) return true;
        String key = meta.path() + ":" + clientIp;
        TokenBucket bucket = buckets.computeIfAbsent(key,
                k -> new TokenBucket(meta.rateLimitRequests(), meta.rateLimitWindow()));
        return bucket.tryConsume();
    }

    public int getRemainingRequests(EntityMetadata meta, String clientIp) {
        if (!meta.rateLimitEnabled()) return -1;
        String key = meta.path() + ":" + clientIp;
        TokenBucket bucket = buckets.get(key);
        if (bucket == null) return meta.rateLimitRequests();
        return bucket.remaining();
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }

    private static final class TokenBucket {
        private final int maxTokens;
        private final long windowMillis;
        private final AtomicLong tokens;
        private volatile long windowStart;

        TokenBucket(int maxTokens, int windowSeconds) {
            this.maxTokens = maxTokens;
            this.windowMillis = windowSeconds * 1000L;
            this.tokens = new AtomicLong(maxTokens);
            this.windowStart = System.currentTimeMillis();
        }

        boolean tryConsume() {
            refillIfNeeded();
            long current = tokens.get();
            while (current > 0) {
                if (tokens.compareAndSet(current, current - 1)) {
                    return true;
                }
                current = tokens.get();
            }
            return false;
        }

        int remaining() {
            refillIfNeeded();
            return (int) Math.max(0, tokens.get());
        }

        boolean isExpired(long now) {
            return now - windowStart > windowMillis * 2;
        }

        private void refillIfNeeded() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMillis) {
                windowStart = now;
                tokens.set(maxTokens);
            }
        }
    }
}
