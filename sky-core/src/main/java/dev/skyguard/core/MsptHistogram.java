/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

/**
 * Integer-bucket MSPT histogram. Avoids storing every sample separately and
 * keeps the cost of P50/P95/P99 bounded by the bucket count, not by the number
 * of ticks. The last bucket is the overflow bucket.
 */
public final class MsptHistogram {

    private final long[] buckets;
    private final int maxBucketMs;
    private long count;

    public MsptHistogram(int maxBucketMs) {
        if (maxBucketMs < 1) {
            throw new IllegalArgumentException("maxBucketMs must be >= 1");
        }
        this.maxBucketMs = maxBucketMs;
        this.buckets = new long[maxBucketMs + 2];
    }

    public synchronized void record(double msptMs) {
        int bucket = msptMs < 0 ? 0 : (int) Math.floor(msptMs);
        if (bucket > maxBucketMs) {
            bucket = maxBucketMs + 1;
        }
        buckets[bucket]++;
        count++;
    }

    public synchronized long count() {
        return count;
    }

    public synchronized long overflow() {
        return buckets[maxBucketMs + 1];
    }

    /** Nearest-rank percentile, returned as the bucket's millisecond value. */
    public synchronized double percentile(double p) {
        if (count == 0) {
            return 0.0;
        }
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("p must be in [0,1]");
        }
        long target = (long) Math.ceil(p * count);
        long seen = 0;
        for (int i = 0; i < buckets.length; i++) {
            seen += buckets[i];
            if (seen >= target) {
                return i == maxBucketMs + 1 ? (double) maxBucketMs : (double) i;
            }
        }
        return maxBucketMs;
    }

    public synchronized void clear() {
        java.util.Arrays.fill(buckets, 0L);
        count = 0;
    }
}
