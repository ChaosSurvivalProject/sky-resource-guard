/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
plugins {
    application
}

application {
    mainClass.set("dev.skyguard.cli.SkyCli")
}

dependencies {
    implementation(project(":sky-api"))
    implementation(project(":sky-core"))
    implementation(project(":sky-audit"))
    implementation(project(":sky-ai-client"))
    implementation(project(":sky-plugin-runtime"))
    implementation(project(":sky-world"))
    implementation(project(":sky-bootstrap"))
}
