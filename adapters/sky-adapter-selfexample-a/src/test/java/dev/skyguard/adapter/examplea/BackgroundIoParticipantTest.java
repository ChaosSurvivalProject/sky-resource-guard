/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.adapter.examplea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skyguard.api.Contracts.Deadline;
import dev.skyguard.api.Contracts.QuiesceHandle;
import dev.skyguard.api.Contracts.QuiesceRequest;
import dev.skyguard.api.Contracts.ResumeToken;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Task book 4.3 / 15.3: own executor, deadline, idempotent resume, no double write. */
class BackgroundIoParticipantTest {

    @Test
    void dryRunQuiesceDoesNotChangeState() {
        var participant = new BackgroundIoParticipant();
        var request = new QuiesceRequest("example-background-io", "test",
                new Deadline(Instant.now().plusSeconds(5), Duration.ofSeconds(1)), true);
        assertEquals(QuiesceHandle.Outcome.DRY_RUN, participant.quiesce(request).join().outcome());
        assertTrue(participant.commandsOpen());
    }

    @Test
    void quiesceClosesOwnExecutorAndRejectsNewWork() {
        var participant = new BackgroundIoParticipant();
        var request = new QuiesceRequest("example-background-io", "test",
                new Deadline(Instant.now().plusSeconds(5), Duration.ofSeconds(1)), false);
        assertEquals(QuiesceHandle.Outcome.QUIESCED, participant.quiesce(request).join().outcome());
        assertFalse(participant.commandsOpen());
        assertFalse(participant.sample(dev.skyguard.api.Contracts.SampleBudget.strict()).healthy());
    }

    @Test
    void resumeIsIdempotentAndNeverDoubleWrites() {
        var participant = new BackgroundIoParticipant();
        var token = new ResumeToken("example-background-io", 7, "lease-1", "key-1");
        var first = participant.resume(token).join();
        var replay = participant.resume(token).join();
        assertTrue(first.ok());
        assertFalse(first.idempotentReplay());
        assertTrue(replay.idempotentReplay());
        assertTrue(participant.commandsOpen());
    }

    @Test
    void checkpointDoesNotHoldALockDuringIo() {
        var participant = new BackgroundIoParticipant();
        var request = new dev.skyguard.api.Contracts.CheckpointRequest(
                "example-background-io", "key-1", 1,
                new Deadline(Instant.now().plusSeconds(5), Duration.ofSeconds(1)));
        assertTrue(participant.checkpoint(request).join().ok());
    }

    @Test
    void releaseClosesOwnResources() {
        var participant = new BackgroundIoParticipant();
        var request = new dev.skyguard.api.Contracts.ReleaseRequest(
                "example-background-io", java.util.Set.of("executor", "listeners"),
                new Deadline(Instant.now().plusSeconds(5), Duration.ofSeconds(1)));
        var receipt = participant.releaseForeignResources(request).join();
        assertTrue(receipt.ok());
        assertTrue(receipt.releasedCount() > 0);
        assertFalse(participant.commandsOpen());
    }
}
