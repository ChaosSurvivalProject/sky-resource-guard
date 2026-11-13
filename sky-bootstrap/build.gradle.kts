/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
// P0: pure-Java assembly and compatibility check. NOT a Bukkit plugin main
// class yet - there is no plugin.yml and no server lifecycle in this version.
dependencies {
    implementation(project(":sky-api"))
    implementation(project(":sky-core"))
    implementation(project(":sky-audit"))
    implementation(project(":sky-ai-client"))
    implementation(project(":sky-plugin-runtime"))
    implementation(project(":sky-world"))
    implementation(project(":sky-platform-paper"))
    implementation(project(":sky-platform-purpur"))
}
