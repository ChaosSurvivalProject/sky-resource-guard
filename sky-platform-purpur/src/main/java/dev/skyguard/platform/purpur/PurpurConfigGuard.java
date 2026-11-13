/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.platform.purpur;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Purpur config-key guard (task book 2.2 / 5.4). Unknown keys are reported and
 * NEVER written. No key is considered "verified" until a startup probe records
 * the concrete server build that confirmed it. Since the Purpur 1.21.11 build
 * is currently UNRESOLVED, the verified set starts EMPTY by design.
 */
public final class PurpurConfigGuard {

    public record KeyReport(String key, boolean verified, String suggestion) {}
    public record WriteDecision(boolean allowed, String reason) {}

    private final Map<String, String> verifiedKeys = new LinkedHashMap<>();
    private final Set<String> reportedUnknown = new LinkedHashSet<>();

    /** Register a key only after a probe confirms it against a concrete build. */
    public synchronized void registerVerifiedKey(String key, String verifiedBuild) {
        if (verifiedBuild == null || verifiedBuild.isBlank() || verifiedBuild.startsWith("UNRESOLVED")) {
            throw new IllegalArgumentException("a concrete verified build is required");
        }
        verifiedKeys.put(key, verifiedBuild);
    }

    public synchronized KeyReport inspect(String key, String proposedValue) {
        boolean verified = verifiedKeys.containsKey(key);
        if (!verified) {
            reportedUnknown.add(key);
        }
        return new KeyReport(key, verified,
                verified ? "known key; still requires a leased, dry-run-first write" : "UNKNOWN KEY - report only, do not write");
    }

    /** P0 policy: no config write is ever allowed. */
    public WriteDecision proposeWrite(String key, String value) {
        KeyReport report = inspect(key, value);
        if (!report.verified()) {
            return new WriteDecision(false, "unknown key; read-only / suggest mode");
        }
        return new WriteDecision(false, "P0 has no server adapter; no config key is written");
    }

    public synchronized int verifiedKeyCount() {
        return verifiedKeys.size();
    }

    public synchronized List<String> reportedUnknownKeys() {
        return List.copyOf(reportedUnknown);
    }
}
