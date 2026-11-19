/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.plugin;

/**
 * Read-only platform facts. {@code verified=false} means the values are
 * placeholders and must be reported as UNRESOLVED, never as measurements.
 *
 * <p>This DTO carries no player name, UUID, IP, coordinate or chat text: the
 * command output and the event bus only ever see aggregate counts.</p>
 */
public record PlatformFacts(
        String serverVersion,
        String bukkitVersion,
        int onlinePlayers,
        int worldCount,
        int loadedPlugins,
        boolean verified) {

    public static PlatformFacts unresolved() {
        return new PlatformFacts("UNRESOLVED", "UNRESOLVED", 0, 0, 0, false);
    }
}
