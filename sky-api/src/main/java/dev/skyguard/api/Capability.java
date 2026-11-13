/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

/** Capabilities a participant truthfully declares. Never claim unaudited ones. */
public enum Capability {
    LOWER_POWER,
    CHECKPOINT,
    QUIESCE,
    RESUME,
    RELEASE_RESOURCES,
    HEALTH_CHECK,
    QUEUE_REPORT,
    CACHE_REPORT,
    EXECUTOR_SHUTDOWN,
    LEAK_DETECTION,
    DRY_RUN
}
