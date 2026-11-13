/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

import dev.skyguard.api.ServiceTier;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Cooperative budget arbitration (task book 9.2). The real scheduling unit is
 * a "task with a budget", not a Java thread priority. Each service tier owns a
 * separate token bucket, so a non-critical plugin cannot consume the CORE
 * floor budget. Thread priority, if used at all, is only a hint and never
 * participates in a correctness argument.
 */
public final class BudgetArbiter {

    public record Quota(double capacity, double refillPerSecond, double floor) {}

    private static final class Bucket {
        private final Quota quota;
        private double tokens;
        private long lastRefillMs = -1L;

        private Bucket(Quota quota) {
            this.quota = quota;
            this.tokens = quota.capacity();
        }
    }

    private static final Map<ServiceTier, Quota> DEFAULTS = new EnumMap<>(ServiceTier.class);

    static {
        DEFAULTS.put(ServiceTier.CORE, new Quota(2000, 2000, 1000));
        DEFAULTS.put(ServiceTier.PROTECTION, new Quota(1000, 800, 0));
        DEFAULTS.put(ServiceTier.PLAYER_SERVICE, new Quota(1000, 600, 0));
        DEFAULTS.put(ServiceTier.BACKGROUND, new Quota(400, 200, 0));
        DEFAULTS.put(ServiceTier.OPTIONAL, new Quota(100, 50, 0));
    }

    private final Map<ServiceTier, Bucket> buckets = new EnumMap<>(ServiceTier.class);

    public BudgetArbiter() {
        for (Map.Entry<ServiceTier, Quota> e : DEFAULTS.entrySet()) {
            buckets.put(e.getKey(), new Bucket(e.getValue()));
        }
    }

    public synchronized boolean tryAcquire(ServiceTier tier, double tokens, Instant now) {
        if (tokens <= 0) {
            return true;
        }
        Bucket bucket = buckets.get(tier);
        refill(bucket, now);
        if (bucket.tokens < tokens) {
            return false;
        }
        bucket.tokens -= tokens;
        return true;
    }

    public synchronized double available(ServiceTier tier, Instant now) {
        Bucket bucket = buckets.get(tier);
        refill(bucket, now);
        return bucket.tokens;
    }

    private void refill(Bucket bucket, Instant now) {
        long nowMs = now.toEpochMilli();
        if (bucket.lastRefillMs < 0) {
            bucket.lastRefillMs = nowMs;
            return;
        }
        long elapsedMs = Math.max(0, nowMs - bucket.lastRefillMs);
        bucket.tokens = Math.min(bucket.quota.capacity(),
                bucket.tokens + (elapsedMs / 1000.0) * bucket.quota.refillPerSecond());
        bucket.tokens = Math.max(bucket.tokens, bucket.quota.floor());
        bucket.lastRefillMs = nowMs;
    }
}
