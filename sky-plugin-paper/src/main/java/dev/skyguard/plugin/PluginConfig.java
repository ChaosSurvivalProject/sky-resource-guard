/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The enforced safe baseline for the installable P1.1 plugin. This class is
 * pure Java (no Bukkit type), so the fail-closed rules are unit-testable
 * without a server.
 *
 * <p>P1.1 is <b>read-only</b>: SHADOW mode, no automation, AI disabled,
 * unknown plugins observed only. If an operator edits config.yml to anything
 * else, {@link SkyPlugin} falls back to {@link #defaults()} and logs a severe
 * warning rather than silently enabling a stronger mode.</p>
 */
public final class PluginConfig {

    private final String mode;
    private final boolean automationEnabled;
    private final boolean writeConfigDryRun;
    private final String unknownPluginPolicy;
    private final boolean aiEnabled;
    private final boolean aiAllowExecute;
    private final int samplingIntervalTicks;
    private final int maxSamplesPerCycle;

    public PluginConfig(
            String mode,
            boolean automationEnabled,
            boolean writeConfigDryRun,
            String unknownPluginPolicy,
            boolean aiEnabled,
            boolean aiAllowExecute,
            int samplingIntervalTicks,
            int maxSamplesPerCycle) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.unknownPluginPolicy = Objects.requireNonNull(unknownPluginPolicy, "unknownPluginPolicy");
        this.automationEnabled = automationEnabled;
        this.writeConfigDryRun = writeConfigDryRun;
        this.aiEnabled = aiEnabled;
        this.aiAllowExecute = aiAllowExecute;
        if (samplingIntervalTicks < 1 || maxSamplesPerCycle < 1) {
            throw new IllegalArgumentException("sampling bounds must be positive");
        }
        this.samplingIntervalTicks = samplingIntervalTicks;
        this.maxSamplesPerCycle = maxSamplesPerCycle;
    }

    public static PluginConfig defaults() {
        return new PluginConfig("SHADOW", false, true, "OBSERVE_ONLY", false, false, 20, 16);
    }

    /** Every reason this configuration is not a read-only P1.1 baseline. */
    public List<String> violations() {
        List<String> out = new ArrayList<>();
        if (!"SHADOW".equals(mode)) {
            out.add("mode must be SHADOW in P1.1 (got " + mode + ")");
        }
        if (automationEnabled) {
            out.add("automation-enabled must be false");
        }
        if (!writeConfigDryRun) {
            out.add("write-config-dry-run must be true");
        }
        if (!"OBSERVE_ONLY".equals(unknownPluginPolicy)) {
            out.add("unknown-plugin-policy must be OBSERVE_ONLY");
        }
        if (aiEnabled) {
            out.add("ai.enabled must be false (no provider is loaded in this build)");
        }
        if (aiAllowExecute) {
            out.add("ai.allow-execute must be false");
        }
        if (samplingIntervalTicks < 20) {
            out.add("sampling.interval-ticks must be >= 20 (no main-tick hot loop)");
        }
        if (maxSamplesPerCycle > 64) {
            out.add("sampling.max-samples-per-cycle must be <= 64 (bounded work)");
        }
        return List.copyOf(out);
    }

    public boolean isReadOnly() {
        return violations().isEmpty();
    }

    public String mode() { return mode; }
    public boolean automationEnabled() { return automationEnabled; }
    public boolean writeConfigDryRun() { return writeConfigDryRun; }
    public String unknownPluginPolicy() { return unknownPluginPolicy; }
    public boolean aiEnabled() { return aiEnabled; }
    public boolean aiAllowExecute() { return aiAllowExecute; }
    public int samplingIntervalTicks() { return samplingIntervalTicks; }
    public int maxSamplesPerCycle() { return maxSamplesPerCycle; }
}
