/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
// Reference adapter B: cache/database/player-command/world-sync shaped plugin
// reached through ExternalPluginAdapter (public API only, no reflection).
dependencies {
    implementation(project(":sky-api"))
    implementation(project(":sky-plugin-runtime"))
}
