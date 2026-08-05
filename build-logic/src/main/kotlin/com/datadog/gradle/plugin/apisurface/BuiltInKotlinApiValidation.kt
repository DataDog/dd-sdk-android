/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

/** BCV 0.18.1 only discovers the standalone Kotlin Android plugin. */
internal fun Project.configureBuiltInKotlinApiValidation() {
    val projectName = name
    pluginManager.withPlugin("binary-compatibility-validator") {
        pluginManager.withPlugin("com.android.library") {
            val components = extensions.getByType<LibraryAndroidComponentsExtension>()
            components.onVariants(components.selector().withBuildType("release")) {
                val apiFile = layout.projectDirectory.file("api/$projectName.api")
                val buildApiFile = layout.buildDirectory.file("api/$projectName.api")
                val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
                dependencies.add("bcv-rt-jvm-cp", libs.findLibrary("kotlinMetadataJvm").get())
                val bcvClasspath = configurations.named("bcv-rt-jvm-cp-resolver")
                val apiBuild = tasks.register<KotlinApiBuildTask>("apiBuild") {
                    inputClassesDirs.from(
                        tasks.named<KotlinCompileTool>("compileReleaseKotlin").flatMap { it.destinationDirectory },
                        tasks.named<JavaCompile>("compileReleaseJavaWithJavac").flatMap { it.destinationDirectory }
                    )
                    outputApiFile.set(buildApiFile)
                    runtimeClasspath.from(bcvClasspath)
                }
                tasks.register<DumpJvmApiTask>("apiDump") {
                    generatedApiFile.set(apiBuild.flatMap { it.outputApiFile })
                    destinationApiFile.set(apiFile)
                }
                val apiCheck = tasks.register<KotlinApiCompareTask>("apiCheck") {
                    group = "verification"
                    projectApiFile.set(apiFile)
                    generatedApiFile.set(apiBuild.flatMap { it.outputApiFile })
                }
                tasks.named("check") { dependsOn(apiCheck) }
            }
        }
    }
}
