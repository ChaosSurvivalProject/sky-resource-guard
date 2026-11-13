/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.cli;

import dev.skyguard.ai.AdviceSchemaValidator;
import dev.skyguard.ai.AiConfig;
import dev.skyguard.audit.AuditLog;
import dev.skyguard.bootstrap.SkyBootstrap;
import dev.skyguard.core.GuardRuntime;
import dev.skyguard.core.PressureSample;
import dev.skyguard.runtime.UnknownPluginPolicy;
import dev.skyguard.api.EvidenceLevel;
import java.time.Instant;
import java.util.Random;

/**
 * Read-only command line demo. It starts in SHADOW mode, never writes config,
 * never contacts an AI provider and never touches a world. Run with:
 * {@code ./gradlew :sky-cli:run --args="shadow-report"}.
 */
public final class SkyCli {

    private SkyCli() {}

    public static void main(String[] args) {
        System.out.print(run(args.length == 0 ? "shadow-report" : String.join(" ", args)));
    }

    public static String run(String command) {
        Instant now = Instant.parse("2026-11-13T09:00:00Z");
        GuardRuntime runtime = SkyBootstrap.assemble();
        AuditLog audit = new AuditLog();
        audit.append(now, "startup", SkyBootstrap.check().note());

        return switch (command.trim()) {
            case "status" -> status(now, runtime, audit);
            case "shadow-report" -> shadowReport(now, runtime, audit);
            case "audit query" -> auditQuery(audit);
            case "help" -> help();
            default -> "unknown command: " + command + "\n\n" + help();
        };
    }

    private static String status(Instant now, GuardRuntime runtime, AuditLog audit) {
        StringBuilder sb = new StringBuilder();
        sb.append("SKY Resource Guard / status\n");
        sb.append("mode: SHADOW (read-only)\n");
        sb.append("java: ").append(Runtime.version()).append('\n');
        sb.append("state: ").append(runtime.stateMachine().state()).append('\n');
        sb.append("ai: enabled=").append(AiConfig.safeDefaults().enabled())
                .append(" allowExecute=").append(AiConfig.safeDefaults().allowExecute()).append('\n');
        sb.append("startupChecks:\n");
        SkyBootstrap.check().checks().forEach(c -> sb.append("  - ").append(c).append('\n'));
        sb.append("unresolved:\n");
        SkyBootstrap.check().unresolved().forEach(u -> sb.append("  - ").append(u).append('\n'));
        audit.append(now, "status", "read-only status query");
        return sb.toString();
    }

    private static String shadowReport(Instant now, GuardRuntime runtime, AuditLog audit) {
        // Fixed-seed, deterministic replay. This is NOT a real server benchmark.
        Random random = new Random(42L);
        for (int i = 0; i < 240; i++) {
            double mspt = 14.0 + random.nextGaussian() * 3.0 + (i % 40 == 0 ? 38.0 : 0.0);
            runtime.recordTick(Math.max(1.0, mspt), now.plusMillis(i * 50L));
        }
        for (int i = 0; i < 3; i++) {
            runtime.observe(new PressureSample(30.0, 42.0, 19.6, 0, 0, 0, 3, 20, 0.6),
                    now.plusSeconds(i + 1));
        }
        audit.append(now, "shadow.report", "generated deterministic SHADOW report");

        UnknownPluginPolicy policy = new UnknownPluginPolicy();
        UnknownPluginPolicy.Decision decision = policy.evaluate(
                "legacy-plugin", EvidenceLevel.L2_WEAK, false, false);

        StringBuilder sb = new StringBuilder(runtime.shadowReport(now.plusSeconds(10)));
        sb.append("unknownPluginPolicy: ").append(decision.mode())
                .append(" mayAutoRestrict=").append(decision.mayAutoRestrict())
                .append(" mayForceThreadStop=").append(decision.mayForceThreadStop()).append('\n');
        sb.append("adviceValidator: allow-execute is always false -> ")
                .append(new AdviceSchemaValidator().validate(null).executable()).append('\n');
        sb.append("auditChainValid: ").append(audit.verifyChain()).append('\n');
        return sb.toString();
    }

    private static String auditQuery(AuditLog audit) {
        StringBuilder sb = new StringBuilder("audit events=").append(audit.size())
                .append(" chainValid=").append(audit.verifyChain()).append('\n');
        audit.entries().forEach(e -> sb.append(e.sequence()).append(' ')
                .append(e.type()).append(' ').append(e.payload()).append('\n'));
        return sb.toString();
    }

    private static String help() {
        return String.join("\n",
                "SKY CLI (read-only, P0)",
                "  status         - startup checks, modes, unresolved facts",
                "  shadow-report  - deterministic SHADOW telemetry report (default)",
                "  audit query    - hash-chained audit log",
                "  help           - this text",
                "") ;
    }
}
