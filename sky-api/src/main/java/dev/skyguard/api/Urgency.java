/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

/** Urgency of a task or action intent. */
public enum Urgency {
    SAFETY,
    CRITICAL,
    NORMAL,
    DEFERABLE,
    OPTIONAL
}
