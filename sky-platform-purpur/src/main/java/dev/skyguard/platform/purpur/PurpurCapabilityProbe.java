/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.platform.purpur;

import dev.skyguard.platform.paper.CapabilityProbe;
import java.util.List;
import java.util.Objects;

/**
 * Purpur-specific capability listing. P0 makes NO availability claim: every
 * feature is reported as unresolved because no concrete Purpur 1.21.11 build
 * has been verified (docs/version-matrix.md). The core state machine never sees
 * a Purpur type.
 */
public final class PurpurCapabilityProbe implements CapabilityProbe {

    public static final List<String> TRACKED_FEATURES = List.of(
            "purpur.entity-limits",
            "purpur.simulation-distance",
            "purpur.spawn-budget",
            "purpur.item-frame-tick",
            "purpur.config-backup");

    private final CapabilityProbe delegate;

    public PurpurCapabilityProbe(CapabilityProbe delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public ProbeResult probe(ProbeRequest request) {
        Objects.requireNonNull(request, "request");
        // P0 hard rule: unresolved build -> read-only / suggest mode, never a crash.
        return new ProbeResult(request.feature(), false,
                "UNRESOLVED Purpur 1.21.11 build; probe plan in docs/version-matrix.md; "
                        + "fallback = read-only observation", true);
    }

    public List<String> trackedFeatures() {
        return TRACKED_FEATURES;
    }
}
