/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

import dev.skyguard.api.EvidenceLevel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Correlates chat feedback with telemetry (task book 12.2). At least two
 * independent signal kinds are required before a problem is escalated. If
 * chat volume grows while telemetry is normal, the result explicitly says it
 * may be a client/network-side issue and refuses to lower server parameters.
 */
public final class EvidenceCorrelator {

    public enum SignalKind {
        CHAT_FEEDBACK, TPS_MSPT, TICK_TAIL, WORLD_CHUNK,
        NETWORK_QUEUE, GC_PAUSE, ENTITY_DENSITY, PLUGIN_COST
    }

    public record Signal(SignalKind kind, double value, EvidenceLevel level) {}

    public record Correlation(boolean escalate, EvidenceLevel level, List<String> reasons, String note) {}

    private static final Duration FEEDBACK_COOLDOWN = Duration.ofSeconds(5);

    private final Map<String, Instant> lastByPlayer = new HashMap<>();
    private final Map<String, Instant> lastByText = new HashMap<>();

    /** Per-player rate limit and similar-text de-duplication. */
    public synchronized boolean acceptFeedback(String playerKey, String normalizedText, Instant now) {
        Objects.requireNonNull(playerKey, "playerKey");
        Objects.requireNonNull(now, "now");
        String text = normalizedText == null ? "" : normalizedText.trim().toLowerCase();
        Instant lastPlayer = lastByPlayer.get(playerKey);
        if (lastPlayer != null && Duration.between(lastPlayer, now).compareTo(FEEDBACK_COOLDOWN) < 0) {
            return false;
        }
        Instant lastText = lastByText.get(text);
        if (lastText != null && Duration.between(lastText, now).compareTo(FEEDBACK_COOLDOWN) < 0) {
            return false;
        }
        lastByPlayer.put(playerKey, now);
        lastByText.put(text, now);
        return true;
    }

    public Correlation evaluate(List<Signal> signals) {
        Objects.requireNonNull(signals, "signals");
        Set<SignalKind> kinds = new HashSet<>();
        List<String> reasons = new ArrayList<>();
        EvidenceLevel strongest = EvidenceLevel.L3_GUESS;
        boolean hasAutoGradeEvidence = false;

        for (Signal s : signals) {
            kinds.add(s.kind());
            reasons.add(s.kind() + " value=" + s.value() + " evidence=" + s.level());
            if (s.level().mayTriggerAutomaticRestriction()) {
                hasAutoGradeEvidence = true;
            }
            if (s.level().ordinal() < strongest.ordinal()) {
                strongest = s.level();
            }
        }

        boolean chatOnly = kinds.size() == 1 && kinds.contains(SignalKind.CHAT_FEEDBACK);
        if (chatOnly) {
            return new Correlation(false, EvidenceLevel.L3_GUESS, reasons,
                    "可能是客户端或网络侧，待补充证据；不降低服务端参数");
        }
        if (kinds.size() < 2) {
            return new Correlation(false, strongest, reasons,
                    "证据不足：至少需要两类独立信号，仅保存建议");
        }
        if (!hasAutoGradeEvidence) {
            return new Correlation(false, strongest, reasons,
                    "仅 L2/L3 证据：只告警或进入调查建议，不自动限制");
        }
        return new Correlation(true, strongest, reasons,
                "两类以上独立信号且含 L0/L1 证据，可进入人工审批的修复流水线");
    }
}
