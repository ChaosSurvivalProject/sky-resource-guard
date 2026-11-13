/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

/** Stable, versioned error codes for the public API. */
public enum ErrorCode {
    OK,
    CAPABILITY_MISSING,
    PERMISSION_DENIED,
    DEPENDENCY_UNSATISFIED,
    UNSAFE_RANGE,
    DRY_RUN_ONLY,
    LEASE_CONFLICT,
    LEASE_EXPIRED,
    VERIFICATION_FAILED,
    ROLLBACK_FAILED,
    LOCKOUT,
    TIMEOUT,
    SCHEMA_INVALID,
    UNAUTHORIZED_ACTION,
    OBSERVE_ONLY,
    UNKNOWN_CONFIG_KEY,
    INTERNAL_ERROR
}
