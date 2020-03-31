/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

@Suppress("UnstableApiUsage")
fun Project.junitConfig() {
    tasks.withType(Test::class.java) {
        useJUnitPlatform {
            includeEngines("spek", "junit-jupiter", "junit-vintage")
        }
        reports {
            junitXml.isEnabled = true
            html.isEnabled = true
        }
    }
}
