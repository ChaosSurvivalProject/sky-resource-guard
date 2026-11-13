/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skyguard.api.ActionKind;
import dev.skyguard.api.Contracts.ActionIntent;
import dev.skyguard.api.Contracts.ActionResult;
import dev.skyguard.api.ErrorCode;
import dev.skyguard.api.GuardState;
import dev.skyguard.api.SideEffect;
import dev.skyguard.api.EvidenceLevel;
import dev.skyguard.testkit.Intents;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Task book 15.1: gateway permissions, TTL, rollback and fail-safe. */
class ActionGatewayTest {

    private static final Instant T = Instant.parse("2026-11-13T00:00:00Z");

    private static final class Harness {
        final LeaseManager leases = new LeaseManager();
        final GuardStateMachine machine = new GuardStateMachine(StateThresholds.initialCandidates());
        final List<String> audit = new ArrayList<>();
        boolean verifyOk = true;
        boolean rollbackOk = true;
        int applyCalls;
        int rollbackCalls;
        final ActionGateway gateway = new ActionGateway(
                leases, machine,
                (intent, lease) -> { applyCalls++; return intent.newValue(); },
                (intent, lease, observed) -> verifyOk && observed.equals(intent.newValue()),
                (intent, lease) -> { rollbackCalls++; return rollbackOk; },
                audit::add);
    }

    @Test
    void validIntentIsAppliedVerifiedAndAudited() {
        Harness h = new Harness();
        ActionResult result = h.gateway.execute(Intents.valid(ActionKind.SIMULATION_DISTANCE), T);
        assertEquals(ErrorCode.OK, result.code());
        assertTrue(result.applied());
        assertTrue(result.verified());
        assertFalse(result.rolledBack());
        assertEquals(1, h.applyCalls);
        assertEquals(0, h.leases.activeCount(T));
        assertTrue(h.audit.stream().anyMatch(s -> s.startsWith("COMMITTED")));
    }

    @Test
    void dryRunNeverAppliesAndNeverAcquiresALease() {
        Harness h = new Harness();
        ActionIntent dryRun = Intents.with(Intents.valid(ActionKind.SPAWN_BUDGET),
                Intents.valid(ActionKind.SPAWN_BUDGET).heldPermissions(), true, false);
        ActionResult result = h.gateway.execute(dryRun, T);
        assertEquals(ErrorCode.DRY_RUN_ONLY, result.code());
        assertEquals(0, h.applyCalls);
        assertEquals(0, h.leases.activeCount(T));
        assertTrue(h.audit.stream().anyMatch(s -> s.startsWith("DRY-RUN")));
    }

    @Test
    void missingPermissionIsRejectedBeforeAnyEffect() {
        Harness h = new Harness();
        ActionIntent noPerms = Intents.with(Intents.valid(ActionKind.SPAWN_BUDGET),
                Set.of(), false, false);
        assertEquals(ErrorCode.PERMISSION_DENIED, h.gateway.execute(noPerms, T).code());
        assertEquals(0, h.applyCalls);
    }

    @Test
    void irreversibleActionIsNeverAutomatic() {
        Harness h = new Harness();
        ActionIntent base = Intents.valid(ActionKind.DROP_MERGE);
        ActionIntent irreversible = new ActionIntent(base.intentId(), base.kind(), base.target(),
                base.initiator(), base.heldPermissions(), base.oldValue(), base.newValue(),
                base.ttl(), Set.of(SideEffect.IRREVERSIBLE), base.evidence(), base.evidenceRefs(),
                false, true, base.rollbackAction());
        assertEquals(ErrorCode.UNAUTHORIZED_ACTION, h.gateway.execute(irreversible, T).code());
        assertEquals(0, h.applyCalls);
    }

    @Test
    void verificationFailureRollsBackAutomatically() {
        Harness h = new Harness();
        h.verifyOk = false;
        ActionResult result = h.gateway.execute(Intents.valid(ActionKind.SIMULATION_DISTANCE), T);
        assertEquals(ErrorCode.VERIFICATION_FAILED, result.code());
        assertTrue(result.rolledBack());
        assertEquals(1, h.rollbackCalls);
        assertEquals(0, h.leases.activeCount(T));
    }

    @Test
    void failedRollbackEntersLockout() {
        Harness h = new Harness();
        h.verifyOk = false;
        h.rollbackOk = false;
        ActionResult result = h.gateway.execute(Intents.valid(ActionKind.SIMULATION_DISTANCE), T);
        assertEquals(ErrorCode.ROLLBACK_FAILED, result.code());
        assertEquals(GuardState.LOCKOUT, h.machine.state());
        assertFalse(h.machine.requestActionSlot(T.plusSeconds(1)));
    }

    @Test
    void concurrentActionOnSameTargetIsRejected() {
        Harness h = new Harness();
        ActionIntent first = Intents.valid(ActionKind.SPAWN_BUDGET);
        h.leases.acquire(first, T); // an unrelated active lease on the same target
        assertEquals(ErrorCode.LEASE_CONFLICT, h.gateway.execute(first, T).code());
        assertEquals(0, h.applyCalls);
    }

    @Test
    void weakEvidenceStaysObserveOnly() {
        Harness h = new Harness();
        ActionIntent base = Intents.valid(ActionKind.SPAWN_BUDGET);
        ActionIntent weak = new ActionIntent(base.intentId(), base.kind(), base.target(),
                base.initiator(), base.heldPermissions(), base.oldValue(), base.newValue(),
                base.ttl(), base.sideEffects(), EvidenceLevel.L2_WEAK, base.evidenceRefs(),
                false, false, base.rollbackAction());
        assertEquals(ErrorCode.OBSERVE_ONLY, h.gateway.execute(weak, T).code());
    }

    @Test
    void expiredLeaseIsReleased() {
        Harness h = new Harness();
        ActionIntent base = Intents.valid(ActionKind.SPAWN_BUDGET);
        ActionIntent shortLived = new ActionIntent(base.intentId(), base.kind(), base.target(),
                base.initiator(), base.heldPermissions(), base.oldValue(), base.newValue(),
                Duration.ofSeconds(1), base.sideEffects(), base.evidence(), base.evidenceRefs(),
                false, false, base.rollbackAction());
        var lease = h.leases.acquire(shortLived, T);
        assertTrue(lease.isPresent());
        assertTrue(lease.get().isExpired(T.plusSeconds(2)));
        assertEquals(1, h.leases.expire(T.plusSeconds(2)));
        assertEquals(0, h.leases.activeCount(T.plusSeconds(2)));
    }
}
