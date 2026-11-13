/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
// Depends only on sky-api so that sky-core tests can use it without a cycle.
dependencies {
    api(project(":sky-api"))
}
