/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal fun Project.kotlinConfig(
    evaluateWarningsAsErrors: Boolean = true,
    jvmBytecodeTarget: JvmTarget = JvmTarget.JVM_17,
    kotlinApiVersion: KotlinVersion = KotlinVersion.KOTLIN_2_0,
    kotlinLanguageVersion: KotlinVersion = KotlinVersion.KOTLIN_2_0
) {
    val jdkVersion = extensions.getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion("jdk")
        .orElseThrow { IllegalStateException("No `jdk` version found in the version catalog") }
        .requiredVersion
        .toInt()
    when (val kotlin = extensions.getByName("kotlin")) {
        is KotlinJvmExtension -> {
            kotlin.jvmToolchain(jdkVersion)
            // Compile against the class library of the bytecode target rather than the toolchain one,
            // so that APIs newer than the bytecode target can't be used.
            taskConfig<KotlinJvmCompile> {
                compilerOptions {
                    freeCompilerArgs.add("-Xjdk-release=${jvmBytecodeTarget.target}")
                }
            }
            taskConfig<JavaCompile> {
                options.release.set(jvmBytecodeTarget.target.toInt())
            }
        }

        // Android modules compile against android.jar, which is incompatible with
        // -Xjdk-release / --release, Java target is set by android.compileOptions instead.
        is KotlinAndroidExtension -> kotlin.jvmToolchain(jdkVersion)

        else -> error("Unsupported Kotlin extension type: ${kotlin::class.java.name}")
    }

    val isCI = providers.environmentVariable("CI")
        .map { it.toBoolean() }
        .orElse(false)
    taskConfig<KotlinJvmCompile> {
        compilerOptions {
            jvmTarget.set(jvmBytecodeTarget)
            allWarningsAsErrors.set(isCI.map { evaluateWarningsAsErrors && it })
            apiVersion.set(kotlinApiVersion)
            languageVersion.set(kotlinLanguageVersion)
        }
    }
}
