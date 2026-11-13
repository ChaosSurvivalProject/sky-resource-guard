/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Immutable set of declared capabilities. */
public final class CapabilitySet {

    private static final CapabilitySet EMPTY = new CapabilitySet(EnumSet.noneOf(Capability.class));

    private final Set<Capability> values;

    private CapabilitySet(Set<Capability> values) {
        this.values = Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    public static CapabilitySet none() {
        return EMPTY;
    }

    public static CapabilitySet of(Capability... capabilities) {
        if (capabilities.length == 0) {
            return EMPTY;
        }
        return new CapabilitySet(EnumSet.copyOf(java.util.Arrays.asList(capabilities)));
    }

    public boolean has(Capability capability) {
        return values.contains(capability);
    }

    public boolean hasAll(Capability... required) {
        for (Capability c : required) {
            if (!values.contains(c)) {
                return false;
            }
        }
        return true;
    }

    public Set<Capability> asSet() {
        return values;
    }

    public int size() {
        return values.size();
    }
}
