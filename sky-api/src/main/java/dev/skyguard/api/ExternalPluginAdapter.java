/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.api;

import dev.skyguard.api.Contracts.AdapterMetadata;
import dev.skyguard.api.Contracts.CheckpointHandle;
import dev.skyguard.api.Contracts.CompatibilityReport;
import dev.skyguard.api.Contracts.Deadline;
import dev.skyguard.api.Contracts.Lease;
import dev.skyguard.api.Contracts.PluginRuntimeSnapshot;
import dev.skyguard.api.Contracts.PowerHandle;
import dev.skyguard.api.Contracts.QuiesceHandle;
import dev.skyguard.api.Contracts.QuiesceRequest;
import dev.skyguard.api.Contracts.ResumeReport;
import dev.skyguard.api.Contracts.ResumeToken;
import dev.skyguard.api.Contracts.ResourceSample;
import dev.skyguard.api.Contracts.SampleBudget;
import java.util.Optional;

/**
 * SPI for an optional, independently versioned adapter targeting an unadapted
 * third-party plugin (task book 5.5).
 *
 * <p>An adapter may only use a plugin's <em>public</em> API, public events and
 * documented commands. It must NEVER stop threads, reflect into private fields,
 * clear internal queues or force-unload a plugin. Every capability is optional:
 * returning {@link Optional#empty()} means "not supported, stay read-only".</p>
 */
public interface ExternalPluginAdapter {

    AdapterMetadata metadata();

    CompatibilityReport probe(PluginRuntimeSnapshot runtime);

    Optional<ResourceSample> sample(SampleBudget budget);

    Optional<PowerHandle> requestLowerPower(PowerLevel level, Lease lease);

    Optional<CheckpointHandle> checkpoint(Deadline deadline);

    Optional<QuiesceHandle> quiesce(QuiesceRequest request);

    ResumeReport resume(ResumeToken token);
}
