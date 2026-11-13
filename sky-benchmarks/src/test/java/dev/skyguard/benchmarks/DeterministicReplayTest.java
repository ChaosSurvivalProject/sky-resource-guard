/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.benchmarks;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeterministicReplayTest {

    @Test
    void reportIsDeterministicAndMakesNoPerformanceClaim() {
        String a = DeterministicReplay.report(42L, 300);
        String b = DeterministicReplay.report(42L, 300);
        assertTrue(a.equals(b), "fixed seed must be reproducible");
        assertTrue(a.contains("NOT A SERVER BENCHMARK"));
        assertTrue(a.contains("baseline"));
        assertTrue(a.contains("no TPS, MSPT or latency improvement is claimed"));
    }
}
