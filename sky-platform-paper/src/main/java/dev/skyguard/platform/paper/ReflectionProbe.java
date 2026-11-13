/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.platform.paper;

import java.util.Objects;

/**
 * Declared, opt-in reflection probe (task book 2.2). Constraints, all enforced
 * here:
 *
 * <ul>
 *   <li>explicit switch ({@code enabled});</li>
 *   <li>version range declared by the caller;</li>
 *   <li>fail-closed: any exception returns "unavailable";</li>
 *   <li>alternative path: the safe, read-only fallback;</li>
 *   <li>risk note in the result;</li>
 *   <li>only checks class <em>presence</em>; never calls {@code setAccessible},
 *       never bypasses Java module or language access boundaries.</li>
 * </ul>
 */
public final class ReflectionProbe implements CapabilityProbe {

    private final boolean enabled;
    private final String supportedVersionRange;

    public ReflectionProbe(boolean enabled, String supportedVersionRange) {
        this.enabled = enabled;
        this.supportedVersionRange = Objects.requireNonNull(supportedVersionRange, "supportedVersionRange");
    }

    @Override
    public ProbeResult probe(ProbeRequest request) {
        Objects.requireNonNull(request, "request");
        if (!enabled || !request.reflectionAllowed()) {
            return ProbeResult.unavailable(request.feature(),
                    "reflection switch off - using read-only fallback");
        }
        if (!supportedVersionRange.equals(request.versionRange())) {
            return ProbeResult.unavailable(request.feature(),
                    "version range mismatch - using read-only fallback");
        }
        try {
            // Presence check only. No reflective member access, no invocation.
            Class.forName("org.bukkit.Server", false, ReflectionProbe.class.getClassLoader());
            return new ProbeResult(request.feature(), true,
                    "class present; risk: presence is NOT an API compatibility guarantee; "
                            + "fallback remains available", false);
        } catch (ClassNotFoundException | LinkageError e) {
            return ProbeResult.unavailable(request.feature(),
                    "probe failed closed (" + e.getClass().getSimpleName() + ") - read-only fallback");
        }
    }
}
