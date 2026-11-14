/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skyguard.api.ActionKind;
import dev.skyguard.api.Contracts.ActionIntent;
import dev.skyguard.api.ErrorCode;
import dev.skyguard.api.EvidenceLevel;
import dev.skyguard.api.GuardState;
import dev.skyguard.api.ServiceTier;
import dev.skyguard.api.SideEffect;
import dev.skyguard.core.EvidenceCorrelator.Signal;
import dev.skyguard.core.EvidenceCorrelator.SignalKind;
import dev.skyguard.testkit.Intents;
import dev.skyguard.testkit.TickReplay;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Ring buffer, histogram, evidence correlation, budget and hard constraints. */
class MetricsAndPolicyTest {

    private static final Instant T = Instant.parse("2026-11-13T00:00:00Z");

    @Test
    void ringBufferKeepsOnlyLastCapacityValues() {
        MetricsRingBuffer buffer = new MetricsRingBuffer(4);
        for (double v : List.of(1.0, 2.0, 3.0, 4.0, 5.0)) {
            buffer.add(v);
        }
        assertEquals(4, buffer.size());
        assertEquals(3.5, buffer.mean(), 1e-9);
        assertEquals(5.0, buffer.percentile(1.0), 1e-9);
        assertEquals(2.0, buffer.snapshot()[0], 1e-9);
    }

    @Test
    void histogramPercentilesAndOverflow() {
        MsptHistogram histogram = new MsptHistogram(200);
        TickReplay.msptSeries(7L, 500, 20.0, 0).forEach(histogram::record);
        histogram.record(500.0);
        assertEquals(501, histogram.count());
        assertEquals(1, histogram.overflow());
        assertTrue(histogram.percentile(0.99) <= 200);
    }

    @Test
    void chatOnlyFeedbackNeverEscalates() {
        EvidenceCorrelator correlator = new EvidenceCorrelator();
        var result = correlator.evaluate(List.of(
                new Signal(SignalKind.CHAT_FEEDBACK, 12, EvidenceLevel.L3_GUESS)));
        assertFalse(result.escalate());
        assertTrue(result.note().contains("客户端或网络侧"));
    }

    @Test
    void twoIndependentSignalsWithStrongEvidenceEscalate() {
        EvidenceCorrelator correlator = new EvidenceCorrelator();
        var result = correlator.evaluate(List.of(
                new Signal(SignalKind.TPS_MSPT, 62, EvidenceLevel.L0_FACT),
                new Signal(SignalKind.TICK_TAIL, 90, EvidenceLevel.L1_STRONG)));
        assertTrue(result.escalate());
        assertEquals(EvidenceLevel.L0_FACT, result.level());
    }

    @Test
    void weakEvidenceIsWarningOnlyEvenWithTwoSignals() {
        EvidenceCorrelator correlator = new EvidenceCorrelator();
        var result = correlator.evaluate(List.of(
                new Signal(SignalKind.CHAT_FEEDBACK, 8, EvidenceLevel.L2_WEAK),
                new Signal(SignalKind.ENTITY_DENSITY, 900, EvidenceLevel.L2_WEAK)));
        assertFalse(result.escalate());
        assertTrue(result.note().contains("只告警"));
    }

    @Test
    void feedbackIsRateLimitedAndDeduplicated() {
        EvidenceCorrelator correlator = new EvidenceCorrelator();
        assertTrue(correlator.acceptFeedback("p1", "好卡", T));
        assertFalse(correlator.acceptFeedback("p1", "还是很卡", T.plusSeconds(1)));
        assertFalse(correlator.acceptFeedback("p2", "好卡", T.plusSeconds(1)));
        assertTrue(correlator.acceptFeedback("p3", "区块加载慢", T.plusSeconds(6)));
    }

    @Test
    void coreFloorBudgetCannotBeConsumedByOtherTiers() {
        BudgetArbiter arbiter = new BudgetArbiter();
        for (int i = 0; i < 200; i++) {
            arbiter.tryAcquire(ServiceTier.BACKGROUND, 50, T);
        }
        assertTrue(arbiter.tryAcquire(ServiceTier.CORE, 500, T));
        assertFalse(arbiter.tryAcquire(ServiceTier.BACKGROUND, 100000, T));
    }

    @Test
    void priorityScoreCannotOverrideHardConstraints() {
        PolicyEngine engine = new PolicyEngine();
        ActionIntent base = Intents.valid(ActionKind.DROP_MERGE);
        ActionIntent irreversible = new ActionIntent(base.intentId(), base.kind(), base.target(),
                base.initiator(), base.heldPermissions(), base.oldValue(), base.newValue(),
                base.ttl(), Set.of(SideEffect.IRREVERSIBLE), EvidenceLevel.L0_FACT,
                base.evidenceRefs(), false, true, base.rollbackAction());
        var decision = engine.evaluate(irreversible, GuardState.CRITICAL,
                PolicyEngine.PolicyContext.healthy(dev.skyguard.api.CapabilitySet.none()));
        assertFalse(decision.allowed());
        assertEquals(ErrorCode.UNAUTHORIZED_ACTION, decision.code());
        assertTrue(Double.isFinite(PolicyEngine.priorityScore(1, 1, 1, 1, 0, 0)));
    }

    @Test
    void ringBufferMaxUsesLiveWindowAndHandlesNegativeValues() {
        MetricsRingBuffer buffer = new MetricsRingBuffer(3);
        for (double v : List.of(-5.0, -2.0, -9.0, -1.0)) {
            buffer.add(v);
        }
        // After the wrap the live window is [-2, -9, -1]; the evicted -5 and
        // the old "start at 0" behaviour must not affect the result.
        assertEquals(3, buffer.size());
        assertEquals(-1.0, buffer.max(), 1e-9);
    }

    @Test
    void ringBufferMeanMatchesItsSnapshotAfterManyWraps() {
        MetricsRingBuffer buffer = new MetricsRingBuffer(8);
        for (int i = 0; i < 1000; i++) {
            buffer.add(1_000_000.0 + (i % 17) * 0.37);
        }
        double[] snap = buffer.snapshot();
        double expected = 0.0;
        for (double v : snap) {
            expected += v;
        }
        expected /= snap.length;
        assertEquals(expected, buffer.mean(), 1e-9,
                "incremental += / -= must not drift away from the live window");
    }

    @Test
    void emptyRingBufferReportsZeroMax() {
        MetricsRingBuffer buffer = new MetricsRingBuffer(4);
        assertEquals(0.0, buffer.max(), 1e-9);
        assertEquals(0.0, buffer.mean(), 1e-9);
    }
}
