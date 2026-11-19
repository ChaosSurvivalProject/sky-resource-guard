/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
/*
 * P1.1 installable plugin. The ONLY external compile dependency is the real,
 * independently re-checkable Paper API coordinate recorded in
 * docs/version-matrix.md. Purpur is a Paper fork, so a plugin built against
 * the Paper public API loads on Purpur; this module deliberately calls NO
 * Purpur-specific API (those keys are still UNRESOLVED).
 *
 * paper-api is compileOnly and MUST NOT be bundled: tools/check-plugin-jar.sh
 * fails the build if any org/bukkit or io/papermc class ends up in the jar.
 */
repositories {
    maven {
        name = "paperMc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    implementation(project(":sky-api"))
    implementation(project(":sky-core"))
    implementation(project(":sky-audit"))
    implementation(project(":sky-plugin-runtime"))
    implementation(project(":adapters:sky-adapter-generated-acme"))
}

tasks.named<org.gradle.api.tasks.bundling.Jar>("jar") {
    archiveFileName.set("SKYResourceGuard-0.1.0-p1.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Gradle 9 requires an explicit task dependency when a jar consumes the
    // outputs of other projects' jar tasks (no implicit dependency allowed).
    dependsOn(configurations.runtimeClasspath)

    // Bundle the pure-Java SKY modules and generated adapters into ONE
    // installable jar. Service registrations stay under this module's own
    // resources (a single authoritative file), so dependency jars' service
    // files are excluded to avoid a non-deterministic merge.
    from({
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    }) {
        exclude(
            "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA",
            "META-INF/MANIFEST.MF", "META-INF/services/**",
            "module-info.class",
        )
    }
}
