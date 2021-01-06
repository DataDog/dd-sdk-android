/*
 * Unless explicitly stated otherwise all pomFilesList in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

import com.datadog.gradle.Dependencies
import com.datadog.gradle.config.bintrayConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.ktLintConfig
import com.datadog.gradle.config.publishingConfig
import com.datadog.gradle.implementation
import com.datadog.gradle.testImplementation

plugins {
    id("java-gradle-plugin")
    kotlin("jvm")
    `maven-publish`
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.dokka")
    id("com.jfrog.bintray")
    jacoco
}

dependencies {
    implementation(gradleApi())
    implementation(Dependencies.Libraries.Kotlin)
    implementation(Dependencies.Libraries.KotlinReflect)
    implementation(Dependencies.Libraries.OkHttp)
    implementation(Dependencies.ClassPaths.AndroidTools)

    testImplementation(Dependencies.Libraries.JUnit5)
    testImplementation(Dependencies.Libraries.TestTools)
    testImplementation(Dependencies.Libraries.OkHttpMock)

    detekt(project(":tools:detekt"))
    detekt(Dependencies.Libraries.DetektCli)
}

kotlinConfig()
detektConfig()
ktLintConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig("${rootDir.canonicalPath}/repo", false)
bintrayConfig()

gradlePlugin {
    plugins {
        register("dd-android-gradle-plugin") {
            id = "dd-android-gradle-plugin" // the alias
            implementationClass = "com.datadog.android.gradle.DdAndroidGradlePlugin"
        }
    }
}

tasks.withType<Test> {
    dependsOn("pluginUnderTestMetadata")
}
