/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.bootstrap;

import dev.skyguard.ai.AiConfig;
import dev.skyguard.core.GuardRuntime;
import dev.skyguard.api.RunMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java assembly and compatibility check.
 *
 * <p><strong>This is NOT a Bukkit {@code JavaPlugin} main class.</strong> P0 has
 * no {@code plugin.yml}, no server lifecycle and no scheduler integration. An
 * unsupported or unresolved capability must put SKY into read-only / advisory
 * mode - it must never enter a startup crash loop.</p>
 */
public final class SkyBootstrap {

    public record StartupReport(
            boolean compatible,
            RunMode mode,
            List<String> checks,
            List<String> unresolved,
            String note) {}

    private SkyBootstrap() {}

    public static StartupReport check() {
        List<String> checks = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();

        int javaFeature = Runtime.version().feature();
        boolean javaOk = javaFeature >= 21;
        checks.add("java.feature=" + javaFeature + " required>=21 ok=" + javaOk);

        AiConfig ai = AiConfig.safeDefaults();
        checks.add("ai.enabled=" + ai.enabled() + " allowExecute=" + ai.allowExecute());
        checks.add("mode=SHADOW (first install default)");
        checks.add("unknownPluginPolicy=OBSERVE_ONLY");
        checks.add("writeConfigDryRun=true");

        unresolved.add("Purpur 1.21.11 concrete build / SHA-256 / upstream Paper build");
        unresolved.add("Purpur proprietary config keys (none verified, none written)");
        unresolved.add("installed plugin list and versions");
        unresolved.add("server artifact is NOT linked in P0");

        return new StartupReport(
                javaOk,
                RunMode.SHADOW,
                List.copyOf(checks),
                List.copyOf(unresolved),
                "No server plugin JAR is produced in P0; unresolved capabilities stay read-only.");
    }

    /** Assemble the core without starting a server, a thread or a network call. */
    public static GuardRuntime assemble() {
        return new GuardRuntime();
    }
}
