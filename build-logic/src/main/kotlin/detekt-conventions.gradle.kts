import com.android.build.api.variant.LibraryAndroidComponentsExtension
import io.gitlab.arturbosch.detekt.Detekt

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

plugins {
    id("io.gitlab.arturbosch.detekt")
}

dependencies {
    detektPlugins(project(":tools:detekt"))
}

val detektConfigFiles = listOf(
    rootProject.file("detekt.yml"),
    rootProject.file("detekt_custom_safe_calls_android.yml"),
    rootProject.file("detekt_custom_safe_calls_third_party.yml"),
    rootProject.file("detekt_custom_unsafe_calls.yml")
)

detekt {
    config.setFrom(detektConfigFiles)
    ignoredVariants = listOf("release")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        sarif.required = false
        html.required = false
        xml.required = false
        txt.required = false
        md.required = false
    }
}

// Disable the default task named "detekt" since it does not run type resolution
// Use it purely as a nicely named umbrella task
tasks.detekt {
    isEnabled = false

    dependsOn(
        tasks.named("detektMain"),
        tasks.named("detektTest")
    )
}

// testFixtures is an Android source set and isn't picked up by the detekt/AGP on AGP 8, this should
// be able to be deleted in AGP 9 and covered by `detektTest`
extensions.getByType<LibraryAndroidComponentsExtension>().onVariants { variant ->
    if (variant.name == "debug") {
        variant.testFixtures?.let { testFixtures ->
            val detektTestFixtures = tasks.register<Detekt>("detektTestFixtures") {
                source(layout.projectDirectory.dir("src/testFixtures/kotlin"))
                config.setFrom(detektConfigFiles)
                classpath.setFrom(testFixtures.compileClasspath)
            }
            tasks.detekt {
                dependsOn(detektTestFixtures)
            }
        }
    }
}
