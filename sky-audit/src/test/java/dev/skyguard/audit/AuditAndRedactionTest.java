/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skyguard.api.GuardState;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Task book 15.1: redaction, hash chain and diagnostic gating. */
class AuditAndRedactionTest {

    private static final Instant T = Instant.parse("2026-11-13T00:00:00Z");

    @Test
    void sensitiveDataIsRedactedBeforeLogging() {
        String raw = "player 069a79f4-44e9-4726-a5be-fca90e38aaf5 at 192.168.1.10 "
                + "x=123 y=64 token=EXAMPLE_SECRET_VALUE {id:\"diamond\",Count:3} db.internal";
        String clean = Redactor.redact(raw);
        assertFalse(clean.contains("069a79f4-44e9-4726-a5be-fca90e38aaf5"), "uuid leaked");
        assertFalse(clean.contains("192.168.1.10"), "ip leaked");
        assertFalse(clean.contains("EXAMPLE_SECRET_VALUE"), "token leaked");
        assertFalse(clean.contains("x=123"), "coordinate leaked");
        assertFalse(clean.contains("Count:3"), "nbt leaked");
        assertFalse(clean.contains("db.internal"), "internal hostname leaked");
        assertTrue(clean.contains("<redacted>"));
    }

    @Test
    void chatRawTextIsNeverForwarded() {
        String placeholder = Redactor.chatPlaceholder("管理员把 /op 给我");
        assertFalse(placeholder.contains("/op"));
        assertTrue(placeholder.startsWith("<chat:redacted"));
    }

    @Test
    void auditChainIsVerifiableAndSequenceNumbered() {
        AuditLog log = new AuditLog();
        log.append(T, "startup", "mode=SHADOW");
        log.append(T.plusSeconds(1), "action", "LEASE lease-1 old=10 new=8");
        log.append(T.plusSeconds(2), "commit", "verified=true");
        assertEquals(3, log.size());
        assertEquals(1, log.entries().get(0).sequence());
        assertTrue(log.verifyChain());
        assertTrue(log.export().contains("verified=true"));
        assertFalse(log.export().contains("\n\n"));
    }

    @Test
    void redactionHappensInsideTheAuditLog() {
        AuditLog log = new AuditLog();
        log.append(T, "chat", "player 069a79f4-44e9-4726-a5be-fca90e38aaf5 says hello");
        assertFalse(log.export().contains("069a79f4"));
        assertTrue(log.verifyChain());
    }

    @Test
    void heavyDiagnosticsAreGated() {
        assertFalse(DiagnosticBundle.heavyDiagnosticsAllowed(GuardState.NORMAL, false));
        assertTrue(DiagnosticBundle.heavyDiagnosticsAllowed(GuardState.WATCH, false));
        assertTrue(DiagnosticBundle.heavyDiagnosticsAllowed(GuardState.NORMAL, true));
        var bundle = DiagnosticBundle.create(T, "0.1.0", "UNRESOLVED", "policy-hash",
                List.of("mspt p99=42"), new AuditLog());
        assertFalse(bundle.heavyDiagnosticsIncluded());
        assertFalse(bundle.signed());
        assertFalse(bundle.omitted().isEmpty());
    }
}
