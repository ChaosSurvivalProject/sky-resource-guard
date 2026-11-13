/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Best-effort redaction (task book 11.4 / 13). Logs, error reports and AI
 * requests must not contain UUIDs, IPs, chat text, coordinates, item NBT,
 * tokens, passwords or internal hostnames.
 *
 * <p>This is a defence-in-depth helper, not a guarantee: producers must still
 * avoid emitting raw sensitive values in the first place. Chat text is never
 * sent through {@link #redact(String)} - use {@link #chatPlaceholder(String)}.</p>
 */
public final class Redactor {

    private static final Pattern UUID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern IPV4 = Pattern.compile(
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)\\b(api[-_]?key|token|password|passwd|secret|authorization|bearer)\\b\\s*[=:]\\s*\\S+");
    private static final Pattern COORDINATE = Pattern.compile(
            "(?i)\\b(x|y|z)\\s*[=:]\\s*-?\\d+(?:\\.\\d+)?");
    private static final Pattern NBT_LIKE = Pattern.compile(
            "(?s)\\{[^{}]{0,512}\\b(?:id|Count|tag|display|Enchantments|CustomName)\\b[^{}]{0,512}\\}");
    private static final Pattern INTERNAL_HOST = Pattern.compile(
            "(?i)\\b[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:internal|local|lan|corp|priv|intra)\\b");

    private static final int MAX_SUMMARY = 512;

    private Redactor() {}

    public static String redact(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String out = input;
        out = SECRET.matcher(out).replaceAll("$1=<redacted>");
        out = UUID.matcher(out).replaceAll("<uuid>");
        out = IPV4.matcher(out).replaceAll("<ip>");
        out = NBT_LIKE.matcher(out).replaceAll("<nbt>");
        out = COORDINATE.matcher(out).replaceAll("<coords>");
        out = INTERNAL_HOST.matcher(out).replaceAll("<host>");
        return out;
    }

    /** Chat raw text is NEVER forwarded; only a length placeholder is kept. */
    public static String chatPlaceholder(String rawChat) {
        if (rawChat == null || rawChat.isBlank()) {
            return "<chat:empty>";
        }
        return "<chat:redacted len=" + rawChat.length() + ">";
    }

    public static String safeSummary(String input) {
        String clean = redact(input);
        return clean.length() <= MAX_SUMMARY ? clean : clean.substring(0, MAX_SUMMARY) + "...<truncated>";
    }

    public static Map<String, String> redactAll(Map<String, String> fields) {
        Map<String, String> out = new LinkedHashMap<>();
        fields.forEach((k, v) -> out.put(k, redact(v)));
        return out;
    }
}
