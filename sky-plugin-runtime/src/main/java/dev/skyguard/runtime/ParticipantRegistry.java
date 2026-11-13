/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.runtime;

import dev.skyguard.api.Contracts.HealthSnapshot;
import dev.skyguard.api.Contracts.ParticipantMetadata;
import dev.skyguard.api.Contracts.SampleBudget;
import dev.skyguard.api.ResourceParticipant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of cooperative participants. SPI calls are isolated: one broken or
 * slow participant must not take down sampling for the others, and must never
 * be "fixed" by interrupting arbitrary JVM threads.
 */
public final class ParticipantRegistry {

    private final Map<String, ResourceParticipant> participants = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();

    public synchronized boolean register(ResourceParticipant participant) {
        ParticipantMetadata meta = participant.metadata();
        if (participants.containsKey(meta.pluginId())) {
            return false;
        }
        participants.put(meta.pluginId(), participant);
        return true;
    }

    public synchronized boolean unregister(String pluginId) {
        return participants.remove(pluginId) != null;
    }

    public synchronized Optional<ResourceParticipant> find(String pluginId) {
        return Optional.ofNullable(participants.get(pluginId));
    }

    public synchronized int size() {
        return participants.size();
    }

    /** Best-effort sampling: a failing participant is recorded, not propagated. */
    public synchronized List<HealthSnapshot> sampleAll(SampleBudget budget) {
        List<HealthSnapshot> out = new ArrayList<>();
        for (Map.Entry<String, ResourceParticipant> e : participants.entrySet()) {
            try {
                out.add(e.getValue().sample(budget));
            } catch (RuntimeException ex) {
                failures.add(e.getKey() + ": " + ex.getClass().getSimpleName());
            }
        }
        return List.copyOf(out);
    }

    public synchronized List<String> failures() {
        return List.copyOf(failures);
    }
}
