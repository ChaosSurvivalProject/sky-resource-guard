/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
plugins {
    application
}

application {
    mainClass.set("dev.skyguard.benchmarks.DeterministicReplay")
}

// Deterministic, fixed-seed replay (task book 15.4). No JMH dependency yet:
// JMH and a real server baseline belong to P1 and must report real numbers.
dependencies {
    implementation(project(":sky-api"))
    implementation(project(":sky-core"))
    implementation(project(":sky-audit"))
    implementation(project(":sky-testkit"))
}
