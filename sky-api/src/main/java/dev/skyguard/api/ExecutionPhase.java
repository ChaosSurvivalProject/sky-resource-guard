/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

/** Execution phase of a participant's work (task book 8.2). */
public enum ExecutionPhase {
    TICK_ESSENTIAL,
    WORLD_SYNC,
    PLAYER_FACING,
    BACKGROUND_IO,
    MAINTENANCE
}
