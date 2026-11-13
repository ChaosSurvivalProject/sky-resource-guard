/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.adapter.exampleb;

import dev.skyguard.api.Contracts.AdapterMetadata;
import dev.skyguard.api.Contracts.CheckpointHandle;
import dev.skyguard.api.Contracts.CompatibilityReport;
import dev.skyguard.api.Contracts.Deadline;
import dev.skyguard.api.Contracts.Lease;
import dev.skyguard.api.Contracts.PluginRuntimeSnapshot;
import dev.skyguard.api.Contracts.PowerHandle;
import dev.skyguard.api.Contracts.QuiesceHandle;
import dev.skyguard.api.Contracts.QuiesceRequest;
import dev.skyguard.api.Contracts.ResumeReport;
import dev.skyguard.api.Contracts.ResumeToken;
import dev.skyguard.api.Contracts.ResourceSample;
import dev.skyguard.api.Contracts.SampleBudget;
import dev.skyguard.api.EvidenceLevel;
import dev.skyguard.api.ExternalPluginAdapter;
import dev.skyguard.api.PowerLevel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference adapter B: a cache/database/player-command/world-sync shaped plugin
 * reached through {@link ExternalPluginAdapter} (task book 4.3 / 5.5).
 *
 * <p>It uses ONLY the plugin's public API and documented commands. When a
 * capability is not publicly available it returns {@link Optional#empty()} and
 * stays read-only. It never stops a thread, never reflects into private fields,
 * never clears an internal queue and never force-unloads the plugin.</p>
 */
public final class ExternalExampleAdapter implements ExternalPluginAdapter {

    private static final String TARGET = "example-inventory-plugin";
    private static final Set<String> REQUIRED_PUBLIC_API = Set.of(
            "getLowPowerMode", "getQueueDepth", "saveCheckpoint");

    private final Set<String> processedResumeKeys = ConcurrentHashMap.newKeySet();
    private volatile boolean lowerPowerRequested;

    @Override
    public AdapterMetadata metadata() {
        // enabledByDefault is forced to false by the record's constructor.
        return new AdapterMetadata(
                "sky-adapter-selfexample-b", TARGET, "0.1.0", List.of("2.x", "3.x"), true);
    }

    @Override
    public CompatibilityReport probe(PluginRuntimeSnapshot runtime) {
        List<String> missing = new ArrayList<>();
        for (String api : REQUIRED_PUBLIC_API) {
            if (!runtime.publicApiSurface().containsKey(api)) {
                missing.add(api);
            }
        }
        List<String> reasons = new ArrayList<>();
        reasons.add("public API surface inspected; no private access performed");
        reasons.add("thread names recorded as L2 correlation only, never used to claim ownership");
        if (!runtime.safeStopCommandKnown()) {
            reasons.add("no documented safe-stop command: quiesce stays unsupported (read-only)");
        }
        return new CompatibilityReport(missing.isEmpty(), EvidenceLevel.L1_STRONG,
                reasons, List.copyOf(missing));
    }

    @Override
    public Optional<ResourceSample> sample(SampleBudget budget) {
        if (budget.maxSamples() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new ResourceSample(
                System.currentTimeMillis(), 0.8, 0, 4096L, EvidenceLevel.L1_STRONG));
    }

    @Override
    public Optional<PowerHandle> requestLowerPower(PowerLevel level, Lease lease) {
        if (lease == null || lease.isExpired(Instant.now())) {
            return Optional.empty();
        }
        lowerPowerRequested = true;
        return Optional.of(new PowerHandle(true, level,
                "called documented command /example lowpower " + level,
                lease.expiresAt()));
    }

    @Override
    public Optional<CheckpointHandle> checkpoint(Deadline deadline) {
        // The target plugin has no public checkpoint API in this example.
        return Optional.empty();
    }

    @Override
    public Optional<QuiesceHandle> quiesce(QuiesceRequest request) {
        // Only a documented safe-stop command would allow this. Never a thread stop.
        return Optional.empty();
    }

    @Override
    public ResumeReport resume(ResumeToken token) {
        String key = token.idempotencyKey() + "#" + token.generation();
        if (!processedResumeKeys.add(key)) {
            return new ResumeReport(true, true, "idempotent replay, no double write");
        }
        lowerPowerRequested = false;
        return new ResumeReport(true, false, "restored via public API in dependency order");
    }

    public boolean lowerPowerRequested() {
        return lowerPowerRequested;
    }
}
