/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

/** Side effects an action may have; used by the policy engine's hard constraints. */
public enum SideEffect {
    PLAYER_VISIBLE,
    ECONOMY,
    INVENTORY,
    WORLD_WRITE,
    EXTERNAL_IO,
    /** Irreversible effects are banned from fully automatic roles. */
    IRREVERSIBLE
}
