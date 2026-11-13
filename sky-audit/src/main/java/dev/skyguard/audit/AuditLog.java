/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Append-only, sequence-numbered, hash-chained event log (task book 10 / 13).
 * Every event is redacted before being stored. {@link #verifyChain()} detects a
 * gap or tamper; a failed verification must be treated as a security incident.
 */
public final class AuditLog {

    public static final String GENESIS = "0".repeat(64);

    public record Entry(
            long sequence, Instant at, String type,
            String payload, String previousHash, String hash) {}

    private final List<Entry> entries = new ArrayList<>();
    private String lastHash = GENESIS;

    public synchronized Entry append(Instant at, String type, String payload) {
        String clean = Redactor.redact(payload);
        long sequence = entries.size() + 1L;
        String hash = sha256(lastHash + "|" + sequence + "|" + at + "|" + type + "|" + clean);
        Entry entry = new Entry(sequence, at, type, clean, lastHash, hash);
        entries.add(entry);
        lastHash = hash;
        return entry;
    }

    public synchronized boolean verifyChain() {
        String expected = GENESIS;
        long expectedSeq = 1L;
        for (Entry e : entries) {
            if (e.sequence() != expectedSeq
                    || !expected.equals(e.previousHash())) {
                return false;
            }
            String recomputed = sha256(expected + "|" + e.sequence() + "|" + e.at()
                    + "|" + e.type() + "|" + e.payload());
            if (!recomputed.equals(e.hash())) {
                return false;
            }
            expected = e.hash();
            expectedSeq++;
        }
        return true;
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    public synchronized int size() {
        return entries.size();
    }

    /** Compact, escaped, one-entry-per-line export for a diagnostic bundle. */
    public synchronized String export() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            sb.append(e.sequence()).append('\t').append(e.at()).append('\t')
                    .append(escape(e.type())).append('\t')
                    .append(escape(e.payload())).append('\t')
                    .append(e.hash()).append('\n');
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\"", "\\\"");
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
