/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

import dev.skyguard.api.GuardState;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Guard state machine (task book 6.1):
 *
 * <pre>
 * NORMAL -&gt; WATCH -&gt; DEGRADE -&gt; CRITICAL -&gt; RECOVERY -&gt; NORMAL
 *                       \          /
 *                         LOCKOUT
 * </pre>
 *
 * <p>Properties: escalation needs sustained violation; de-escalation uses a
 * lower threshold and a longer duration; only one major action at a time;
 * cooldown between actions; recovery is staged, never all-at-once; LOCKOUT
 * stops further automatic action but keeps {@link #manualResetToNormal}.</p>
 *
 * <p>Time is passed in explicitly, so the whole machine is deterministic and
 * testable without a clock, a server or a scheduler.</p>
 */
public final class GuardStateMachine {

    private final StateThresholds thresholds;
    private final List<String> history = new ArrayList<>();

    private GuardState state = GuardState.NORMAL;
    private int violationStreak;
    private int calmStreak;
    private int recoveryLevel;
    private Instant lastActionAt;
    private boolean actionInFlight;
    private String lockoutReason;

    public GuardStateMachine(StateThresholds thresholds) {
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds");
    }

    public synchronized GuardState state() {
        return state;
    }

    public synchronized String lockoutReason() {
        return lockoutReason;
    }

    public synchronized List<String> history() {
        return List.copyOf(history);
    }

    /** Feed one observation window. Returns the resulting state. */
    public synchronized GuardState observe(PressureSample sample, Instant now) {
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(now, "now");
        if (state == GuardState.LOCKOUT) {
            return state;
        }
        if (state == GuardState.RECOVERY) {
            return observeRecovery(sample, now);
        }

        int current = rank(state);
        int pressure = pressureLevel(sample);

        if (pressure > current) {
            violationStreak++;
            calmStreak = 0;
            if (violationStreak >= thresholds.enterViolations()) {
                state = fromRank(Math.min(current + 1, 3));
                note(now, "escalate -> " + state + " (p95=" + sample.msptP95Ms()
                        + ", p99=" + sample.msptP99Ms() + ", sustained=" + violationStreak + ")");
                violationStreak = 0;
            }
        } else if (pressure == 0 && current > 0 && isCalmEnoughToExit(sample)) {
            calmStreak++;
            violationStreak = 0;
            if (calmStreak >= thresholds.exitViolations()) {
                state = GuardState.RECOVERY;
                recoveryLevel = current;
                note(now, "enter RECOVERY with " + recoveryLevel + " staged step(s)");
                calmStreak = 0;
            }
        } else {
            violationStreak = 0;
            calmStreak = 0;
        }
        return state;
    }

    private GuardState observeRecovery(PressureSample sample, Instant now) {
        int pressure = pressureLevel(sample);
        if (pressure > 0) {
            // Relapse: leave staged recovery, but still one level at a time.
            calmStreak = 0;
            state = fromRank(Math.max(1, pressure));
            recoveryLevel = 0;
            note(now, "recovery relapse -> " + state);
            return state;
        }
        if (!isCalmEnoughToExit(sample)) {
            calmStreak = 0;
            return state;
        }
        calmStreak++;
        if (calmStreak >= thresholds.exitViolations()) {
            recoveryLevel--;
            calmStreak = 0;
            if (recoveryLevel <= 0) {
                state = GuardState.NORMAL;
                note(now, "recovery complete -> NORMAL");
            } else {
                note(now, "recovery step, " + recoveryLevel + " step(s) remaining");
            }
        }
        return state;
    }

    /** Manually advance exactly one staged recovery step (admin/CLI path). */
    public synchronized boolean recoverByOneStep(Instant now) {
        if (state != GuardState.RECOVERY) {
            return false;
        }
        recoveryLevel--;
        if (recoveryLevel <= 0) {
            state = GuardState.NORMAL;
        }
        note(now, "manual recovery step, remaining=" + Math.max(0, recoveryLevel));
        return true;
    }

    /** Whether one major automatic action may be issued right now. */
    public synchronized boolean requestActionSlot(Instant now) {
        if (!state.allowsAutomaticAction() || actionInFlight) {
            return false;
        }
        if (lastActionAt == null) {
            return true;
        }
        return !Duration.between(lastActionAt, now).minus(thresholds.actionCooldown()).isNegative();
    }

    public synchronized void markActionStarted(Instant now) {
        actionInFlight = true;
        lastActionAt = now;
    }

    public synchronized void markActionFinished() {
        actionInFlight = false;
    }

    public synchronized void lockout(String reason, Instant now) {
        state = GuardState.LOCKOUT;
        lockoutReason = reason;
        actionInFlight = false;
        note(now, "LOCKOUT: " + reason + " (human recovery required)");
    }

    /** Human recovery entry point from LOCKOUT. Never called automatically. */
    public synchronized void manualResetToNormal(Instant now) {
        state = GuardState.NORMAL;
        lockoutReason = null;
        actionInFlight = false;
        violationStreak = 0;
        calmStreak = 0;
        recoveryLevel = 0;
        note(now, "manual reset -> NORMAL");
    }

    /** Exit threshold is LOWER than the watch threshold: this is the hysteresis. */
    private boolean isCalmEnoughToExit(PressureSample s) {
        return s.msptP95Ms() < thresholds.exitMsptP95Ms()
                && s.consecutiveTicksOver50Ms() == 0
                && s.consecutiveTicksOver100Ms() == 0;
    }

    private int pressureLevel(PressureSample s) {
        if (s.consecutiveTicksOver100Ms() >= thresholds.enterViolations()
                || s.msptP99Ms() >= thresholds.criticalTickMs()) {
            return 3;
        }
        if (s.consecutiveTicksOver50Ms() >= thresholds.enterViolations()
                || s.msptP99Ms() >= thresholds.degradeMsptP99Ms()) {
            return 2;
        }
        if (s.msptP95Ms() >= thresholds.watchMsptP95Ms()) {
            return 1;
        }
        return 0;
    }

    private static int rank(GuardState s) {
        return switch (s) {
            case NORMAL -> 0;
            case WATCH -> 1;
            case DEGRADE, RECOVERY -> 2;
            case CRITICAL -> 3;
            case LOCKOUT -> 4;
        };
    }

    private static GuardState fromRank(int r) {
        return switch (r) {
            case 1 -> GuardState.WATCH;
            case 2 -> GuardState.DEGRADE;
            case 3 -> GuardState.CRITICAL;
            default -> GuardState.NORMAL;
        };
    }

    private void note(Instant now, String message) {
        history.add(now + " " + message);
    }
}
