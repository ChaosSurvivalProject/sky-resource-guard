/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.benchmarks;

import dev.skyguard.core.MetricsRingBuffer;
import dev.skyguard.testkit.TickReplay;
import java.util.List;

/**
 * Fixed-seed, pure-Java replay (task book 15.4).
 *
 * <p><strong>This is NOT a server benchmark and proves no performance gain.</strong>
 * It only exercises the metrics code deterministically. A real benchmark needs
 * a pinned Purpur build, a real world, baseline/shadow/protect runs, P50/P95/P99
 * with confidence intervals and a failure rate - see docs/benchmark.md.</p>
 */
public final class DeterministicReplay {

    private DeterministicReplay() {}

    public static void main(String[] args) {
        System.out.print(report(42L, 1200));
    }

    public static String report(long seed, int ticks) {
        StringBuilder sb = new StringBuilder();
        sb.append("SKY deterministic replay (PURE JAVA, NOT A SERVER BENCHMARK)\n");
        sb.append("seed=").append(seed).append(" ticks=").append(ticks).append('\n');

        for (String mode : List.of("baseline", "shadow", "protect")) {
            List<Double> series = TickReplay.msptSeries(seed, ticks, 16.0, mode.equals("protect") ? 0 : 40);
            MetricsRingBuffer buffer = new MetricsRingBuffer(1024);
            series.forEach(buffer::add);
            sb.append(String.format("%-9s samples=%d p50=%.2f p95=%.2f p99=%.2f max=%.2f%n",
                    mode, buffer.size(), buffer.percentile(0.50), buffer.percentile(0.95),
                    buffer.percentile(0.99), buffer.max()));
        }
        sb.append("NOTE: no TPS, MSPT or latency improvement is claimed here.\n");
        sb.append("Robot/telemetry behaviour is not a substitute for player experience.\n");
        return sb.toString();
    }
}
