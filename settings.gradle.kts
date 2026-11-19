/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
rootProject.name = "sky-resource-guard"

/*
 * SKY Resource Guard - Gradle multi-module layout (see docs/architecture.md).
 *
 * P0 modules are pure Java and MUST build without a Minecraft server,
 * without Paper/Purpur artifacts and without network access to an AI provider.
 */
include(
    ":sky-api",
    ":sky-core",
    ":sky-platform-paper",
    ":sky-platform-purpur",
    ":sky-world",
    ":sky-plugin-runtime",
    ":sky-ai-client",
    ":sky-audit",
    ":sky-cli",
    ":sky-bootstrap",
    ":sky-testkit",
    ":sky-benchmarks",
    ":adapters:sky-adapter-selfexample-a",
    ":adapters:sky-adapter-selfexample-b",
    ":adapters:sky-adapter-generated-acme",
    ":sky-plugin-paper",
)
