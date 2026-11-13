/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.ai;

import dev.skyguard.api.ActionKind;
import dev.skyguard.api.Contracts.AdviceProposal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates AI advice before it can even be stored as a proposal (task book
 * 11.3). Low confidence, conflicting evidence, an invalid shape, unregistered
 * actions or privilege-escalation text mean: <strong>save the advice only,
 * never execute</strong>.
 *
 * <p>P0 has no JSON parser dependency; object-level validation plus raw-string
 * injection scanning are implemented here, and the JSON Schema file is
 * validated in CI. P3's provider layer will parse JSON with a reviewed
 * dependency before calling this validator.</p>
 */
public final class AdviceSchemaValidator {

    public record Verdict(boolean valid, boolean executable, List<String> violations) {
        public static Verdict ok() {
            return new Verdict(true, false, List.of("advisory only; allow-execute=false"));
        }
    }

    private static final Set<String> ALLOWED_DRY_RUNS = Arrays.stream(ActionKind.values())
            .filter(k -> !k.isHighRisk())
            .map(Enum::name)
            .collect(Collectors.toSet());

    private static final List<String> INJECTION_MARKERS = List.of(
            "ignore previous instructions", "ignore all previous", "system prompt",
            "you are now", "/op ", "/sky plan apply", "/sky override", "sudo ",
            "rm -rf", "drop table", "grant op", "execute as admin");

    /** Object-level validation. Never returns executable=true. */
    public Verdict validate(AdviceProposal proposal) {
        List<String> violations = new ArrayList<>();
        if (proposal == null) {
            return new Verdict(false, false, List.of("null proposal"));
        }
        if (Double.isNaN(proposal.confidence()) || proposal.confidence() < 0.0
                || proposal.confidence() > 1.0) {
            violations.add("confidence out of [0,1]");
        }
        if (proposal.risk() == null) {
            violations.add("risk missing");
        }
        if (proposal.hypotheses().isEmpty() && proposal.missingEvidence().isEmpty()) {
            violations.add("neither hypotheses nor missingEvidence provided");
        }
        for (String action : proposal.recommendedDryRuns()) {
            if (!ALLOWED_DRY_RUNS.contains(action)) {
                violations.add("unregistered or high-risk action: " + action);
            }
        }
        for (String text : proposal.hypotheses()) {
            if (containsInjection(text)) {
                violations.add("prompt-injection marker in hypothesis");
            }
        }
        if (proposal.rollback() == null || proposal.rollback().isBlank()) {
            violations.add("rollback description missing");
        }
        return violations.isEmpty()
                ? Verdict.ok()
                : new Verdict(false, false, List.copyOf(violations));
    }

    /** Heuristic raw-payload scan for obvious injection / privilege escalation. */
    public boolean containsInjection(String raw) {
        if (raw == null) {
            return false;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        return INJECTION_MARKERS.stream().anyMatch(lower::contains);
    }

    public Set<String> allowedDryRuns() {
        return ALLOWED_DRY_RUNS;
    }
}
