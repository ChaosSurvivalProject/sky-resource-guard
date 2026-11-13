/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.platform.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skyguard.platform.paper.CapabilityProbe.ProbeRequest;
import org.junit.jupiter.api.Test;

/** Task book 2.2: opt-in, version-bounded, fail-closed capability probes. */
class ReflectionProbeTest {

    @Test
    void disabledProbeFailsClosedToReadOnlyFallback() {
        var probe = new ReflectionProbe(false, "1.21.11");
        var result = probe.probe(new ProbeRequest("feature-a", "1.21.11", true));
        assertFalse(result.available());
        assertTrue(result.fallbackUsed());
        assertTrue(result.detail().contains("fallback"));
    }

    @Test
    void versionMismatchFailsClosed() {
        var probe = new ReflectionProbe(true, "1.21.11");
        var result = probe.probe(new ProbeRequest("feature-a", "1.21.4", true));
        assertFalse(result.available());
        assertTrue(result.detail().contains("version range mismatch"));
    }

    @Test
    void absentServerApiIsReportedAsUnavailableNotInvented() {
        // No Bukkit artifact on the test classpath: a truthful probe must fail closed.
        var probe = new ReflectionProbe(true, "1.21.11");
        var result = probe.probe(new ProbeRequest("feature-a", "1.21.11", true));
        assertFalse(result.available());
        assertTrue(result.detail().contains("failed closed"));
    }
}
