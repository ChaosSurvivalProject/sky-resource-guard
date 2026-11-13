/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

import dev.skyguard.api.Contracts.ActionIntent;
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
import java.util.concurrent.CompletableFuture;

/**
 * Native SPI for a cooperative resource participant (task book 8.1).
 *
 * <p>Implementations MUST be honest about their capabilities. SKY never scans
 * all threads and interrupts them: only executors explicitly registered by the
 * participant and accepting the interruption contract are shut down.</p>
 *
 * <p>Contract summary:</p>
 * <ul>
 *   <li>{@code quiesce}: atomically reject new work, finish or safely cancel
 *       in-flight work, checkpoint, flush, close the participant's OWN
 *       executors/connections/listeners, then report QUIESCED / TIMEOUT / FAILED.</li>
 *   <li>{@code resume}: validate lease and generation, restore connections and
 *       caches first, then scheduling, then player commands; every step must be
 *       retryable and idempotent.</li>
 * </ul>
 */
public interface ResourceParticipant {

    ParticipantMetadata metadata();

    CapabilitySet capabilities();

    /** Read-only, bounded sampling. Must not perform world writes or blocking I/O. */
    HealthSnapshot sample(SampleBudget budget);

    /** Cooperative, short-term power reduction. The participant may decline. */
    boolean requestLowerPower(PowerLevel level, Lease lease);

    CompletableFuture<CheckpointReceipt> checkpoint(CheckpointRequest request);

    CompletableFuture<QuiesceReceipt> quiesce(QuiesceRequest request);

    CompletableFuture<ResumeReceipt> resume(ResumeToken token);

    /** Release only resources the participant itself owns. */
    CompletableFuture<ReleaseReceipt> releaseForeignResources(ReleaseRequest request);

    /** Optional hook: an adapter may inspect a dry-run intent before approval. */
    default void onActionIntent(ActionIntent intent) {
        // no-op by default
    }
}
