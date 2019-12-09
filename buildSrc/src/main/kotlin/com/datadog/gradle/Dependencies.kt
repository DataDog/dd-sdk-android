/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.gradle

object Dependencies {

    object Versions {
        // Commons
        const val Kotlin = "1.3.61"
        const val AndroidToolsPlugin = "3.5.3"
        const val Gson = "2.8.6"
        const val OkHttp = "4.2.2"

        // JUnit
        const val JUnitJupiter = "5.5.2"
        const val JUnitPlatform = "1.5.2"
        const val JUnitVintage = "5.5.2"
        const val JunitMockitoExt = "3.2.0"

        // Tests Tools
        const val AssertJ = "0.2.1"
        const val Elmyr = "1.0.0-beta2"
        const val Jacoco = "0.8.4"
        const val MockitoKotlin = "2.2.0"
        const val AndroidBenchmark = "1.0.0"

        // Tools
        const val Detekt = "1.2.1"
        const val KtLint = "8.2.0"
        const val DependencyVersion = "0.27.0"
    }

    object Libraries {

        const val Kotlin = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin}"

        const val Gson = "com.google.code.gson:gson:${Versions.Gson}"

        const val OkHttp = "com.squareup.okhttp3:okhttp:${Versions.OkHttp}"

        val JetpackBenchmark = arrayOf(
            "androidx.benchmark:benchmark-junit4:${Versions.AndroidBenchmark}",
            "androidx.test.ext:junit:1.1.1"
        )

        @JvmField
        val JUnit5 = arrayOf(
            "org.junit.platform:junit-platform-launcher:${Versions.JUnitPlatform}",
            "org.junit.vintage:junit-vintage-engine:${Versions.JUnitVintage}",
            "org.junit.jupiter:junit-jupiter:${Versions.JUnitJupiter}",
            "org.mockito:mockito-junit-jupiter:${Versions.JunitMockitoExt}"
        )

        @JvmField
        val TestTools = arrayOf(
            "net.wuerl.kotlin:assertj-core-kotlin:${Versions.AssertJ}",
            "com.github.xgouchet.Elmyr:core:${Versions.Elmyr}",
            "com.github.xgouchet.Elmyr:inject:${Versions.Elmyr}",
            "com.github.xgouchet.Elmyr:junit5:${Versions.Elmyr}",
            "com.github.xgouchet.Elmyr:jvm:${Versions.Elmyr}",
            "com.nhaarman.mockitokotlin2:mockito-kotlin:${Versions.MockitoKotlin}"
        )

        @JvmField
        val AndroidTestTools = arrayOf(
            "com.github.xgouchet.Elmyr:core:${Versions.Elmyr}",
            "com.github.xgouchet.Elmyr:inject:${Versions.Elmyr}",
            "com.github.xgouchet.Elmyr:junit4:${Versions.Elmyr}"
        )

        const val OkHttpMock = "com.squareup.okhttp3:mockwebserver:${Versions.OkHttp}"

        const val DetektCli = "io.gitlab.arturbosch.detekt:detekt-cli:${Versions.Detekt}"
        const val DetektApi = "io.gitlab.arturbosch.detekt:detekt-api:${Versions.Detekt}"
        const val DetektTest = "io.gitlab.arturbosch.detekt:detekt-test:${Versions.Detekt}"
    }

    object ClassPaths {
        const val AndroidTools = "com.android.tools.build:gradle:${Versions.AndroidToolsPlugin}"
        const val Kotlin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.Kotlin}"
        const val KtLint = "org.jlleitschuh.gradle:ktlint-gradle:${Versions.KtLint}"
        const val AndroidBenchmark = "androidx.benchmark:benchmark-gradle-plugin:${Versions.AndroidBenchmark}"
    }

    object Repositories {
        const val Gradle = "https://plugins.gradle.org/m2/"
        const val Google = "https://maven.google.com"
        const val Jitpack = "https://jitpack.io"
    }

    object PluginIds {
        const val Android = "com.android.library"
        const val Detetk = "io.gitlab.arturbosch.detekt"
        const val KtLint = "org.jlleitschuh.gradle.ktlint"
        const val DependencyVersion = "com.github.ben-manes.versions"
        const val Kotlin = "org.jetbrains.kotlin"
        const val KotlinJVM = "org.jetbrains.kotlin.jvm"
        const val KotlinAndroid = "org.jetbrains.kotlin.android"
        const val KotlinAndroidExtension = "org.jetbrains.kotlin.android.extensions"
    }

    object PluginNamespaces {
        const val Gradle = "org.gradle"
    }
}
