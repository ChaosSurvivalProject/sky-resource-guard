/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.ai;

import java.time.Duration;
import java.util.Objects;

/**
 * AI configuration (task book 2.3 / 11.4). Display name, provider, base URL,
 * model alias and key reference are kept separate, so the core never binds to a
 * vendor SDK type. Secrets are referenced, never stored or logged.
 *
 * <p><strong>P0 hard rule:</strong> {@code allowExecute} is forced to false and
 * AI is disabled by default. An AI outage must not affect P0/P1 protection.</p>
 */
public record AiConfig(
        boolean enabled,
        String displayName,
        String provider,
        String baseUrl,
        String apiKeyRef,
        String modelAlias,
        String chatPath,
        Duration timeout,
        int maxInflight,
        int queueCapacity,
        boolean allowExecute,
        int promptSchemaVersion) {

    public AiConfig {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(apiKeyRef, "apiKeyRef");
        Objects.requireNonNull(modelAlias, "modelAlias");
        Objects.requireNonNull(chatPath, "chatPath");
        Objects.requireNonNull(timeout, "timeout");
        if (maxInflight < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("bounded queue/concurrency must be >= 1");
        }
        if (!apiKeyRef.startsWith("secret:")) {
            throw new IllegalArgumentException("api key must be a secret reference, not a literal");
        }
        // Advisory only. No execution path exists in P0 by design.
        allowExecute = false;
    }

    public static AiConfig safeDefaults() {
        return new AiConfig(
                false,
                "DeepSeek V4.1 Flash",
                "deepseek",
                "https://api.deepseek.com",
                "secret:sky/ai-api-key",
                "deepseek-flash",
                "/chat/completions",
                Duration.ofMillis(8000),
                2,
                32,
                false,
                1);
    }
}
