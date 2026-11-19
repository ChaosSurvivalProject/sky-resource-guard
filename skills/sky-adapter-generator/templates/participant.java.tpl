/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 *
 * GENERATED FILE - DO NOT EDIT BY HAND.
 * Spec      : {{SPEC_FILE}}
 * Generator : skills/sky-adapter-generator/scripts/generate_adapter.py
 */
package {{PACKAGE}};

import dev.skyguard.api.Capability;
import dev.skyguard.api.CapabilitySet;
import dev.skyguard.api.Contracts.CheckpointReceipt;
import dev.skyguard.api.Contracts.CheckpointRequest;
import dev.skyguard.api.Contracts.HealthSnapshot;
import dev.skyguard.api.Contracts.Lease;
import dev.skyguard.api.Contracts.ParticipantMetadata;
import dev.skyguard.api.Contracts.QuiesceHandle;
import dev.skyguard.api.Contracts.QuiesceReceipt;
import dev.skyguard.api.Contracts.QuiesceRequest;
import dev.skyguard.api.Contracts.ReleaseReceipt;
import dev.skyguard.api.Contracts.ReleaseRequest;
import dev.skyguard.api.Contracts.ResumeReceipt;
import dev.skyguard.api.Contracts.ResumeToken;
import dev.skyguard.api.Contracts.SampleBudget;
import dev.skyguard.api.ExecutionPhase;
import dev.skyguard.api.PowerLevel;
import dev.skyguard.api.ResourceParticipant;
import dev.skyguard.api.ServiceTier;
import dev.skyguard.api.Urgency;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generated OBSERVE_ONLY adapter for {{PLUGIN_NAME}} ({{PLUGIN_ID}} {{VERSION}}).
 *
 * <p>This adapter reports read-only health facts and DECLINES every mutating
 * lifecycle operation. It never touches another plugin's threads, executors,
 * world state or configuration. Capabilities are exactly those declared in the
 * spec - no capability is inferred or inflated.</p>
 *
 * <p>Discovered automatically by SkyPlugin through java.util.ServiceLoader;
 * no plugin source change is needed to "adapt" this plugin.</p>
 */
public final class {{CLASS}} implements ResourceParticipant {

    private static final String ID = "{{PLUGIN_ID}}";
    private static final String VERSION = "{{VERSION}}";

    private final AtomicInteger queueDepth = new AtomicInteger({{QUEUE}});
    private final Set<String> seenResumeKeys = ConcurrentHashMap.newKeySet();
    private volatile boolean healthy = true;

    @Override
    public ParticipantMetadata metadata() {
        return new ParticipantMetadata(ID, VERSION, List.of({{DEPS}}), ServiceTier.{{TIER}},
                capabilities(), "{{FAULT}}", Instant.EPOCH);
    }

    @Override
    public CapabilitySet capabilities() {
        return CapabilitySet.of({{CAPS}});
    }

    @Override
    public HealthSnapshot sample(SampleBudget budget) {
        // Read-only, bounded, no blocking I/O and no player-identifying data.
        return new HealthSnapshot(ID, Instant.EPOCH, ExecutionPhase.{{PHASE}},
                Urgency.DEFERABLE, queueDepth.get(), 1, {{HEAP}}L, 1_000L,
                "{{STAGE}}", healthy, Set.of(), List.of());
    }

    @Override
    public boolean requestLowerPower(PowerLevel level, Lease lease) {
        // OBSERVE_ONLY: never change another plugin's behaviour.
        return false;
    }

    @Override
    public CompletableFuture<CheckpointReceipt> checkpoint(CheckpointRequest request) {
        return CompletableFuture.completedFuture(new CheckpointReceipt(
                ID, request.generation(), "none", false,
                "observe-only adapter does not checkpoint"));
    }

    @Override
    public CompletableFuture<QuiesceReceipt> quiesce(QuiesceRequest request) {
        QuiesceHandle.Outcome outcome = request.dryRun()
                ? QuiesceHandle.Outcome.DRY_RUN : QuiesceHandle.Outcome.FAILED;
        return CompletableFuture.completedFuture(new QuiesceReceipt(
                ID, outcome, "observe-only adapter never quiesces; dry-run is advisory only"));
    }

    @Override
    public CompletableFuture<ResumeReceipt> resume(ResumeToken token) {
        boolean replay = !seenResumeKeys.add(token.idempotencyKey() + "#" + token.generation());
        return CompletableFuture.completedFuture(new ResumeReceipt(
                ID, token.generation(), false, replay,
                "observe-only adapter has no quiesced state"));
    }

    @Override
    public CompletableFuture<ReleaseReceipt> releaseForeignResources(ReleaseRequest request) {
        return CompletableFuture.completedFuture(new ReleaseReceipt(
                ID, 0, true, "observe-only adapter owns no foreign resources"));
    }

    /** Package-private test hook: simulates a reported queue depth. */
    void setReportedQueueDepth(int depth) {
        queueDepth.set(Math.max(0, depth));
    }
}
