/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
}

group = "dev.skyguard"
version = providers.gradleProperty("sky.version").getOrElse("0.1.0-SNAPSHOT")

/*
 * Centralised, minimal configuration. Every subproject is a plain java-library.
 * P0 deliberately has NO server-side repository declared: no Paper/Purpur
 * artifact is resolved until a concrete, verifiable build is pinned in
 * docs/version-matrix.md. Fabricating coordinates is explicitly forbidden.
 */
subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
        // repo.papermc.io / repo.purpurmc.org are intentionally NOT enabled yet.
        // See docs/version-matrix.md (status: UNRESOLVED).
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    // CI runs `./gradlew javadoc`. Doclint is relaxed so that a missing tag
    // never breaks a P0 build, while real compilation errors still do.
    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }
}
