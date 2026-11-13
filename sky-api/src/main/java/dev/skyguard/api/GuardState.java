/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

/** Pressure states of the guard state machine (task book 6.1). */
public enum GuardState {
    NORMAL,
    WATCH,
    DEGRADE,
    CRITICAL,
    RECOVERY,
    /** Stops further automatic action; keeps a human recovery entry point. */
    LOCKOUT;

    /** Whether automatic protective actions may be issued in this state. */
    public boolean allowsAutomaticAction() {
        return this != LOCKOUT;
    }
}
