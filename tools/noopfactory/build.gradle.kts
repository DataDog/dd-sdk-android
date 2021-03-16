/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.Dependencies
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.ktLintConfig
import com.datadog.gradle.implementation
import com.datadog.gradle.testImplementation

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    jacoco
}

dependencies {
    implementation(Dependencies.Libraries.Kotlin)
    implementation(Dependencies.Libraries.KotlinReflect)
    implementation("com.squareup:kotlinpoet:1.7.2")

    testImplementation(Dependencies.Libraries.JUnit5)
    testImplementation(Dependencies.Libraries.TestTools)
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.2.7")
}

kotlinConfig()
detektConfig()
ktLintConfig()
junitConfig()
dependencyUpdateConfig()
