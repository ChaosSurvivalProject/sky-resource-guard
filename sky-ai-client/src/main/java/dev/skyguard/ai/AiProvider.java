/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.ai;

import java.util.concurrent.CompletableFuture;

/**
 * Vendor-neutral provider abstraction. The core knows nothing about DeepSeek
 * SDK types; swapping a provider is a configuration change plus an adapter.
 *
 * <p>Implementations MUST: run off the main tick, use a bounded queue, enforce
 * connect/request/total deadlines, cap concurrency, circuit-break, retry with
 * a finite budget, redact input, and fall back to local rules on failure.
 * P0 ships NO implementation and makes NO network call.</p>
 */
public interface AiProvider {

    String id();

    /** The prompt MUST already be redacted. Raw chat text is never accepted. */
    CompletableFuture<String> complete(String redactedPrompt);

    default boolean isAvailable() {
        return false;
    }
}
