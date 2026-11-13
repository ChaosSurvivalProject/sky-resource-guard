/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

/** Operating modes. First install MUST default to {@link #SHADOW}. */
public enum RunMode {
    /** Read-only observation and reporting. No production change. */
    SHADOW,
    /** Observation plus advisory output, still no production change. */
    ADVISE,
    /** Safe, reversible, leased protective actions only. */
    PROTECT,
    /** Full automation, still bound by policy, permissions and hard constraints. */
    AUTOMATE
}
