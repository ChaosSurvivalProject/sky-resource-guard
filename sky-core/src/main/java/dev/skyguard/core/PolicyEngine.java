/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

import dev.skyguard.api.Capability;
import dev.skyguard.api.CapabilitySet;
import dev.skyguard.api.Contracts.ActionIntent;
import dev.skyguard.api.ErrorCode;
import dev.skyguard.api.GuardState;
import dev.skyguard.api.Permissions;
import dev.skyguard.api.SideEffect;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The local policy engine. The {@code priorityScore} is <em>auxiliary</em>: it
 * can never override a hard constraint (task book 6.2 / 10). Irreversible,
 * economy, inventory and world-write effects never enter a fully automatic
 * role, regardless of score.
 */
public final class PolicyEngine {

    public record PolicyContext(
            boolean dependenciesSatisfied,
            boolean businessFloorOk,
            boolean playerSafe,
            boolean dataDurable,
            CapabilitySet participantCapabilities) {
        public static PolicyContext healthy(CapabilitySet capabilities) {
            return new PolicyContext(true, true, true, true, capabilities);
        }
    }

    public record Decision(boolean allowed, ErrorCode code, double score, List<String> reasons) {
        public static Decision deny(ErrorCode code, String reason) {
            return new Decision(false, code, 0.0, List.of(reason));
        }
    }

    public Decision evaluate(ActionIntent intent, GuardState state, PolicyContext ctx) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(ctx, "ctx");
        List<String> reasons = new ArrayList<>();

        // 1. capability validation -------------------------------------------------
        if (intent.kind() == null) {
            return Decision.deny(ErrorCode.CAPABILITY_MISSING, "no pre-registered action kind");
        }
        Capability required = requiredCapability(intent.kind());
        if (required != null && !ctx.participantCapabilities().has(required)) {
            return Decision.deny(ErrorCode.CAPABILITY_MISSING,
                    "participant did not declare " + required);
        }

        // 2. permission ------------------------------------------------------------
        String node = Permissions.requiredFor(intent.kind());
        if (!intent.heldPermissions().contains(node)) {
            return Decision.deny(ErrorCode.PERMISSION_DENIED, "missing " + node);
        }
        if (intent.kind().isHighRisk() && !intent.approved()) {
            return Decision.deny(ErrorCode.PERMISSION_DENIED,
                    "high-risk action requires explicit approval");
        }

        // 3. dependencies + hard constraints --------------------------------------
        if (!ctx.dependenciesSatisfied()) {
            return Decision.deny(ErrorCode.DEPENDENCY_UNSATISFIED, "dependency not satisfied");
        }
        if (!ctx.businessFloorOk() || !ctx.playerSafe() || !ctx.dataDurable()) {
            return Decision.deny(ErrorCode.UNSAFE_RANGE, "a non-overridable hard constraint failed");
        }

        // 4. safe range ------------------------------------------------------------
        if (intent.ttl() == null || intent.ttl().isNegative() || intent.ttl().isZero()) {
            return Decision.deny(ErrorCode.UNSAFE_RANGE, "lease TTL must be positive");
        }
        if (intent.rollbackAction() == null || intent.rollbackAction().isBlank()) {
            return Decision.deny(ErrorCode.UNSAFE_RANGE, "action has no rollback path");
        }

        // 5. irreversible / sensitive effects -------------------------------------
        if (intent.isIrreversible()) {
            return Decision.deny(ErrorCode.UNAUTHORIZED_ACTION,
                    "irreversible actions are never automatic");
        }
        boolean sensitive = intent.sideEffects().contains(SideEffect.ECONOMY)
                || intent.sideEffects().contains(SideEffect.INVENTORY)
                || intent.sideEffects().contains(SideEffect.WORLD_WRITE)
                || intent.sideEffects().contains(SideEffect.IRREVERSIBLE);
        if (sensitive && !(intent.approved()
                && intent.heldPermissions().contains(Permissions.APPROVE_CRITICAL))) {
            return Decision.deny(ErrorCode.PERMISSION_DENIED,
                    "sensitive effect requires critical approval");
        }

        // 6. evidence grade --------------------------------------------------------
        if (intent.evidence() == null || intent.evidence().warningOnly()) {
            return Decision.deny(ErrorCode.OBSERVE_ONLY,
                    "L2/L3 evidence is warning-only; unadapted plugins stay OBSERVE_ONLY");
        }

        // 7. lockout (dry-run remains available for human diagnosis) --------------
        if (state == GuardState.LOCKOUT && !intent.dryRun()) {
            return Decision.deny(ErrorCode.LOCKOUT, "LOCKOUT stops automatic actions");
        }

        reasons.add("capability=" + required + ", permission=" + node
                + ", evidence=" + intent.evidence() + ", state=" + state);
        double score = priorityScore(
                urgencyWeight(intent),
                1.0,
                1.0,
                evidenceConfidence(intent.evidence()),
                1.0,
                intent.kind().isHighRisk() ? 4.0 : 1.0);
        reasons.add("priorityScore=" + score + " (advisory only)");
        return new Decision(true, ErrorCode.OK, score, List.copyOf(reasons));
    }

    /** Auxiliary formula from task book 6.2. Denominators are floored to stay finite. */
    public static double priorityScore(
            double urgencyWeight, double playerImpact, double deadlinePressure,
            double evidenceConfidence, double estimatedCost, double actionRisk) {
        double denominator = Math.max(estimatedCost, 1e-6) * Math.max(actionRisk, 1e-6);
        return urgencyWeight * playerImpact * deadlinePressure * evidenceConfidence / denominator;
    }

    private static double urgencyWeight(ActionIntent intent) {
        return intent.kind().isHighRisk() ? 0.25 : 1.0;
    }

    private static double evidenceConfidence(dev.skyguard.api.EvidenceLevel level) {
        return switch (level) {
            case L0_FACT -> 1.0;
            case L1_STRONG -> 0.8;
            case L2_WEAK -> 0.4;
            case L3_GUESS -> 0.1;
        };
    }

    private static Capability requiredCapability(dev.skyguard.api.ActionKind kind) {
        return switch (kind) {
            case PLUGIN_LOW_POWER -> Capability.LOWER_POWER;
            case PLUGIN_QUIESCE -> Capability.QUIESCE;
            case CACHE_EVICT -> Capability.CACHE_REPORT;
            default -> null;
        };
    }
}
