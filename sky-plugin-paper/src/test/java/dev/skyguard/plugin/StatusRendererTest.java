/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skyguard.api.Contracts.HealthSnapshot;
import dev.skyguard.api.ExecutionPhase;
import dev.skyguard.api.SideEffect;
import dev.skyguard.api.Urgency;
import dev.skyguard.core.GuardRuntime;
import dev.skyguard.runtime.ParticipantRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Status output must be honest: no invented numbers, no sensitive fields. */
class StatusRendererTest {

    private static final Instant T = Instant.parse("2026-11-20T00:00:00Z");

    @Test
    void readOnlyStatusStatesItsLimits() {
        String out = StatusRenderer.render(
                new GuardRuntime(), new ParticipantRegistry(),
                PlatformFacts.unresolved(), PluginConfig.defaults(), List.of(), T);
        assertTrue(out.contains("configReadOnly: true"));
        assertTrue(out.contains("mode: SHADOW"));
        assertTrue(out.contains("state: NORMAL"));
        assertTrue(out.contains("ai=false"));
        assertTrue(out.contains("NOT MEASURED"));
        assertTrue(out.contains("UNRESOLVED"));
        assertTrue(out.contains("verified=false"));
        assertTrue(out.contains("no world write"));
    }

    @Test
    void unsafeConfigIsVisibleInTheReport() {
        PluginConfig bad = new PluginConfig(
                "PROTECT", true, true, "OBSERVE_ONLY", false, false, 20, 16);
        String out = StatusRenderer.render(
                new GuardRuntime(), new ParticipantRegistry(),
                PlatformFacts.unresolved(), bad, List.of(), T);
        assertTrue(out.contains("configReadOnly: false"));
        assertTrue(out.contains("mode must be SHADOW"));
    }

    @Test
    void participantLinesCarryOnlyAggregateFields() {
        HealthSnapshot snap = new HealthSnapshot(
                "acme-quests", T, ExecutionPhase.BACKGROUND_IO, Urgency.DEFERABLE,
                3, 1, 2048L, 1000L, "idle", true,
                Set.of(SideEffect.EXTERNAL_IO), List.of());
        String out = StatusRenderer.render(
                new GuardRuntime(), new ParticipantRegistry(),
                new PlatformFacts("Purpur 1.21.11", "1.21.11-R0.1-SNAPSHOT", 12, 3, 8, true),
                PluginConfig.defaults(), List.of(snap), T);
        assertTrue(out.contains("acme-quests"));
        assertTrue(out.contains("queue=3"));
        assertTrue(out.contains("verified=true"));
        assertFalse(out.contains("player="), "aggregate counts only, never player names");
    }

    @Test
    void noPercentageOrTpsClaimIsEmitted() {
        String out = StatusRenderer.render(
                new GuardRuntime(), new ParticipantRegistry(),
                PlatformFacts.unresolved(), PluginConfig.defaults(), List.of(), T);
        assertFalse(out.contains("TPS improvement"));
        assertFalse(out.contains("P99 improvement"));
    }
}
