/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

/** Four evidence levels for attributing load to an unadapted plugin (5.2). */
public enum EvidenceLevel {
    /** Known fact: plugin self-reported or an explicit platform API. */
    L0_FACT,
    /** Strong correlation: explicit synchronous cost or task record around a call. */
    L1_STRONG,
    /** Weak correlation: thread name, class loader, time correlation. */
    L2_WEAK,
    /** Guess: resource totals and player perception only. */
    L3_GUESS;

    /** Only L0/L1 may be used for automatic restriction. */
    public boolean mayTriggerAutomaticRestriction() {
        return this == L0_FACT || this == L1_STRONG;
    }

    /** L2 is warning-only; L3 may only enter investigation suggestions. */
    public boolean warningOnly() {
        return this == L2_WEAK || this == L3_GUESS;
    }
}
