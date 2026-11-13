/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

import dev.skyguard.api.Contracts.ActionIntent;
import dev.skyguard.api.Contracts.ActionResult;
import dev.skyguard.api.ErrorCode;
import dev.skyguard.api.GuardState;
import java.time.Instant;
import java.util.Objects;

/**
 * Pure-Java assembly of the guard core. Contains no server type, no AI call
 * and no I/O; it can be exercised in a unit test with a fixed {@link Instant}.
 */
public final class GuardRuntime {

    private final StateThresholds thresholds;
    private final GuardStateMachine stateMachine;
    private final MetricsRingBuffer msptRing;
    private final MsptHistogram histogram;
    private final GuardEventBus eventBus;
    private final LeaseManager leaseManager;
    private final PolicyEngine policyEngine;
    private final BudgetArbiter budgetArbiter;
    private final EvidenceCorrelator evidenceCorrelator;

    private ActionGateway actionGateway;

    public GuardRuntime() {
        this(StateThresholds.initialCandidates());
    }

    public GuardRuntime(StateThresholds thresholds) {
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds");
        this.stateMachine = new GuardStateMachine(thresholds);
        this.msptRing = new MetricsRingBuffer(1024);
        this.histogram = new MsptHistogram(250);
        this.eventBus = new GuardEventBus();
        this.leaseManager = new LeaseManager();
        this.policyEngine = new PolicyEngine();
        this.budgetArbiter = new BudgetArbiter();
        this.evidenceCorrelator = new EvidenceCorrelator();
    }

    /** Register the (possibly platform-specific) action executor. */
    public void installActionGateway(ActionGateway gateway) {
        this.actionGateway = Objects.requireNonNull(gateway, "gateway");
    }

    public void recordTick(double msptMs, Instant now) {
        msptRing.add(msptMs);
        histogram.record(msptMs);
        eventBus.publish("tick.sample", now, "mspt=" + msptMs);
    }

    public GuardState observe(PressureSample sample, Instant now) {
        GuardState before = stateMachine.state();
        GuardState after = stateMachine.observe(sample, now);
        if (before != after) {
            eventBus.publish("state.change", now, before + " -> " + after);
        }
        return after;
    }

    public ActionResult submit(ActionIntent intent, Instant now) {
        if (actionGateway == null) {
            return ActionResult.rejected(intent.intentId(), ErrorCode.OBSERVE_ONLY,
                    "no action executor installed; SHADOW mode is read-only");
        }
        return actionGateway.execute(intent, now);
    }

    /** Read-only SHADOW report. Never changes any state. */
    public String shadowReport(Instant now) {
        StringBuilder sb = new StringBuilder();
        sb.append("SKY Resource Guard - SHADOW report\n");
        sb.append("generatedAt: ").append(now).append('\n');
        sb.append("mode: SHADOW (read-only, no production change)\n");
        sb.append("state: ").append(stateMachine.state()).append('\n');
        sb.append("lockoutReason: ").append(stateMachine.lockoutReason()).append('\n');
        sb.append("samples: ").append(msptRing.size()).append('/').append(msptRing.capacity()).append('\n');
        sb.append("mspt mean/p95/p99: ")
                .append(String.format("%.2f", msptRing.mean())).append(" / ")
                .append(String.format("%.2f", msptRing.percentile(0.95))).append(" / ")
                .append(String.format("%.2f", msptRing.percentile(0.99))).append('\n');
        sb.append("histogram p50/p95/p99: ")
                .append(histogram.percentile(0.50)).append(" / ")
                .append(histogram.percentile(0.95)).append(" / ")
                .append(histogram.percentile(0.99)).append(" ms\n");
        sb.append("histogram overflow: ").append(histogram.overflow()).append('\n');
        sb.append("activeLeases: ").append(leaseManager.activeCount(now)).append('\n');
        sb.append("events: ").append(eventBus.recent().size()).append('\n');
        sb.append("stateHistory:\n");
        for (String line : stateMachine.history()) {
            sb.append("  - ").append(line).append('\n');
        }
        sb.append("NOTE: no performance gain is claimed; this report is telemetry only.\n");
        return sb.toString();
    }

    public StateThresholds thresholds() { return thresholds; }
    public GuardStateMachine stateMachine() { return stateMachine; }
    public MetricsRingBuffer msptRing() { return msptRing; }
    public MsptHistogram histogram() { return histogram; }
    public GuardEventBus eventBus() { return eventBus; }
    public LeaseManager leaseManager() { return leaseManager; }
    public PolicyEngine policyEngine() { return policyEngine; }
    public BudgetArbiter budgetArbiter() { return budgetArbiter; }
    public EvidenceCorrelator evidenceCorrelator() { return evidenceCorrelator; }
}
