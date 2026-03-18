/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.withType

fun Project.junitConfig() {
    tasks.withType<Test>().configureEach {
        jvmArgs(
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED"
        )
        useJUnitPlatform {
            includeEngines("spek", "junit-jupiter", "junit-vintage")
        }
        reports {
            junitXml.required.set(true)
            html.required.set(true)
        }
        testLogging {
            events(TestLogEvent.FAILED)

            // Test assumptions log to stderr if assumption fails, so we have the manual control of stderr
            val stderrBuffers = mutableMapOf<String, StringBuilder>()

            addTestOutputListener { descriptor, event ->
                if (event.destination == TestOutputEvent.Destination.StdErr) {
                    stderrBuffers.getOrPut(descriptor.displayName) { StringBuilder() }
                        .append(event.message)
                }
            }

            addTestListener(object : TestListener {
                override fun afterTest(descriptor: TestDescriptor, result: TestResult) {
                    flushStdErrBuffer(descriptor, result)
                }

                override fun beforeTest(descriptor: TestDescriptor) {}
                override fun beforeSuite(suite: TestDescriptor) {}
                override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                    flushStdErrBuffer(suite, result)
                }

                private fun flushStdErrBuffer(descriptor: TestDescriptor, result: TestResult) {
                    val buffer = stderrBuffers.remove(descriptor.displayName)
                    if (result.resultType == TestResult.ResultType.FAILURE && buffer != null) {
                        System.err.println(buffer.toString())
                    }
                }
            })
        }
    }
}
