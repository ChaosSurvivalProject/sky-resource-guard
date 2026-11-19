/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The installable plugin must fail closed to a read-only baseline. */
class PluginConfigTest {

    @Test
    void defaultsAreReadOnly() {
        PluginConfig cfg = PluginConfig.defaults();
        assertTrue(cfg.isReadOnly());
        assertTrue(cfg.violations().isEmpty());
        assertEquals("SHADOW", cfg.mode());
        assertFalse(cfg.automationEnabled());
        assertFalse(cfg.aiEnabled());
        assertFalse(cfg.aiAllowExecute());
    }

    @Test
    void strongerModeIsRejected() {
        PluginConfig cfg = new PluginConfig(
                "PROTECT", false, true, "OBSERVE_ONLY", false, false, 20, 16);
        assertFalse(cfg.isReadOnly());
        assertTrue(cfg.violations().stream().anyMatch(s -> s.contains("mode must be SHADOW")));
    }

    @Test
    void automationAndAiAreRejected() {
        PluginConfig cfg = new PluginConfig(
                "SHADOW", true, true, "OBSERVE_ONLY", true, true, 20, 16);
        assertFalse(cfg.isReadOnly());
        assertEquals(3, cfg.violations().size());
        assertTrue(cfg.violations().stream().anyMatch(s -> s.contains("automation-enabled")));
        assertTrue(cfg.violations().stream().anyMatch(s -> s.contains("ai.enabled")));
        assertTrue(cfg.violations().stream().anyMatch(s -> s.contains("ai.allow-execute")));
    }

    @Test
    void hotLoopAndUnboundedSamplingAreRejected() {
        PluginConfig hot = new PluginConfig(
                "SHADOW", false, true, "OBSERVE_ONLY", false, false, 1, 16);
        assertTrue(hot.violations().stream().anyMatch(s -> s.contains("interval-ticks")));
        PluginConfig unbounded = new PluginConfig(
                "SHADOW", false, true, "OBSERVE_ONLY", false, false, 20, 1000);
        assertTrue(unbounded.violations().stream().anyMatch(s -> s.contains("max-samples-per-cycle")));
    }

    @Test
    void unknownPluginPolicyMustStayObserveOnly() {
        PluginConfig cfg = new PluginConfig(
                "SHADOW", false, true, "AUTO_LIMIT", false, false, 20, 16);
        assertFalse(cfg.isReadOnly());
        assertTrue(cfg.violations().stream().anyMatch(s -> s.contains("OBSERVE_ONLY")));
    }

    @Test
    void invalidBoundsThrowEarly() {
        assertThrows(IllegalArgumentException.class, () -> new PluginConfig(
                "SHADOW", false, true, "OBSERVE_ONLY", false, false, 0, 16));
        assertThrows(NullPointerException.class, () -> new PluginConfig(
                null, false, true, "OBSERVE_ONLY", false, false, 20, 16));
    }
}
