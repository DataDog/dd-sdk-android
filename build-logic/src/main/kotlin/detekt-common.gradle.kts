/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.android.build.api.dsl.TestedExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.DeviceTest
import dev.detekt.gradle.Detekt

plugins {
    id("dev.detekt")
}

detekt {
    config.setFrom(rootProject.file("detekt.yml"))
    basePath = rootProject.layout.projectDirectory
}

listOf("com.android.application", "com.android.library").forEach { pluginId ->
    pluginManager.withPlugin(pluginId) {
        val releaseBuildType = "release"
        val androidComponents = extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.finalizeDsl { android ->
            // Shared androidTest sources may run only against release, depending on testBuildType.
            val testBuildType = (android as? TestedExtension)?.testBuildType
            val releaseSources = fileTree("src") {
                include("release*/**/*.kt", "*Release*/**/*.kt", "release*/**/*.kts", "*Release*/**/*.kts")
            }
            if (releaseSources.isEmpty && testBuildType != releaseBuildType) {
                detekt.ignoredBuildTypes.add(releaseBuildType)
            }
        }

        val sourceRoot = layout.projectDirectory.dir("src").asFile
        androidComponents.onVariants(androidComponents.selector().withBuildType(releaseBuildType)) { variant ->
            // Debug covers shared sources. Device tests may exist only for release and need their full suite.
            (listOf(variant) + variant.nestedComponents).filterNot { it is DeviceTest }.forEach { component ->
                val detektTaskName = "detekt${component.name.replaceFirstChar { it.uppercaseChar() }}"
                tasks.withType<Detekt>().named { it == detektTaskName }.configureEach {
                    exclude { element ->
                        val sourceSet = element.file.relativeTo(sourceRoot)
                            .invariantSeparatorsPath.substringBefore('/')
                        !sourceSet.startsWith(releaseBuildType) && !sourceSet.contains("Release")
                    }
                }
            }
        }
    }
}

tasks.withType<Detekt>().configureEach {
    reports {
        sarif.required = false
        html.required = false
        markdown.required = false
        checkstyle.required = false
    }
}

// Use the compilation tasks so rules requiring type resolution also run on tests and fixtures.
// JVM fixtures have a separate task; Android fixtures are already dependencies of detektTest.
tasks.named<Detekt>("detekt") {
    isEnabled = false
    dependsOn(tasks.named { it in setOf("detektMain", "detektTest", "detektTestFixtures") })
}
