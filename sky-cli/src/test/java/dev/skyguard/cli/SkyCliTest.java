/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyCliTest {

    @Test
    void shadowReportIsReadOnlyAndDeclaresNoPerformanceClaim() {
        String report = SkyCli.run("shadow-report");
        assertTrue(report.contains("SHADOW"));
        assertTrue(report.contains("no performance gain is claimed"));
        assertTrue(report.contains("auditChainValid: true"));
    }

    @Test
    void statusListsUnresolvedFacts() {
        String status = SkyCli.run("status");
        assertTrue(status.contains("mode: SHADOW"));
        assertTrue(status.contains("UNRESOLVED") || status.contains("Purpur 1.21.11 concrete build"));
        assertTrue(status.contains("allowExecute=false"));
    }

    @Test
    void auditQueryShowsAValidChain() {
        assertTrue(SkyCli.run("audit query").contains("chainValid=true"));
    }

    @Test
    void unknownCommandExecutesNothing() {
        String output = SkyCli.run("/sky plan apply lease-1");
        assertTrue(output.startsWith("unknown command"));
        assertTrue(output.contains("read-only"));
    }
}
