/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.plugin;

import dev.skyguard.api.Contracts.HealthSnapshot;
import dev.skyguard.api.Contracts.SampleBudget;
import dev.skyguard.api.ResourceParticipant;
import dev.skyguard.core.GuardRuntime;
import dev.skyguard.runtime.ParticipantRegistry;
import java.time.Instant;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Installable Purpur/Paper plugin entry point (P1.1).
 *
 * <p>What it actually does: loads the safe config, assembles the pure-Java
 * guard core, auto-discovers {@link ResourceParticipant} providers through
 * {@link ServiceLoader}, samples them on a low-frequency repeating task, and
 * exposes read-only commands. It performs <b>no world write, no config write,
 * no automatic action and no AI call</b>.</p>
 *
 * <p>What it deliberately does NOT do: it never feeds a fabricated MSPT value
 * into the state machine. {@code GuardRuntime.recordTick} is unused because no
 * Paper/Purpur tick-time probe has been verified (see docs/version-matrix.md).
 * The state machine therefore stays in its initial state in P1.1.</p>
 *
 * <p>ServiceLoader currently sees providers visible to this plugin's own class
 * loader (the adapters bundled in the jar). Discovering providers from other
 * plugins' class loaders is a P2 item and is NOT claimed here.</p>
 */
public final class SkyPlugin extends JavaPlugin {

    private final AtomicReference<List<HealthSnapshot>> lastSamples = new AtomicReference<>(List.of());

    private GuardRuntime runtime;
    private ParticipantRegistry registry;
    private PluginConfig config;
    private int samplerTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = readConfig();
        List<String> violations = config.violations();
        if (!violations.isEmpty()) {
            getLogger().severe("unsafe config; falling back to read-only defaults: "
                    + String.join("; ", violations));
            config = PluginConfig.defaults();
        }

        runtime = new GuardRuntime();
        registry = new ParticipantRegistry();
        int discovered = discoverParticipants();

        PluginCommand command = getCommand("sky");
        if (command != null) {
            command.setExecutor(new SkyCommand(config, runtime, registry, lastSamples, this::platformFacts));
        } else {
            getLogger().warning("command 'sky' missing from plugin.yml; status commands unavailable");
        }

        startSampler();
        getLogger().info("SKY P1.1 enabled: SHADOW/read-only, participants=" + discovered
                + ", ai=disabled, msptProbe=UNRESOLVED");
    }

    @Override
    public void onDisable() {
        if (samplerTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(samplerTaskId);
            samplerTaskId = -1;
        }
        lastSamples.set(List.of());
        getLogger().info("SKY P1.1 disabled: no world state was changed");
    }

    private PluginConfig readConfig() {
        var cfg = getConfig();
        return new PluginConfig(
                cfg.getString("mode", "SHADOW"),
                cfg.getBoolean("automation-enabled", false),
                cfg.getBoolean("write-config-dry-run", true),
                cfg.getString("unknown-plugin-policy", "OBSERVE_ONLY"),
                cfg.getBoolean("ai.enabled", false),
                cfg.getBoolean("ai.allow-execute", false),
                cfg.getInt("sampling.interval-ticks", 20),
                cfg.getInt("sampling.max-samples-per-cycle", 16));
    }

    /** Generated adapters register themselves via META-INF/services: no plugin edit needed. */
    private int discoverParticipants() {
        int count = 0;
        ServiceLoader<ResourceParticipant> loader =
                ServiceLoader.load(ResourceParticipant.class, SkyPlugin.class.getClassLoader());
        for (ResourceParticipant participant : loader) {
            try {
                String id = participant.metadata().pluginId();
                if (registry.register(participant)) {
                    count++;
                    getLogger().info("auto-registered participant: " + id);
                } else {
                    getLogger().warning("duplicate participant id ignored: " + id);
                }
            } catch (RuntimeException | ServiceConfigurationError e) {
                // One broken adapter must never prevent the plugin from loading.
                getLogger().warning("participant discovery failed ("
                        + e.getClass().getSimpleName() + ")");
            }
        }
        return count;
    }

    private void startSampler() {
        long period = Math.max(20L, config.samplingIntervalTicks());
        samplerTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                List<HealthSnapshot> snapshots = registry.sampleAll(SampleBudget.strict());
                lastSamples.set(snapshots);
                runtime.eventBus().publish("participant.sample", Instant.now(),
                        "count=" + snapshots.size() + ",failures=" + registry.failures().size());
            } catch (RuntimeException e) {
                getLogger().warning("sampling cycle failed ("
                        + e.getClass().getSimpleName() + ")");
            }
        }, period, period).getTaskId();
    }

    /** Aggregate counts only. Never player names, UUIDs, coordinates or chat. */
    public PlatformFacts platformFacts() {
        return new PlatformFacts(
                Bukkit.getVersion(),
                Bukkit.getBukkitVersion(),
                Bukkit.getOnlinePlayers().size(),
                Bukkit.getWorlds().size(),
                Bukkit.getPluginManager().getPlugins().length,
                true);
    }
}
