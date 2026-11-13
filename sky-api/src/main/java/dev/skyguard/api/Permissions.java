/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

/** Permission node constants (task book 10). Never accept a look-alike node. */
public final class Permissions {

    private Permissions() {}

    public static final String OBSERVE_READ = "sky.observe.read";
    public static final String ADVICE_READ = "sky.advice.read";
    public static final String PLAN_DRYRUN = "sky.plan.dryrun";
    public static final String PROTECT_MANAGE = "sky.protect.manage";
    public static final String ACTION_SAFE = "sky.action.safe";
    public static final String ACTION_EXPENSIVE = "sky.action.expensive";
    public static final String ACTION_PLUGIN_LIFECYCLE = "sky.action.plugin.lifecycle";
    public static final String APPROVE_STANDARD = "sky.approve.standard";
    public static final String APPROVE_CRITICAL = "sky.approve.critical";
    public static final String AI_CONFIGURE = "sky.ai.configure";
    public static final String AUDIT_EXPORT = "sky.audit.export";
    public static final String OVERRIDE_EMERGENCY = "sky.override.emergency";

    /** Permission required by an action kind. */
    public static String requiredFor(ActionKind kind) {
        return switch (kind) {
            case PLUGIN_LIFECYCLE -> ACTION_PLUGIN_LIFECYCLE;
            case PLUGIN_QUIESCE, CACHE_EVICT -> ACTION_EXPENSIVE;
            default -> ACTION_SAFE;
        };
    }
}
