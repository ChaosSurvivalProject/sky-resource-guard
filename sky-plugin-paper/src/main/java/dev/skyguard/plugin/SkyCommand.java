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
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** Read-only command surface. Every branch only sends text; nothing mutates state. */
public final class SkyCommand implements CommandExecutor {

    private static final String PERMISSION = "sky.observe.read";

    private final PluginConfig config;
    private final GuardRuntime runtime;
    private final ParticipantRegistry registry;
    private final AtomicReference<List<HealthSnapshot>> lastSamples;
    private final Supplier<PlatformFacts> facts;

    public SkyCommand(
            PluginConfig config,
            GuardRuntime runtime,
            ParticipantRegistry registry,
            AtomicReference<List<HealthSnapshot>> lastSamples,
            Supplier<PlatformFacts> facts) {
        this.config = Objects.requireNonNull(config, "config");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.lastSamples = Objects.requireNonNull(lastSamples, "lastSamples");
        this.facts = Objects.requireNonNull(facts, "facts");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("SKY: permission " + PERMISSION + " is required.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> sender.sendMessage(StatusRenderer.render(
                    runtime, registry, facts.get(), config, lastSamples.get(), Instant.now()));
            case "participants" -> sender.sendMessage(renderParticipants());
            case "shadow-report" -> sender.sendMessage(runtime.shadowReport(Instant.now()));
            case "help" -> sender.sendMessage(help());
            default -> sender.sendMessage(help());
        }
        return true;
    }

    private String renderParticipants() {
        StringBuilder sb = new StringBuilder();
        sb.append("SKY participants: ").append(registry.size()).append('\n');
        List<HealthSnapshot> snapshots = lastSamples.get();
        if (snapshots.isEmpty()) {
            sb.append("(no sample yet; sampling is read-only and low-frequency)\n");
        }
        for (HealthSnapshot s : snapshots) {
            sb.append("  ").append(s.pluginId())
                    .append(" healthy=").append(s.healthy())
                    .append(" queue=").append(s.queueDepth())
                    .append(" phase=").append(s.phase()).append('\n');
        }
        if (!registry.failures().isEmpty()) {
            sb.append("sampleFailures: ").append(registry.failures().size()).append('\n');
        }
        return sb.toString();
    }

    private static String help() {
        return "SKY P1.1 (read-only)\n"
                + "/sky status          - state, participants, platform facts\n"
                + "/sky participants    - last read-only health samples\n"
                + "/sky shadow-report   - full SHADOW telemetry report\n"
                + "/sky help            - this text\n"
                + "No command changes the world. AI is disabled in this build.\n";
    }
}
