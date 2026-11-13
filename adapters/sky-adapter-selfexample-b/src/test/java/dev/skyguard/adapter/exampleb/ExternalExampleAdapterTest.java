/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.adapter.exampleb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skyguard.api.Contracts.Lease;
import dev.skyguard.api.Contracts.PluginRuntimeSnapshot;
import dev.skyguard.api.Contracts.QuiesceRequest;
import dev.skyguard.api.Contracts.Deadline;
import dev.skyguard.api.Contracts.ResumeToken;
import dev.skyguard.api.Contracts.SampleBudget;
import dev.skyguard.api.EvidenceLevel;
import dev.skyguard.api.PowerLevel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Task book 5.5: public API only, no forced thread stop, independently versioned. */
class ExternalExampleAdapterTest {

    @Test
    void adapterIsNeverEnabledByDefault() {
        assertFalse(new ExternalExampleAdapter().metadata().enabledByDefault());
    }

    @Test
    void missingPublicApiKeepsReadOnly() {
        var adapter = new ExternalExampleAdapter();
        var snapshot = new PluginRuntimeSnapshot("example-inventory-plugin", true,
                List.of("worker-1"), Map.of(), false, 3);
        var report = adapter.probe(snapshot);
        assertFalse(report.compatible());
        assertEquals(EvidenceLevel.L1_STRONG, report.evidence());
        assertFalse(report.missingApis().isEmpty());
        assertTrue(adapter.quiesce(new QuiesceRequest("example-inventory-plugin", "test",
                new Deadline(Instant.now().plusSeconds(5), Duration.ofSeconds(1)), false)).isEmpty());
        assertTrue(adapter.checkpoint(new Deadline(Instant.now().plusSeconds(5),
                Duration.ofSeconds(1))).isEmpty());
    }

    @Test
    void lowerPowerOnlyUsesADocumentedCommand() {
        var adapter = new ExternalExampleAdapter();
        Instant now = Instant.now();
        var lease = new Lease("lease-1", "example-inventory-plugin", "intent-1", "admin",
                "FULL", "LOW", now, Duration.ofSeconds(30),
                "verify", "restore FULL", "checksum", List.of());
        var handle = adapter.requestLowerPower(PowerLevel.LOW, lease);
        assertTrue(handle.isPresent());
        assertTrue(handle.get().accepted());
        assertTrue(handle.get().detail().contains("documented command"));
        assertTrue(adapter.lowerPowerRequested());
    }

    @Test
    void expiredLeaseIsRefused() {
        var adapter = new ExternalExampleAdapter();
        Instant old = Instant.now().minusSeconds(120);
        var lease = new Lease("lease-old", "example-inventory-plugin", "intent-1", "admin",
                "FULL", "LOW", old, Duration.ofSeconds(30),
                "verify", "restore FULL", "checksum", List.of());
        assertTrue(adapter.requestLowerPower(PowerLevel.LOW, lease).isEmpty());
    }

    @Test
    void resumeIsIdempotent() {
        var adapter = new ExternalExampleAdapter();
        var token = new ResumeToken("example-inventory-plugin", 2, "lease-1", "key-1");
        assertFalse(adapter.resume(token).idempotentReplay());
        assertTrue(adapter.resume(token).idempotentReplay());
    }

    @Test
    void samplingRespectsTheBudget() {
        var adapter = new ExternalExampleAdapter();
        assertTrue(adapter.sample(new SampleBudget(0, Duration.ofMillis(1), false)).isEmpty());
        assertTrue(adapter.sample(SampleBudget.strict()).isPresent());
    }
}
