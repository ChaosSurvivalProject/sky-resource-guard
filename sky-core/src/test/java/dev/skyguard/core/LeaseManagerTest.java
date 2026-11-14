/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skyguard.api.ActionKind;
import dev.skyguard.api.Contracts.ActionIntent;
import dev.skyguard.api.Contracts.Lease;
import dev.skyguard.testkit.Intents;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Expired leases must be recoverable, not silently discarded. */
class LeaseManagerTest {

    private static final Instant T = Instant.parse("2026-11-14T00:00:00Z");

    private static ActionIntent shortLived() {
        ActionIntent base = Intents.valid(ActionKind.SPAWN_BUDGET);
        return new ActionIntent(base.intentId(), base.kind(), base.target(),
                base.initiator(), base.heldPermissions(), base.oldValue(), base.newValue(),
                Duration.ofSeconds(1), base.sideEffects(), base.evidence(), base.evidenceRefs(),
                false, false, base.rollbackAction());
    }

    private static ActionIntent withTarget(ActionIntent base, String target, String id) {
        return new ActionIntent(id, base.kind(), target, base.initiator(),
                base.heldPermissions(), base.oldValue(), base.newValue(), base.ttl(),
                base.sideEffects(), base.evidence(), base.evidenceRefs(),
                false, false, base.rollbackAction());
    }

    @Test
    void drainExpiredReturnsTheLeaseSoOldStateCanBeRestored() {
        LeaseManager manager = new LeaseManager();
        Lease lease = manager.acquire(shortLived(), T).orElseThrow();

        assertTrue(manager.drainExpired(T).isEmpty(), "lease is still valid at issue time");
        List<Lease> drained = manager.drainExpired(T.plusSeconds(2));
        assertEquals(1, drained.size());
        Lease expired = drained.get(0);
        assertEquals(lease.leaseId(), expired.leaseId());
        assertEquals("10", expired.oldValue(), "old state must be available for restore");
        assertEquals("8", expired.newValue());
        assertEquals("restore:simulation-distance=10", expired.rollbackAction());
        assertTrue(expired.isExpired(T.plusSeconds(2)));
        assertEquals(0, manager.activeCount(T.plusSeconds(2)));
        assertTrue(manager.drainExpired(T.plusSeconds(2)).isEmpty(), "drain is idempotent");
    }

    @Test
    void drainExpiredReturnsEveryExpiredLeaseInInsertionOrder() {
        LeaseManager manager = new LeaseManager();
        ActionIntent first = shortLived();
        Lease a = manager.acquire(first, T).orElseThrow();
        Lease b = manager.acquire(withTarget(first, "world:nether", "intent-second"), T).orElseThrow();

        List<Lease> drained = manager.drainExpired(T.plusSeconds(2));
        assertEquals(2, drained.size());
        assertEquals(a.leaseId(), drained.get(0).leaseId());
        assertEquals(b.leaseId(), drained.get(1).leaseId());
        assertEquals(0, manager.activeCount(T.plusSeconds(2)));
        assertEquals(0, manager.expire(T.plusSeconds(2)), "nothing left to purge");
    }
}
