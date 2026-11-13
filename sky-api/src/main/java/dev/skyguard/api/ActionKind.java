/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

/** Pre-registered action kinds. Only these may ever be proposed or executed. */
public enum ActionKind {
    SIMULATION_DISTANCE(false),
    SPAWN_BUDGET(false),
    ACTIVATION_BUDGET(false),
    DROP_MERGE(false),
    DROP_FREEZE(false),
    CACHE_EVICT(false),
    TASK_SUSPEND(false),
    PLUGIN_LOW_POWER(false),
    PLUGIN_QUIESCE(false),
    VIEW_DISTANCE(false),
    /** Requires explicit administrator authorisation and dependency checks. */
    PLUGIN_LIFECYCLE(true);

    private final boolean highRisk;

    ActionKind(boolean highRisk) {
        this.highRisk = highRisk;
    }

    public boolean isHighRisk() {
        return highRisk;
    }
}
