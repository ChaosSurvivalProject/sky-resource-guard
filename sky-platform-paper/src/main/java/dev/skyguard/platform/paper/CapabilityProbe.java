/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.platform.paper;

/**
 * Read-only startup capability probe (task book 2.2). Platform abilities are
 * discovered, never assumed; a probe failure must fall back to a read-only /
 * advisory mode instead of crashing the server.
 */
public interface CapabilityProbe {

    record ProbeRequest(String feature, String versionRange, boolean reflectionAllowed) {}

    record ProbeResult(String feature, boolean available, String detail, boolean fallbackUsed) {
        public static ProbeResult unavailable(String feature, String detail) {
            return new ProbeResult(feature, false, detail, true);
        }
    }

    ProbeResult probe(ProbeRequest request);
}
