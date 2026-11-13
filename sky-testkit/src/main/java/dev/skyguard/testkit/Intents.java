/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.testkit;

import dev.skyguard.api.ActionKind;
import dev.skyguard.api.Contracts.ActionIntent;
import dev.skyguard.api.EvidenceLevel;
import dev.skyguard.api.Permissions;
import dev.skyguard.api.SideEffect;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Factory for well-formed action intents used by core tests. */
public final class Intents {

    private Intents() {}

    public static ActionIntent valid(ActionKind kind) {
        return new ActionIntent(
                "intent-" + kind.name(),
                kind,
                "world:overworld",
                "admin-test",
                Set.of(Permissions.ACTION_SAFE, Permissions.ACTION_EXPENSIVE,
                        Permissions.APPROVE_STANDARD, Permissions.APPROVE_CRITICAL),
                "10",
                "8",
                Duration.ofSeconds(30),
                Set.<SideEffect>of(),
                EvidenceLevel.L0_FACT,
                List.of("evidence:test"),
                false,
                false,
                "restore:simulation-distance=10");
    }

    public static ActionIntent with(ActionIntent base,
                                    Set<String> permissions,
                                    boolean dryRun,
                                    boolean approved) {
        return new ActionIntent(base.intentId(), base.kind(), base.target(), base.initiator(),
                permissions, base.oldValue(), base.newValue(), base.ttl(), base.sideEffects(),
                base.evidence(), base.evidenceRefs(), dryRun, approved, base.rollbackAction());
    }
}
