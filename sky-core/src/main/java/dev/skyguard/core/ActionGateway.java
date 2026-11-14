/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

import dev.skyguard.api.CapabilitySet;
import dev.skyguard.api.Contracts.ActionIntent;
import dev.skyguard.api.Contracts.ActionResult;
import dev.skyguard.api.Contracts.Lease;
import dev.skyguard.api.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The single choke point for every production change (task book 10):
 *
 * <pre>
 * validate capability -&gt; check permission -&gt; check dependencies
 * -&gt; check safe range -&gt; dry-run -&gt; acquire lease -&gt; snapshot old state
 * -&gt; apply -&gt; verify -&gt; audit -&gt; release or rollback
 * </pre>
 *
 * <p>Fail-safe rules implemented here: a dry-run never acquires a lease and
 * never applies; verification failure rolls back automatically; a failed
 * rollback enters LOCKOUT; an expired lease is recoverable through {@link
 * LeaseManager#drainExpired(Instant)}; actions that could double-write are not
 * retried.</p>
 *
 * <p><b>Lease release is unconditional.</b> It lives in a {@code finally} so
 * that success, verification failure, and a throwing executor / verifier /
 * rollback all release the lease exactly once. A throwing collaborator is
 * treated as an unverified change: it is rolled back, and a failed rollback
 * escalates to LOCKOUT rather than leaving an unknown world state.</p>
 */
public final class ActionGateway {

    @FunctionalInterface
    public interface Executor {
        /** Returns the observed value after applying; must be side-effect-bounded. */
        String apply(ActionIntent intent, Lease lease) throws Exception;
    }

    @FunctionalInterface
    public interface Verifier {
        boolean verify(ActionIntent intent, Lease lease, String observed);
    }

    @FunctionalInterface
    public interface Rollback {
        /** Must be idempotent: repeated calls must not double-write. */
        boolean rollback(ActionIntent intent, Lease lease);
    }

    @FunctionalInterface
    public interface AuditSink {
        void record(String event);
    }

    private final PolicyEngine policy = new PolicyEngine();
    private final LeaseManager leases;
    private final GuardStateMachine stateMachine;
    /**
     * Capabilities SKY itself owns. Actions targeting a specific participant
     * MUST instead pass that participant's truthfully declared capability set
     * through {@link #execute(ActionIntent, Instant, CapabilitySet)}.
     */
    private final CapabilitySet platformCapabilities;
    private final Executor executor;
    private final Verifier verifier;
    private final Rollback rollback;
    private final AuditSink audit;

    public ActionGateway(
            LeaseManager leases,
            GuardStateMachine stateMachine,
            Executor executor,
            Verifier verifier,
            Rollback rollback,
            AuditSink audit) {
        this(leases, stateMachine, executor, verifier, rollback, audit,
                CapabilitySet.of(dev.skyguard.api.Capability.values()));
    }

    public ActionGateway(
            LeaseManager leases,
            GuardStateMachine stateMachine,
            Executor executor,
            Verifier verifier,
            Rollback rollback,
            AuditSink audit,
            CapabilitySet platformCapabilities) {
        this.leases = Objects.requireNonNull(leases, "leases");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.rollback = Objects.requireNonNull(rollback, "rollback");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.platformCapabilities = Objects.requireNonNull(platformCapabilities, "platformCapabilities");
    }

    /** Uses SKY's own platform capabilities. */
    public ActionResult execute(ActionIntent intent, Instant now) {
        return execute(intent, now, platformCapabilities);
    }

    public synchronized ActionResult execute(
            ActionIntent intent, Instant now, CapabilitySet participantCapabilities) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(participantCapabilities, "participantCapabilities");

        PolicyEngine.Decision decision = policy.evaluate(
                intent, stateMachine.state(), PolicyEngine.PolicyContext.healthy(participantCapabilities));
        if (!decision.allowed()) {
            audit.record("REJECTED " + intent.intentId() + " " + decision.code()
                    + " " + String.join("; ", decision.reasons()));
            return ActionResult.rejected(intent.intentId(), decision.code(),
                    String.join("; ", decision.reasons()));
        }

        // dry-run: no lease, no apply, no production change (task book 14).
        if (intent.dryRun()) {
            audit.record("DRY-RUN " + intent.intentId() + " " + intent.kind()
                    + " -> " + intent.target() + " (no change applied)");
            return new ActionResult(intent.intentId(), ErrorCode.DRY_RUN_ONLY,
                    false, false, false, null, "dry-run only; no lease acquired");
        }

        // Atomic check-and-reserve: no second caller can slip between the
        // slot check and markActionStarted.
        if (!stateMachine.tryBeginAction(now)) {
            ErrorCode code = stateMachine.state() == dev.skyguard.api.GuardState.LOCKOUT
                    ? ErrorCode.LOCKOUT : ErrorCode.TIMEOUT;
            audit.record("DEFERRED " + intent.intentId() + " " + code);
            return ActionResult.rejected(intent.intentId(), code,
                    "action slot unavailable (in-flight, cooldown or LOCKOUT)");
        }

        try {
            Optional<Lease> maybeLease = leases.acquire(intent, now);
            if (maybeLease.isEmpty()) {
                audit.record("REJECTED " + intent.intentId() + " LEASE_CONFLICT");
                return ActionResult.rejected(intent.intentId(), ErrorCode.LEASE_CONFLICT,
                        "another active lease targets " + intent.target());
            }
            Lease lease = maybeLease.get();
            audit.record("LEASE " + lease.leaseId() + " " + intent.intentId()
                    + " old=" + intent.oldValue() + " new=" + intent.newValue()
                    + " ttl=" + intent.ttl() + " checksum=" + lease.checksum());
            try {
                return applyUnderLease(intent, lease, now);
            } finally {
                // release() is idempotent. This single line covers every exit
                // path, including exceptions thrown by collaborators.
                leases.release(lease.leaseId(), now);
            }
        } finally {
            stateMachine.markActionFinished();
        }
    }

    private ActionResult applyUnderLease(ActionIntent intent, Lease lease, Instant now) {
        final String observed;
        try {
            observed = executor.apply(intent, lease);
        } catch (Exception e) {
            return recoverAfterFailure(intent, lease, now, ErrorCode.INTERNAL_ERROR,
                    "executor failed: " + e.getMessage(), e);
        }

        final boolean verified;
        try {
            verified = verifier.verify(intent, lease, observed);
        } catch (RuntimeException e) {
            return recoverAfterFailure(intent, lease, now, ErrorCode.VERIFICATION_FAILED,
                    "verifier threw: " + e.getMessage(), e);
        }
        audit.record("APPLIED " + intent.intentId() + " verified=" + verified
                + " observed=" + observed);

        if (!verified) {
            boolean rolledBack = attemptRollback(intent, lease);
            if (!rolledBack) {
                stateMachine.lockout("rollback failed after verification of " + intent.intentId(), now);
                audit.record("LOCKOUT " + intent.intentId() + " rollback failed");
            }
            return new ActionResult(intent.intentId(),
                    rolledBack ? ErrorCode.VERIFICATION_FAILED : ErrorCode.ROLLBACK_FAILED,
                    true, false, rolledBack, lease.leaseId(),
                    rolledBack ? "verification failed, rolled back" : "verification AND rollback failed");
        }

        audit.record("COMMITTED " + intent.intentId() + " " + lease.leaseId());
        return new ActionResult(intent.intentId(), ErrorCode.OK,
                true, true, false, lease.leaseId(), "applied and verified");
    }

    /** A throwing collaborator leaves the change unverified: roll back, never guess. */
    private ActionResult recoverAfterFailure(
            ActionIntent intent, Lease lease, Instant now,
            ErrorCode code, String detail, Exception cause) {
        boolean rolledBack = attemptRollback(intent, lease);
        if (!rolledBack) {
            stateMachine.lockout("rollback failed after " + detail + " on " + intent.intentId(), now);
            audit.record("LOCKOUT " + intent.intentId() + " rollback failed after exception");
        }
        // Only the exception CLASS is audited: messages may contain host names,
        // paths or other sensitive data and are deliberately not logged.
        audit.record("ERROR " + intent.intentId() + " rolledBack=" + rolledBack
                + " cause=" + cause.getClass().getSimpleName());
        return new ActionResult(intent.intentId(), code,
                true, false, rolledBack, lease.leaseId(), detail);
    }

    private boolean attemptRollback(ActionIntent intent, Lease lease) {
        try {
            return rollback.rollback(intent, lease);
        } catch (RuntimeException e) {
            audit.record("ERROR rollback threw " + e.getClass().getSimpleName()
                    + " for " + intent.intentId());
            return false;
        }
    }
}
