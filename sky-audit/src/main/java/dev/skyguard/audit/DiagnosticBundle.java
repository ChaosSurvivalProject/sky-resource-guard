/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.audit;

import dev.skyguard.api.GuardState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Diagnostic bundle skeleton (task book 13). By default it excludes chat raw
 * text, secrets, complete NBT, identifiable player information and world data.
 * Heavy diagnostics (full thread dump, object histogram) require an explicit
 * administrator trigger or a WATCH-or-higher state plus a resource budget.
 */
public final class DiagnosticBundle {

    public static final List<String> DEFAULT_OMISSIONS = List.of(
            "chat raw text", "api keys and tokens", "complete item NBT",
            "identifiable player data", "world save data");

    public record Bundle(
            String traceId,
            Instant at,
            String skyVersion,
            String serverVersion,
            String policyHash,
            List<String> sections,
            List<String> omitted,
            boolean heavyDiagnosticsIncluded,
            boolean signed) {}

    private DiagnosticBundle() {}

    public static Bundle create(
            Instant at, String skyVersion, String serverVersion,
            String policyHash, List<String> metricSummaries, AuditLog audit) {
        String traceId = "trace-" + UUID.randomUUID();
        return new Bundle(
                traceId, at, skyVersion, serverVersion, policyHash,
                List.copyOf(metricSummaries), DEFAULT_OMISSIONS, false, false);
    }

    /**
     * Full thread dumps and object histograms are gated: they must not run in
     * NORMAL without an admin trigger, and never without a CPU/time/size budget.
     */
    public static boolean heavyDiagnosticsAllowed(GuardState state, boolean adminTriggered) {
        if (adminTriggered) {
            return true;
        }
        return switch (state) {
            case WATCH, DEGRADE, CRITICAL, RECOVERY, LOCKOUT -> true;
            case NORMAL -> false;
        };
    }
}
