/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
// P0: provider abstraction + schema validation only. No HTTP client, no SDK.
dependencies {
    api(project(":sky-api"))
    implementation(project(":sky-audit"))
}
