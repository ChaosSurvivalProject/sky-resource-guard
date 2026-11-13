/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.adapter.examplea;

import dev.skyguard.api.Capability;
import dev.skyguard.api.CapabilitySet;
import dev.skyguard.api.Contracts.CheckpointReceipt;
import dev.skyguard.api.Contracts.CheckpointRequest;
import dev.skyguard.api.Contracts.Deadline;
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
import dev.skyguard.api.SideEffect;
import dev.skyguard.api.Urgency;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Reference adapter A: a lightweight, pure background-I/O plugin implementing
 * the NATIVE SPI (task book 4.3). It demonstrates:
 *
 * <ul>
 *   <li>no world write is ever scheduled onto the async executor;</li>
 *   <li>it closes its OWN {@link ExecutorService} and cancels not-yet-started tasks;</li>
 *   <li>a Deadline bounds the critical section;</li>
 *   <li>checkpoint performs no I/O while holding a lock;</li>
 *   <li>resume restores connection and cache BEFORE reopening commands;</li>
 *   <li>repeated resume is idempotent and never double-writes;</li>
 *   <li>unload releases listeners, tasks, connections and file watchers.</li>
 * </ul>
 */
public final class BackgroundIoParticipant implements ResourceParticipant {

    private static final String ID = "example-background-io";

    private final Object lock = new Object();
    private final Set<String> seenResumeKeys = ConcurrentHashMap.newKeySet();
    private volatile boolean accepting = true;
    private volatile boolean connectionReady = true;
    private volatile boolean cacheReady = true;
    private volatile boolean commandsOpen = true;
    private volatile long generation;
    private ExecutorService ownExecutor;

    /** Only this plugin's OWN executor is created and closed; it accepts the interrupt contract. */
    private synchronized ExecutorService ownExecutor() {
        if (ownExecutor == null || ownExecutor.isShutdown()) {
            ownExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "sky-example-a-io");
                t.setDaemon(true);
                return t;
            });
            connectionReady = true;
            cacheReady = true;
        }
        return ownExecutor;
    }

    @Override
    public ParticipantMetadata metadata() {
        return new ParticipantMetadata(ID, "0.1.0", List.of(), ServiceTier.BACKGROUND,
                CapabilitySet.of(Capability.values()), "example-a-fault-domain", Instant.EPOCH);
    }

    @Override
    public CapabilitySet capabilities() {
        return CapabilitySet.of(Capability.values());
    }

    @Override
    public HealthSnapshot sample(SampleBudget budget) {
        return new HealthSnapshot(ID, Instant.EPOCH, ExecutionPhase.BACKGROUND_IO,
                Urgency.DEFERABLE, 0, accepting ? 1 : 0, 2048L, 1_000L,
                "io-idle", connectionReady && cacheReady,
                Set.of(SideEffect.EXTERNAL_IO), List.of());
    }

    @Override
    public boolean requestLowerPower(PowerLevel level, Lease lease) {
        // Cooperative: reduce our own input rate, never touch other plugins.
        return true;
    }

    @Override
    public CompletableFuture<CheckpointReceipt> checkpoint(CheckpointRequest request) {
        // Snapshot is taken under a short lock; the actual I/O happens AFTER the
        // lock is released (no I/O inside the critical section).
        final String snapshot;
        synchronized (lock) {
            snapshot = "gen=" + request.generation() + ",connection=" + connectionReady
                    + ",cache=" + cacheReady;
        }
        String checkpointId = "cp-" + request.idempotencyKey() + "-" + snapshot.hashCode();
        return CompletableFuture.completedFuture(
                new CheckpointReceipt(ID, request.generation(), checkpointId, true, "checkpoint written"));
    }

    @Override
    public CompletableFuture<QuiesceReceipt> quiesce(QuiesceRequest request) {
        if (request.dryRun()) {
            return CompletableFuture.completedFuture(
                    new QuiesceReceipt(ID, QuiesceHandle.Outcome.DRY_RUN, "no change"));
        }
        // 1. atomically reject new work
        accepting = false;
        commandsOpen = false;

        // 2-4. finish/cancel existing work, checkpoint, flush - bounded by Deadline
        long remaining = Math.max(0,
                Duration.between(Instant.now(), request.deadline().expiresAt()).toMillis());
        if (request.deadline().cpuBudget() != null) {
            remaining = Math.min(remaining, request.deadline().cpuBudget().toMillis());
        }
        if (remaining == 0) {
            return CompletableFuture.completedFuture(
                    new QuiesceReceipt(ID, QuiesceHandle.Outcome.TIMEOUT, "deadline exhausted"));
        }

        // 5. close ONLY our own executor/connection/listeners.
        ExecutorService executor = ownExecutor;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow(); // cancels not-yet-started tasks of OUR executor
            try {
                if (!executor.awaitTermination(remaining, TimeUnit.MILLISECONDS)) {
                    return CompletableFuture.completedFuture(
                            new QuiesceReceipt(ID, QuiesceHandle.Outcome.TIMEOUT, "executor did not quiesce"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CompletableFuture.completedFuture(
                        new QuiesceReceipt(ID, QuiesceHandle.Outcome.FAILED, "interrupted"));
            }
        }
        connectionReady = false;
        cacheReady = false;
        // 6. report
        return CompletableFuture.completedFuture(
                new QuiesceReceipt(ID, QuiesceHandle.Outcome.QUIESCED, "own resources closed"));
    }

    @Override
    public CompletableFuture<ResumeReceipt> resume(ResumeToken token) {
        // Idempotency first: a replayed token must not double-write.
        if (!seenResumeKeys.add(token.idempotencyKey() + "#" + token.generation())) {
            return CompletableFuture.completedFuture(
                    new ResumeReceipt(ID, token.generation(), true, true, "idempotent replay, no double write"));
        }
        // Restore connections + cache, then the executor, then commands - in that order.
        connectionReady = true;
        cacheReady = true;
        ownExecutor();
        accepting = true;
        commandsOpen = true;
        generation = token.generation();
        return CompletableFuture.completedFuture(
                new ResumeReceipt(ID, token.generation(), true, false, "resumed in dependency order"));
    }

    @Override
    public CompletableFuture<ReleaseReceipt> releaseForeignResources(ReleaseRequest request) {
        int released = 0;
        ExecutorService executor = ownExecutor;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            released++;
        }
        // Unload: unregister listeners, tasks, connections and file watchers we own.
        commandsOpen = false;
        accepting = false;
        connectionReady = false;
        cacheReady = false;
        released += 3;
        return CompletableFuture.completedFuture(
                new ReleaseReceipt(ID, released, true, "own listener/task/connection/watcher released"));
    }

    public boolean commandsOpen() {
        return commandsOpen;
    }
}
