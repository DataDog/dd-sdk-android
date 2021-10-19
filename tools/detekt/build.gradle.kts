/*
 * Unless explicitly stated otherwise all pomFilesList in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.ktLintConfig

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    jacoco
}

dependencies {
    implementation(libs.kotlin)
    implementation(libs.kotlinReflect)
    compileOnly(libs.detektApi)

    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.detektTest)
}

kotlinConfig()
detektConfig()
ktLintConfig()
junitConfig()
dependencyUpdateConfig()
