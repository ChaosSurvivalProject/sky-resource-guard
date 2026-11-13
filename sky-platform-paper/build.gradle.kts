/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
// P0: NO Paper artifact is resolved here. The probe is expressed against the
// stable sky-api contracts only. See docs/version-matrix.md (UNRESOLVED).
dependencies {
    api(project(":sky-api"))
    implementation(project(":sky-core"))
}
