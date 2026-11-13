/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

import java.time.Duration;
import java.util.Objects;

/**
 * <strong>Initial candidate thresholds only</strong> (task book 6.1). These are
 * NOT universal defaults and MUST be calibrated on the target hardware before
 * PROTECT/AUTOMATE is enabled.
 *
 * <p>Hysteresis is structural: {@code exitMsptP95Ms} is lower than
 * {@code watchMsptP95Ms}, and {@code exitViolations} is larger than
 * {@code enterViolations}. Entering a high-pressure state therefore needs
 * sustained violation, while leaving needs a longer calm period.</p>
 */
public record StateThresholds(
        double watchMsptP95Ms,
        double degradeMsptP99Ms,
        double criticalTickMs,
        double exitMsptP95Ms,
        int enterViolations,
        int exitViolations,
        Duration actionCooldown,
        int maxRecoveryStepsPerCycle) {

    public StateThresholds {
        Objects.requireNonNull(actionCooldown, "actionCooldown");
        if (enterViolations < 1 || exitViolations < 1) {
            throw new IllegalArgumentException("violation counts must be >= 1");
        }
        if (exitViolations <= enterViolations) {
            throw new IllegalArgumentException("exitViolations must be > enterViolations (hysteresis)");
        }
        if (exitMsptP95Ms > watchMsptP95Ms) {
            throw new IllegalArgumentException("exit threshold must be lower than watch threshold");
        }
        if (maxRecoveryStepsPerCycle < 1) {
            throw new IllegalArgumentException("recovery must be staged");
        }
    }

    public static StateThresholds initialCandidates() {
        return new StateThresholds(
                40.0, 50.0, 100.0, 30.0,
                3, 12,
                Duration.ofSeconds(5), 1);
    }
}
