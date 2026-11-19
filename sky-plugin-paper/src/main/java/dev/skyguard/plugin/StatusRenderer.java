/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.plugin;

import dev.skyguard.api.Contracts.HealthSnapshot;
import dev.skyguard.core.GuardRuntime;
import dev.skyguard.runtime.ParticipantRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Pure-Java rendering of the read-only status. Keeping it free of Bukkit types
 * lets the whole command output be tested without a Minecraft server, and
 * makes the "no performance claim / no sensitive field" rules inspectable.
 */
public final class StatusRenderer {

    private StatusRenderer() {}

    public static String render(
            GuardRuntime runtime,
            ParticipantRegistry registry,
            PlatformFacts facts,
            PluginConfig config,
            List<HealthSnapshot> lastSamples,
            Instant now) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(lastSamples, "lastSamples");
        Objects.requireNonNull(now, "now");

        StringBuilder sb = new StringBuilder();
        sb.append("SKY Resource Guard / P1.1 (experimental, read-only)\n");
        sb.append("generatedAt: ").append(now).append('\n');
        sb.append("configReadOnly: ").append(config.isReadOnly());
        if (!config.isReadOnly()) {
            sb.append(" violations=").append(String.join("; ", config.violations()));
        }
        sb.append('\n');
        sb.append("mode: ").append(config.mode())
                .append(" | automation=").append(config.automationEnabled())
                .append(" | ai=").append(config.aiEnabled()).append('\n');
        sb.append("state: ").append(runtime.stateMachine().state())
                .append(" | lockoutReason: ").append(runtime.stateMachine().lockoutReason()).append('\n');
        sb.append("participants: ").append(registry.size())
                .append(" | lastSampleCount: ").append(lastSamples.size())
                .append(" | sampleFailures: ").append(registry.failures().size()).append('\n');
        sb.append("platform: server=").append(facts.serverVersion())
                .append(" bukkit=").append(facts.bukkitVersion())
                .append(" players=").append(facts.onlinePlayers())
                .append(" worlds=").append(facts.worldCount())
                .append(" plugins=").append(facts.loadedPlugins())
                .append(" verified=").append(facts.verified()).append('\n');
        sb.append("msptSamples: ").append(runtime.msptRing().size())
                .append('/').append(runtime.msptRing().capacity())
                .append(" (NOT MEASURED: no verified tick-time probe yet)\n");
        for (HealthSnapshot s : lastSamples) {
            sb.append("participant: ").append(s.pluginId())
                    .append(" healthy=").append(s.healthy())
                    .append(" queue=").append(s.queueDepth())
                    .append(" phase=").append(s.phase()).append('\n');
        }
        sb.append("note: no world write, no automatic action, no performance claim.\n");
        sb.append("note: concrete Purpur build / SHA-256 remain UNRESOLVED.\n");
        return sb.toString();
    }
}
