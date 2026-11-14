/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

import java.util.Arrays;

/**
 * Fixed-capacity ring buffer for low-overhead telemetry. Bounded memory, O(1)
 * append, no allocation on the hot path except when a snapshot is requested.
 */
public final class MetricsRingBuffer {

    private final double[] values;
    private int cursor;
    private int size;
    private double sum;

    public MetricsRingBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.values = new double[capacity];
    }

    public synchronized void add(double value) {
        if (size == values.length) {
            sum -= values[cursor];
        } else {
            size++;
        }
        values[cursor] = value;
        sum += value;
        cursor = (cursor + 1) % values.length;
        // Recompute the running sum once per full lap. Repeated += / -= on a
        // long-running server accumulates floating-point drift; a full lap
        // touches every slot anyway, so this is bounded and cheap.
        if (cursor == 0) {
            double recomputed = 0.0;
            for (double v : values) {
                recomputed += v;
            }
            sum = recomputed;
        }
    }

    public synchronized int size() {
        return size;
    }

    public int capacity() {
        return values.length;
    }

    /** Oldest-to-newest copy. Safe to call from a background reader. */
    public synchronized double[] snapshot() {
        double[] out = new double[size];
        int start = (cursor - size + values.length) % values.length;
        for (int i = 0; i < size; i++) {
            out[i] = values[(start + i) % values.length];
        }
        return out;
    }

    public synchronized double mean() {
        return size == 0 ? 0.0 : sum / size;
    }

    /** Nearest-rank percentile. {@code p} is in [0, 1]. */
    public synchronized double percentile(double p) {
        if (size == 0) {
            return 0.0;
        }
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("p must be in [0,1]");
        }
        double[] sorted = snapshot();
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(p * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(rank, sorted.length - 1))];
    }

    /**
     * Maximum over the <em>live window</em> only. Walking indexes 0..size-1
     * happens to work for a never-cleared buffer but is not the ring's window
     * semantics; starting at -Infinity also keeps negative-valued metrics
     * correct instead of clamping them to 0.
     */
    public synchronized double max() {
        if (size == 0) {
            return 0.0;
        }
        double m = Double.NEGATIVE_INFINITY;
        int start = (cursor - size + values.length) % values.length;
        for (int i = 0; i < size; i++) {
            m = Math.max(m, values[(start + i) % values.length]);
        }
        return m;
    }

    public synchronized void clear() {
        Arrays.fill(values, 0.0);
        cursor = 0;
        size = 0;
        sum = 0.0;
    }
}
