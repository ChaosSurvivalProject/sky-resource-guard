/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 *
 * GENERATED FILE - DO NOT EDIT BY HAND.
 * Spec      : {{SPEC_FILE}}
 * Generator : skills/sky-adapter-generator/scripts/generate_adapter.py
 */
package {{PACKAGE}};

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skyguard.api.Capability;
import dev.skyguard.api.CapabilitySet;
import dev.skyguard.api.Contracts.CheckpointRequest;
import dev.skyguard.api.Contracts.Deadline;
import dev.skyguard.api.Contracts.QuiesceHandle;
import dev.skyguard.api.Contracts.QuiesceRequest;
import dev.skyguard.api.Contracts.ReleaseRequest;
import dev.skyguard.api.Contracts.ResumeReceipt;
import dev.skyguard.api.Contracts.ResumeToken;
import dev.skyguard.api.Contracts.SampleBudget;
import dev.skyguard.api.PowerLevel;
import dev.skyguard.api.ResourceParticipant;
import dev.skyguard.api.ServiceTier;
import java.time.Duration;
import java.time.Instant;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/** Contract tests for a generated OBSERVE_ONLY adapter. */
class {{CLASS}}Test {

    private final {{CLASS}} participant = new {{CLASS}}();
    private static final Instant T = Instant.parse("2026-11-20T00:00:00Z");

    private static Deadline deadline() {
        return new Deadline(T.plusSeconds(30), Duration.ofSeconds(1));
    }

    @Test
    void metadataMatchesTheSpec() {
        var m = participant.metadata();
        assertEquals("{{PLUGIN_ID}}", m.pluginId());
        assertEquals("{{VERSION}}", m.version());
        assertEquals(ServiceTier.{{TIER}}, m.serviceTier());
        assertEquals("{{FAULT}}", m.faultDomain());
        assertEquals({{N_DEPS}}, m.dependencies().size());
    }

    @Test
    void capabilitiesMatchSpecExactlyAndAreNotOverstated() {
        CapabilitySet caps = participant.capabilities();
        assertEquals({{N_CAPS}}, caps.size());
        assertTrue(caps.hasAll({{CAPS_ASSERT}}));
        assertFalse(caps.has(Capability.QUIESCE));
        assertFalse(caps.has(Capability.LOWER_POWER));
        assertFalse(caps.has(Capability.EXECUTOR_SHUTDOWN));
    }

    @Test
    void sampleIsReadOnlyAndCarriesNoSensitiveFields() {
        var s = participant.sample(SampleBudget.strict());
        assertEquals("{{PLUGIN_ID}}", s.pluginId());
        assertTrue(s.sideEffects().isEmpty());
        assertTrue(s.errors().isEmpty());
        assertEquals({{QUEUE}}, s.queueDepth());
        participant.setReportedQueueDepth(7);
        assertEquals(7, participant.sample(SampleBudget.strict()).queueDepth());
    }

    @Test
    void lowerPowerIsAlwaysDeclined() {
        // The generated implementation never reads the lease, so null is safe.
        assertFalse(participant.requestLowerPower(PowerLevel.LOW, null));
    }

    @Test
    void quiesceIsAdvisoryOnly() throws Exception {
        QuiesceRequest dry = new QuiesceRequest("{{PLUGIN_ID}}", "test", deadline(), true);
        assertEquals(QuiesceHandle.Outcome.DRY_RUN, participant.quiesce(dry).get().outcome());
        QuiesceRequest real = new QuiesceRequest("{{PLUGIN_ID}}", "test", deadline(), false);
        assertEquals(QuiesceHandle.Outcome.FAILED, participant.quiesce(real).get().outcome());
    }

    @Test
    void checkpointAndResumeNeverClaimSuccess() throws Exception {
        CheckpointRequest cp = new CheckpointRequest("{{PLUGIN_ID}}", "k1", 1L, deadline());
        assertFalse(participant.checkpoint(cp).get().ok());
        ResumeToken token = new ResumeToken("{{PLUGIN_ID}}", 1L, "lease-x", "k1");
        assertFalse(participant.resume(token).get().ok());
        ResumeReceipt replay = participant.resume(token).get();
        assertTrue(replay.idempotentReplay(), "a replayed token must never double-write");
    }

    @Test
    void releaseTouchesNothing() throws Exception {
        ReleaseRequest req = new ReleaseRequest("{{PLUGIN_ID}}", Set.of("cache"), deadline());
        assertEquals(0, participant.releaseForeignResources(req).get().releasedCount());
    }

    @Test
    void serviceLoaderDiscoversTheGeneratedAdapter() {
        ServiceLoader<ResourceParticipant> loader = ServiceLoader.load(ResourceParticipant.class);
        assertTrue(StreamSupport.stream(loader.spliterator(), false)
                        .anyMatch(p -> p instanceof {{CLASS}}),
                "META-INF/services must register the generated adapter for auto-adaptation");
    }
}
