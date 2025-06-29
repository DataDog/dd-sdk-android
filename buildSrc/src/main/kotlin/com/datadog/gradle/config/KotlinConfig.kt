/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun Project.kotlinConfig(
    evaluateWarningsAsErrors: Boolean = true,
    jvmBytecodeTarget: JvmTarget = JvmTarget.JVM_17
) {
    taskConfig<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(jvmBytecodeTarget)
            val isCI = System.getenv("CI").toBoolean()
            allWarningsAsErrors.set(evaluateWarningsAsErrors && isCI)
            apiVersion.set(KotlinVersion.KOTLIN_1_7)
            languageVersion.set(KotlinVersion.KOTLIN_1_7)
        }
    }
}
