/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

/** Service tier. Self-developed identity raises adaptation priority, never budget. */
public enum ServiceTier {
    /** Core services with a non-squeezable floor budget. */
    CORE,
    PROTECTION,
    PLAYER_SERVICE,
    BACKGROUND,
    OPTIONAL
}
