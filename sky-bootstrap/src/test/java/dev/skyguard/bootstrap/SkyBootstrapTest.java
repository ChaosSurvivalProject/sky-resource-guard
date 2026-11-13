/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skyguard.api.RunMode;
import org.junit.jupiter.api.Test;

class SkyBootstrapTest {

    @Test
    void startupIsCompatibleWithJava21ButReportsUnresolvedFacts() {
        var report = SkyBootstrap.check();
        assertTrue(report.compatible());
        assertFalse(report.unresolved().isEmpty());
        assertTrue(report.checks().stream().anyMatch(c -> c.contains("java.feature=21")));
    }

    @Test
    void firstInstallIsShadowAndReadOnly() {
        var report = SkyBootstrap.check();
        assertTrue(report.mode() == RunMode.SHADOW);
        assertTrue(report.checks().contains("mode=SHADOW (first install default)"));
        assertTrue(report.checks().contains("unknownPluginPolicy=OBSERVE_ONLY"));
        assertTrue(report.note().contains("No server plugin JAR"));
    }

    @Test
    void assemblyDoesNotNeedAServer() {
        assertNotNull(SkyBootstrap.assemble());
        assertTrue(SkyBootstrap.assemble().stateMachine().state()
                == dev.skyguard.api.GuardState.NORMAL);
    }
}
