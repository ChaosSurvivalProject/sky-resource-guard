/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable data contracts shared by all SKY layers.
 *
 * <p>Keeping the DTOs in one place makes the public surface easy to version:
 * consumers use {@code import dev.skyguard.api.Contracts.*} and refer to
 * {@code Lease}, {@code ActionIntent}, etc. by their plain names. No type here
 * references a server implementation, an AI SDK or a network client.</p>
 */
public final class Contracts {

    private Contracts() {}

    // ------------------------------------------------------------------
    // Participant identity and health
    // ------------------------------------------------------------------

    public record ParticipantMetadata(
            String pluginId,
            String version,
            List<String> dependencies,
            ServiceTier serviceTier,
            CapabilitySet capabilities,
            String faultDomain,
            Instant registeredAt) {
        public ParticipantMetadata {
            Objects.requireNonNull(pluginId, "pluginId");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(serviceTier, "serviceTier");
            Objects.requireNonNull(capabilities, "capabilities");
            dependencies = List.copyOf(dependencies);
        }
    }

    /** A bounded sampling budget; participants must not exceed it. */
    public record SampleBudget(int maxSamples, Duration maxCpuTime, boolean allowExpensiveProbe) {
        public SampleBudget {
            if (maxSamples < 0) {
                throw new IllegalArgumentException("maxSamples must be >= 0");
            }
            Objects.requireNonNull(maxCpuTime, "maxCpuTime");
        }

        public static SampleBudget strict() {
            return new SampleBudget(16, Duration.ofMillis(2), false);
        }
    }

    public record HealthSnapshot(
            String pluginId,
            Instant at,
            ExecutionPhase phase,
            Urgency urgency,
            int queueDepth,
            int activeConcurrency,
            long estimatedHeapBytes,
            long cpuTimeNanos,
            String currentStage,
            boolean healthy,
            Set<SideEffect> sideEffects,
            List<String> errors) {
        public HealthSnapshot {
            sideEffects = Set.copyOf(sideEffects);
            errors = List.copyOf(errors);
        }
    }

    // ------------------------------------------------------------------
    // Lease and action pipeline (task book 10)
    // ------------------------------------------------------------------

    /**
     * A time-bounded authorisation for exactly one production change.
     *
     * <p>Contains old/new value, preconditions (in {@code verificationQuery}),
     * a rollback action, a checksum and evidence references. A lease that is
     * lost, expired or unverified must restore the old state.</p>
     */
    public record Lease(
            String leaseId,
            String target,
            String actionId,
            String initiator,
            String oldValue,
            String newValue,
            Instant issuedAt,
            Duration ttl,
            String verificationQuery,
            String rollbackAction,
            String checksum,
            List<String> evidenceRefs) {
        public Lease {
            Objects.requireNonNull(leaseId, "leaseId");
            Objects.requireNonNull(ttl, "ttl");
            if (ttl.isNegative() || ttl.isZero()) {
                throw new IllegalArgumentException("ttl must be positive");
            }
            evidenceRefs = List.copyOf(evidenceRefs);
        }

        public Instant expiresAt() {
            return issuedAt.plus(ttl);
        }

        public boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt());
        }
    }

    public record ActionIntent(
            String intentId,
            ActionKind kind,
            String target,
            String initiator,
            Set<String> heldPermissions,
            String oldValue,
            String newValue,
            Duration ttl,
            Set<SideEffect> sideEffects,
            EvidenceLevel evidence,
            List<String> evidenceRefs,
            boolean dryRun,
            boolean approved,
            String rollbackAction) {
        public ActionIntent {
            Objects.requireNonNull(kind, "kind");
            heldPermissions = Set.copyOf(heldPermissions);
            sideEffects = Set.copyOf(sideEffects);
            evidenceRefs = List.copyOf(evidenceRefs);
        }

        public boolean isIrreversible() {
            return sideEffects.contains(SideEffect.IRREVERSIBLE);
        }
    }

    public record ActionResult(
            String intentId,
            ErrorCode code,
            boolean applied,
            boolean verified,
            boolean rolledBack,
            String leaseId,
            String detail) {
        public static ActionResult rejected(String intentId, ErrorCode code, String detail) {
            return new ActionResult(intentId, code, false, false, false, null, detail);
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle contracts (task book 8)
    // ------------------------------------------------------------------

    public record Deadline(Instant expiresAt, Duration cpuBudget) {
        public boolean expired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    public record CheckpointRequest(
            String participantId, String idempotencyKey, long generation, Deadline deadline) {}

    public record CheckpointReceipt(
            String participantId, long generation, String checkpointId, boolean ok, String detail) {}

    public record QuiesceRequest(
            String participantId, String reason, Deadline deadline, boolean dryRun) {}

    public record QuiesceReceipt(
            String participantId, QuiesceHandle.Outcome outcome, String detail) {}

    public record ResumeToken(
            String participantId, long generation, String leaseId, String idempotencyKey) {}

    public record ResumeReceipt(
            String participantId, long generation, boolean ok, boolean idempotentReplay, String detail) {}

    public record ReleaseRequest(
            String participantId, Set<String> resourceKinds, Deadline deadline) {
        public ReleaseRequest {
            resourceKinds = Set.copyOf(resourceKinds);
        }
    }

    public record ReleaseReceipt(
            String participantId, int releasedCount, boolean ok, String detail) {}

    // ------------------------------------------------------------------
    // External / unadapted plugin adapter contracts (task book 5.5)
    // ------------------------------------------------------------------

    /** Read-only observation of an unadapted plugin. Never a live Bukkit object. */
    public record PluginRuntimeSnapshot(
            String pluginId,
            boolean enabled,
            List<String> observedThreadNames,
            Map<String, String> publicApiSurface,
            boolean safeStopCommandKnown,
            long observationCount) {
        public PluginRuntimeSnapshot {
            observedThreadNames = List.copyOf(observedThreadNames);
            publicApiSurface = Map.copyOf(publicApiSurface);
        }
    }

    /**
     * Adapters are never enabled implicitly. {@code enabledByDefault} is forced
     * to {@code false}; an administrator must opt in per adapter.
     */
    public record AdapterMetadata(
            String adapterId,
            String targetPluginId,
            String adapterVersion,
            List<String> supportedPluginVersions,
            boolean enabledByDefault) {
        public AdapterMetadata {
            supportedPluginVersions = List.copyOf(supportedPluginVersions);
            enabledByDefault = false;
        }
    }

    public record CompatibilityReport(
            boolean compatible, EvidenceLevel evidence, List<String> reasons, List<String> missingApis) {
        public CompatibilityReport {
            reasons = List.copyOf(reasons);
            missingApis = List.copyOf(missingApis);
        }
    }

    public record ResourceSample(
            long timestampMillis, double estimatedCpuMillis, int queueDepth,
            long estimatedBytes, EvidenceLevel evidence) {}

    public record PowerHandle(
            boolean accepted, PowerLevel grantedLevel, String detail, Instant validUntil) {}

    public record CheckpointHandle(boolean accepted, String checkpointId, String detail) {}

    public record QuiesceHandle(boolean accepted, Outcome outcome, String detail) {
        public enum Outcome { QUIESCED, TIMEOUT, FAILED, DRY_RUN }
    }

    public record ResumeReport(boolean ok, boolean idempotentReplay, String detail) {}

    // ------------------------------------------------------------------
    // AI advice output (task book 11.3)
    // ------------------------------------------------------------------

    /**
     * The only shape an AI provider may produce. Validation is deliberately
     * NOT performed in the constructor so that malformed payloads can be
     * rejected by {@code AdviceSchemaValidator} rather than throwing early.
     */
    public record AdviceProposal(
            List<String> hypotheses,
            double confidence,
            List<String> evidenceRefs,
            List<String> recommendedDryRuns,
            Risk risk,
            String rollback,
            List<String> missingEvidence) {
        public enum Risk { LOW, MEDIUM, HIGH }

        public AdviceProposal {
            hypotheses = List.copyOf(hypotheses);
            evidenceRefs = List.copyOf(evidenceRefs);
            recommendedDryRuns = List.copyOf(recommendedDryRuns);
            missingEvidence = List.copyOf(missingEvidence);
        }
    }
}
