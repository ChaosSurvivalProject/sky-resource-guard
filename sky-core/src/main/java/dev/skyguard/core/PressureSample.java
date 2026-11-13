/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

/** One observation window fed into the guard state machine (task book 6.1). */
public record PressureSample(
        double msptP95Ms,
        double msptP99Ms,
        double weightedTps,
        int consecutiveTicksOver50Ms,
        int consecutiveTicksOver100Ms,
        long gcPauseMs,
        int networkQueueDepth,
        int onlinePlayers,
        double entityDensity) {

    public static PressureSample calm() {
        return new PressureSample(12.0, 18.0, 20.0, 0, 0, 0, 0, 20, 0.4);
    }

    public static PressureSample critical() {
        return new PressureSample(85.0, 130.0, 8.0, 12, 9, 240, 180, 90, 4.5);
    }
}
