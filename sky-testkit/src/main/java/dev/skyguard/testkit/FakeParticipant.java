/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.testkit;

import dev.skyguard.api.Capability;
import dev.skyguard.api.CapabilitySet;
import dev.skyguard.api.Contracts.CheckpointReceipt;
import dev.skyguard.api.Contracts.CheckpointRequest;
import dev.skyguard.api.Contracts.HealthSnapshot;
import dev.skyguard.api.Contracts.Lease;
import dev.skyguard.api.Contracts.ParticipantMetadata;
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
import dev.skyguard.api.SideEffect;
import dev.skyguard.api.Urgency;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Controllable participant for fault injection (task book 15.3). */
public final class FakeParticipant implements ResourceParticipant {

    public enum Failure {
        NONE,
        /** quiesce returns a future that never completes. */
        QUIESCE_NEVER_RETURNS,
        RESUME_BREAKS,
        TIMEOUT
    }

    private final String id;
    private final CapabilitySet capabilities;
    private Failure failure = Failure.NONE;
    private long generation;
    private volatile boolean quiesced;
    private volatile int resumeCalls;
    private volatile boolean resourcesReleased;

    public FakeParticipant(String id) {
        this(id, CapabilitySet.of(Capability.values()));
    }

    public FakeParticipant(String id, CapabilitySet capabilities) {
        this.id = id;
        this.capabilities = capabilities;
    }

    public void setFailure(Failure failure) { this.failure = failure; }
    public boolean isQuiesced() { return quiesced; }
    public int resumeCalls() { return resumeCalls; }
    public boolean resourcesReleased() { return resourcesReleased; }

    @Override
    public ParticipantMetadata metadata() {
        return new ParticipantMetadata(id, "1.0.0-test", List.of(), ServiceTier.BACKGROUND,
                capabilities, "test-fault-domain", Instant.EPOCH);
    }

    @Override
    public CapabilitySet capabilities() {
        return capabilities;
    }

    @Override
    public HealthSnapshot sample(SampleBudget budget) {
        return new HealthSnapshot(id, Instant.EPOCH, ExecutionPhase.BACKGROUND_IO,
                Urgency.NORMAL, 0, 1, 1024L, 1_000L, "idle", true,
                Set.<SideEffect>of(), List.of());
    }

    @Override
    public boolean requestLowerPower(PowerLevel level, Lease lease) {
        return capabilities.has(Capability.LOWER_POWER);
    }

    @Override
    public CompletableFuture<CheckpointReceipt> checkpoint(CheckpointRequest request) {
        return CompletableFuture.completedFuture(new CheckpointReceipt(
                id, request.generation(), "cp-" + request.idempotencyKey(), true, "ok"));
    }

    @Override
    public CompletableFuture<QuiesceReceipt> quiesce(QuiesceRequest request) {
        if (failure == Failure.QUIESCE_NEVER_RETURNS) {
            return new CompletableFuture<>();
        }
        if (failure == Failure.TIMEOUT) {
            return CompletableFuture.completedFuture(new QuiesceReceipt(
                    id, dev.skyguard.api.Contracts.QuiesceHandle.Outcome.TIMEOUT, "injected timeout"));
        }
        if (request.dryRun()) {
            return CompletableFuture.completedFuture(new QuiesceReceipt(
                    id, dev.skyguard.api.Contracts.QuiesceHandle.Outcome.DRY_RUN, "dry-run only"));
        }
        quiesced = true;
        return CompletableFuture.completedFuture(new QuiesceReceipt(
                id, dev.skyguard.api.Contracts.QuiesceHandle.Outcome.QUIESCED, "closed own executor"));
    }

    @Override
    public CompletableFuture<ResumeReceipt> resume(ResumeToken token) {
        int calls = ++resumeCalls;
        if (failure == Failure.RESUME_BREAKS) {
            return CompletableFuture.failedFuture(new IllegalStateException("injected resume break"));
        }
        // Idempotent: a repeated token/generation is a replay, never a double write.
        boolean replay = calls > 1 && token.generation() == generation;
        generation = token.generation();
        quiesced = false;
        return CompletableFuture.completedFuture(new ResumeReceipt(
                id, token.generation(), true, replay, replay ? "idempotent replay" : "resumed"));
    }

    @Override
    public CompletableFuture<ReleaseReceipt> releaseForeignResources(ReleaseRequest request) {
        resourcesReleased = true;
        return CompletableFuture.completedFuture(new ReleaseReceipt(id, 1, true, "closed own handles"));
    }
}
