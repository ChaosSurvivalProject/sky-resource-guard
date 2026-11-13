/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

/** Cooperative lower-power levels; a participant may refuse. */
public enum PowerLevel {
    FULL,
    REDUCED,
    LOW,
    MINIMAL
}
