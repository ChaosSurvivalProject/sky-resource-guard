/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skyguard.api.GuardState;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Task book 15.1: state-machine boundaries and hysteresis. */
class GuardStateMachineTest {

    private static final Instant T = Instant.parse("2026-11-13T00:00:00Z");

    @Test
    void escalationRequiresSustainedViolationAndOnlyOneLevelAtATime() {
        GuardStateMachine machine = new GuardStateMachine(StateThresholds.initialCandidates());
        assertEquals(GuardState.NORMAL, machine.observe(PressureSample.critical(), T));
        assertEquals(GuardState.NORMAL, machine.observe(PressureSample.critical(), T.plusSeconds(1)));
        assertEquals(GuardState.WATCH, machine.observe(PressureSample.critical(), T.plusSeconds(2)));
        assertEquals(GuardState.WATCH, machine.observe(PressureSample.critical(), T.plusSeconds(3)));
    }

    @Test
    void exitNeedsLowerThresholdAndLongerCalmThanEntry() {
        GuardStateMachine machine = new GuardStateMachine(StateThresholds.initialCandidates());
        for (int i = 0; i < 3; i++) {
            machine.observe(PressureSample.critical(), T.plusSeconds(i));
        }
        assertEquals(GuardState.WATCH, machine.state());

        // "Warm but not calm enough" must NOT count towards exit.
        PressureSample warm = new PressureSample(35.0, 45.0, 19.0, 0, 0, 0, 2, 20, 0.5);
        for (int i = 0; i < 20; i++) {
            machine.observe(warm, T.plusSeconds(10 + i));
        }
        assertEquals(GuardState.WATCH, machine.state());

        for (int i = 0; i < 11; i++) {
            machine.observe(PressureSample.calm(), T.plusSeconds(40 + i));
        }
        assertEquals(GuardState.WATCH, machine.state());
        assertEquals(GuardState.RECOVERY, machine.observe(PressureSample.calm(), T.plusSeconds(52)));

        for (int i = 0; i < 12; i++) {
            machine.observe(PressureSample.calm(), T.plusSeconds(60 + i));
        }
        assertEquals(GuardState.NORMAL, machine.state());
    }

    @Test
    void lockoutStopsAutomaticActionsAndRequiresHumanReset() {
        GuardStateMachine machine = new GuardStateMachine(StateThresholds.initialCandidates());
        machine.lockout("injected rollback failure", T);
        assertEquals(GuardState.LOCKOUT, machine.state());
        assertFalse(machine.requestActionSlot(T));
        assertEquals(GuardState.LOCKOUT, machine.observe(PressureSample.critical(), T.plusSeconds(1)));
        assertTrue(machine.history().stream().anyMatch(s -> s.contains("LOCKOUT")));
        machine.manualResetToNormal(T.plusSeconds(2));
        assertEquals(GuardState.NORMAL, machine.state());
        assertTrue(machine.requestActionSlot(T.plusSeconds(2)));
    }

    @Test
    void onlyOneMajorActionAtATimeWithCooldown() {
        GuardStateMachine machine = new GuardStateMachine(StateThresholds.initialCandidates());
        assertTrue(machine.requestActionSlot(T));
        machine.markActionStarted(T);
        assertFalse(machine.requestActionSlot(T.plusSeconds(1)));
        machine.markActionFinished();
        assertFalse(machine.requestActionSlot(T.plusSeconds(1)), "cooldown must be respected");
        assertTrue(machine.requestActionSlot(T.plusSeconds(6)));
    }

    @Test
    void stagedRecoveryIsManualAndOneStepAtATime() {
        GuardStateMachine machine = new GuardStateMachine(StateThresholds.initialCandidates());
        assertFalse(machine.recoverByOneStep(T));
        for (int i = 0; i < 9; i++) {
            machine.observe(PressureSample.critical(), T.plusSeconds(i));
        }
        for (int i = 0; i < 12; i++) {
            machine.observe(PressureSample.calm(), T.plusSeconds(20 + i));
        }
        assertEquals(GuardState.RECOVERY, machine.state());
        // Recovery is STAGED: one manual step must not restore everything at once.
        int steps = 0;
        while (machine.state() == GuardState.RECOVERY && steps < 10) {
            assertTrue(machine.recoverByOneStep(T.plusSeconds(40 + steps)));
            steps++;
        }
        assertEquals(GuardState.NORMAL, machine.state());
        assertTrue(steps >= 2, "CRITICAL must not recover to NORMAL in a single step");
    }

    @Test
    void historyIsBoundedAndEvictionIsObservable() {
        GuardStateMachine machine = new GuardStateMachine(StateThresholds.initialCandidates(), 3);
        assertEquals(3, machine.historyLimit());
        assertEquals(0, machine.historyDropped());
        // Each lockout + manual reset writes two transition records: 20 total.
        for (int i = 0; i < 10; i++) {
            machine.lockout("reason-" + i, T.plusSeconds(i));
            machine.manualResetToNormal(T.plusSeconds(i));
        }
        List<String> retained = machine.history();
        assertEquals(3, retained.size(), "history must not grow without bound");
        assertTrue(machine.historyDropped() > 0, "truncation must be observable, never silent");
        assertTrue(retained.stream().anyMatch(s -> s.contains("reason-9")),
                "the most recent transition must survive");
        assertFalse(retained.stream().anyMatch(s -> s.contains("reason-0")),
                "the oldest transition must be evicted");
    }

    @Test
    void historyLimitMustBePositive() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new GuardStateMachine(StateThresholds.initialCandidates(), 0));
    }

    @Test
    void tryBeginActionReservesTheSlotAtomically() {
        GuardStateMachine machine = new GuardStateMachine(StateThresholds.initialCandidates());
        assertTrue(machine.tryBeginAction(T));
        assertFalse(machine.requestActionSlot(T), "the slot must already be marked in-flight");
        assertFalse(machine.tryBeginAction(T), "no second caller may win the same slot");
        machine.markActionFinished();
        assertFalse(machine.tryBeginAction(T.plusSeconds(1)), "cooldown still applies");
        assertTrue(machine.tryBeginAction(T.plusSeconds(6)));
    }
}
