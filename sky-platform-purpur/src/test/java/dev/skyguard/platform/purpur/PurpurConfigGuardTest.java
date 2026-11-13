/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.platform.purpur;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skyguard.platform.paper.CapabilityProbe.ProbeRequest;
import dev.skyguard.platform.paper.ReflectionProbe;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Task book 2.2 / 5.4: unknown keys are reported, never written. */
class PurpurConfigGuardTest {

    @Test
    void everyKeyStartsUnverifiedBecauseNoBuildWasConfirmed() {
        var guard = new PurpurConfigGuard();
        assertEquals(0, guard.verifiedKeyCount());
        var report = guard.inspect("purpur.some-unknown-key", "true");
        assertFalse(report.verified());
        assertFalse(guard.proposeWrite("purpur.some-unknown-key", "true").allowed());
        assertEquals(List.of("purpur.some-unknown-key"), guard.reportedUnknownKeys());
    }

    @Test
    void anUnresolvedBuildCannotRegisterAVerifiedKey() {
        var guard = new PurpurConfigGuard();
        assertThrows(IllegalArgumentException.class,
                () -> guard.registerVerifiedKey("key", "UNRESOLVED"));
        assertThrows(IllegalArgumentException.class,
                () -> guard.registerVerifiedKey("key", ""));
    }

    @Test
    void evenAVerifiedKeyIsNotWrittenInP0() {
        var guard = new PurpurConfigGuard();
        guard.registerVerifiedKey("purpur.known", "purpur-1.21.11-build-1234");
        assertTrue(guard.inspect("purpur.known", "8").verified());
        assertFalse(guard.proposeWrite("purpur.known", "8").allowed());
    }

    @Test
    void purpurProbeNeverClaimsAvailability() {
        var probe = new PurpurCapabilityProbe(new ReflectionProbe(true, "1.21.11"));
        for (String feature : probe.trackedFeatures()) {
            var result = probe.probe(new ProbeRequest(feature, "1.21.11", true));
            assertFalse(result.available());
            assertTrue(result.detail().contains("UNRESOLVED"));
            assertTrue(result.fallbackUsed());
        }
    }
}
