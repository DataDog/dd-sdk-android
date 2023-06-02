/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.android.build.gradle.tasks.factory.AndroidUnitTest
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

fun Project.kotlinConfig(
    evaluateWarningsAsErrors: Boolean = true,
    jvmBytecodeTarget: JvmTarget = JvmTarget.JVM_17
) {
    taskConfig<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(jvmBytecodeTarget)
            allWarningsAsErrors.set(evaluateWarningsAsErrors)
            apiVersion.set(KotlinVersion.KOTLIN_1_6)
            languageVersion.set(KotlinVersion.KOTLIN_1_6)
        }
    }

    val moduleName = this@kotlinConfig.name
    val javaAgentJar = File(File(rootDir, "libs"), "dd-java-agent-0.98.1.jar")
    taskConfig<AndroidUnitTest> {
        if (environment["DD_INTEGRATION_JUNIT_5_ENABLED"] == "true") {
            val variant = variantName.substringBeforeLast("UnitTest")

            // set the `env` tag for the test spans
            environment("DD_ENV", "ci")
            // add custom tags based on the module and variant (debug/release, flavors, …)
            environment("DD_TAGS", "test.module:$moduleName,test.variant:$variant")

            // disable other Datadog integrations that could interact with the Java Agent
            environment("DD_INTEGRATIONS_ENABLED", "false")
            // disable JMX integration
            environment("DD_JMX_FETCH_ENABLED", "false")

            jvmArgs("-javaagent:${javaAgentJar.absolutePath}")
        }
    }
}
