/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skyguard.api.EvidenceLevel;
import dev.skyguard.runtime.UnknownPluginPolicy.Mode;
import org.junit.jupiter.api.Test;

/** Task book 16.6: unknown plugins default to OBSERVE_ONLY, no forced thread stop. */
class UnknownPluginPolicyTest {

    @Test
    void defaultIsObserveOnly() {
        assertEquals(Mode.OBSERVE_ONLY, new UnknownPluginPolicy().defaultMode());
    }

    @Test
    void weakEvidenceNeverTriggersAutomaticRestriction() {
        var decision = new UnknownPluginPolicy().evaluate(
                "legacy-plugin", EvidenceLevel.L2_WEAK, true, true);
        assertEquals(Mode.OBSERVE_ONLY, decision.mode());
        assertFalse(decision.mayAutoRestrict());
        assertFalse(decision.mayForceThreadStop());
    }

    @Test
    void privilegedLookingNameDoesNotBypassPolicy() {
        var policy = new UnknownPluginPolicy();
        assertTrue(UnknownPluginPolicy.looksPrivileged("SkyAdminOpPlugin"));
        var decision = policy.evaluate("SkyAdminOpPlugin", EvidenceLevel.L3_GUESS, true, true);
        assertEquals(Mode.OBSERVE_ONLY, decision.mode());
        assertFalse(decision.mayAutoRestrict());
        assertFalse(policy.mayForceThreadStop());
        assertTrue(decision.reasons().stream().anyMatch(s -> s.contains("NOT a policy bypass")));
    }

    @Test
    void strongEvidenceWithoutAdapterStaysReadOnly() {
        var decision = new UnknownPluginPolicy().evaluate(
                "legacy-plugin", EvidenceLevel.L0_FACT, false, false);
        assertEquals(Mode.OBSERVE_ONLY, decision.mode());
        assertFalse(decision.mayAutoRestrict());
    }

    @Test
    void strongEvidenceWithAdapterAllowsOnlyCooperativeRestriction() {
        var decision = new UnknownPluginPolicy().evaluate(
                "legacy-plugin", EvidenceLevel.L1_STRONG, true, false);
        assertEquals(Mode.LIMITED, decision.mode());
        assertTrue(decision.mayAutoRestrict());
        assertFalse(decision.mayForceThreadStop());
    }

    @Test
    void adminDisableStillNeverForceStopsThreads() {
        var decision = new UnknownPluginPolicy().evaluate(
                "legacy-plugin", EvidenceLevel.L0_FACT, true, true);
        assertEquals(Mode.ADMIN_DISABLE, decision.mode());
        assertFalse(decision.mayForceThreadStop());
    }
}
