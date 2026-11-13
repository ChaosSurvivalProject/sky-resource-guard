/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

import dev.skyguard.api.Contracts.ActionIntent;
import dev.skyguard.api.Contracts.Lease;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Time-bounded, single-target leases (task book 10). A second action on the
 * same target is rejected while a lease is active, preventing two concurrent
 * writes from racing. Expired leases are purged and their old state restored by
 * the caller's rollback path.
 */
public final class LeaseManager {

    private final Map<String, Lease> active = new LinkedHashMap<>();

    public synchronized Optional<Lease> acquire(ActionIntent intent, Instant now) {
        expire(now);
        if (hasConflict(intent.target(), now)) {
            return Optional.empty();
        }
        String leaseId = "lease-" + UUID.randomUUID();
        String checksum = computeChecksum(
                leaseId, intent.target(), intent.intentId(), intent.initiator(),
                String.valueOf(intent.oldValue()), String.valueOf(intent.newValue()),
                now.toString(), intent.ttl().toString());
        Lease lease = new Lease(
                leaseId,
                intent.target(),
                intent.intentId(),
                intent.initiator(),
                intent.oldValue(),
                intent.newValue(),
                now,
                intent.ttl(),
                "verify:" + intent.kind(),
                intent.rollbackAction(),
                checksum,
                intent.evidenceRefs());
        active.put(leaseId, lease);
        return Optional.of(lease);
    }

    public synchronized boolean release(String leaseId, Instant now) {
        expire(now);
        return active.remove(leaseId) != null;
    }

    public synchronized Optional<Lease> find(String leaseId, Instant now) {
        expire(now);
        return Optional.ofNullable(active.get(leaseId));
    }

    public synchronized boolean hasConflict(String target, Instant now) {
        expire(now);
        return active.values().stream().anyMatch(l -> l.target().equals(target));
    }

    /** Purge expired leases. Returns how many were removed. */
    public synchronized int expire(Instant now) {
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Lease> e : active.entrySet()) {
            if (e.getValue().isExpired(now)) {
                expired.add(e.getKey());
            }
        }
        expired.forEach(active::remove);
        return expired.size();
    }

    public synchronized int activeCount(Instant now) {
        expire(now);
        return active.size();
    }

    public static String computeChecksum(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update((part == null ? "" : part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK; unreachable in practice.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
