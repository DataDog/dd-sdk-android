/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle

object Dependencies {

    object Versions {
        // Commons
        const val Kotlin = "1.3.61"
        const val Gson = "2.8.6"
        const val OkHttp = "3.12.6"

        // Android
        const val AndroidToolsPlugin = "3.6.1"
        const val AndroidXAppCompat = "1.1.0"
        const val AndroidXCore = "1.2.0"
        const val AndroidXMultidex = "2.0.1"
        const val AndroidXNavigation = "2.2.0-rc02"
        const val AndroidXRecyclerView = "1.1.0"
        const val AndroidXWorkManager = "2.3.3"

        // DD-TRACE-OT
        const val OpenTracing = "0.32.0"

        // JUnit
        const val JUnitJupiter = "5.5.2"
        const val JUnitPlatform = "1.5.2"
        const val JUnitVintage = "5.5.2"
        const val JunitMockitoExt = "3.2.0"

        // Tests Tools
        const val AssertJ = "0.2.1"
        const val Elmyr = "1.0.0"
        const val Jacoco = "0.8.4"
        const val MockitoKotlin = "2.2.0"
        const val JetpackBenchmark = "1.0.0"

        // Tools
        const val Detekt = "1.6.0"
        const val KtLint = "8.2.0"
        const val DependencyVersion = "0.27.0"
        const val Dokka = "0.10.0"
        const val Bintray = "1.8.4"
        const val Unmock = "0.7.5"
        const val Robolectric = "4.4_r1-robolectric-r2" // Use lowest API

        // AndroidJunit
        const val AndroidJunitRunner = "1.2.0"
        const val AndroidExtJunit = "1.1.1"
        const val AndroidJunitCore = "1.2.0"
        const val Espresso = "3.1.1"

        // Sample Apps
        const val ConstraintLayout = "2.0.0-beta4"
        const val GoogleMaterial = "1.0.0"

        // Integrations
        const val Timber = "4.7.1"

        // Internal APIs
        const val TracingOt = "0.45.0"

        // NDK
        const val NdkVersion = "21.3.6528147"
        const val CMakeVersion = "3.10.2"
    }

    object Libraries {

        @JvmField
        val TracingOt = arrayOf(
            "io.opentracing:opentracing-api:${Versions.OpenTracing}",
            "io.opentracing:opentracing-noop:${Versions.OpenTracing}",
            "io.opentracing:opentracing-util:${Versions.OpenTracing}"
        )
        const val Kotlin = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin}"
        const val KotlinReflect = "org.jetbrains.kotlin:kotlin-reflect:${Versions.Kotlin}"

        const val Gson = "com.google.code.gson:gson:${Versions.Gson}"

        const val OkHttp = "com.squareup.okhttp3:okhttp:${Versions.OkHttp}"

        const val AndroidXMultidex = "androidx.multidex:multidex:${Versions.AndroidXMultidex}"

        val JetpackBenchmark = arrayOf(
            "androidx.benchmark:benchmark-junit4:${Versions.JetpackBenchmark}",
            "androidx.test.ext:junit:1.1.1"
        )

        val AndroidXCore = "androidx.core:core:${Versions.AndroidXCore}"
        val AndroidXWorkManager = "androidx.work:work-runtime:${Versions.AndroidXWorkManager}"
        val AndroidXAppCompat = "androidx.appcompat:appcompat:${Versions.AndroidXAppCompat}"
        val AndroidXNavigation = arrayOf(
            "androidx.navigation:navigation-fragment-ktx:${Versions.AndroidXNavigation}",
            "androidx.navigation:navigation-ui-ktx:${Versions.AndroidXNavigation}",
            "androidx.navigation:navigation-runtime-ktx:${Versions.AndroidXNavigation}"
        )
        val AndroidXRecyclerView = "androidx.recyclerview:recyclerview:${Versions.AndroidXRecyclerView}"

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

        const val Elmyr = "com.github.xgouchet.Elmyr:core:${Versions.Elmyr}"

        @JvmField
        val IntegrationTests = arrayOf(
            // Core library
            "androidx.test:core:${Versions.AndroidJunitCore}",
            // AndroidJUnitRunner and JUnit Rules
            "androidx.test:runner:${Versions.AndroidJunitRunner}",
            "androidx.test:runner:${Versions.AndroidJunitRunner}",
            "androidx.test:rules:${Versions.AndroidJunitRunner}",
            "androidx.test.ext:junit:${Versions.AndroidExtJunit}",
            // Espresso
            "androidx.test.espresso:espresso-core:${Versions.Espresso}",
            "androidx.test.espresso:espresso-contrib:${Versions.Espresso}",
            "androidx.test.espresso:espresso-intents:${Versions.Espresso}",
            // Elmyr
            "com.github.xgouchet.Elmyr:core:${Versions.Elmyr}",
            "com.github.xgouchet.Elmyr:inject:${Versions.Elmyr}",
            "com.github.xgouchet.Elmyr:junit4:${Versions.Elmyr}"
        )

        @JvmField
        val AndroidxSupportBase = arrayOf(
            AndroidXAppCompat,
            "androidx.constraintlayout:constraintlayout:${Versions.ConstraintLayout}",
            "com.google.android.material:material:${Versions.GoogleMaterial}"
        )

        const val OkHttpMock = "com.squareup.okhttp3:mockwebserver:${Versions.OkHttp}"

        const val DetektCli = "io.gitlab.arturbosch.detekt:detekt-cli:${Versions.Detekt}"
        const val DetektApi = "io.gitlab.arturbosch.detekt:detekt-api:${Versions.Detekt}"
        const val DetektTest = "io.gitlab.arturbosch.detekt:detekt-test:${Versions.Detekt}"

        const val Timber = "com.jakewharton.timber:timber:${Versions.Timber}"
        const val Robolectric = "org.robolectric:android-all:${Versions.Robolectric}"
    }

    object ClassPaths {
        const val AndroidTools = "com.android.tools.build:gradle:${Versions.AndroidToolsPlugin}"
        const val Kotlin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.Kotlin}"
        const val KtLint = "org.jlleitschuh.gradle:ktlint-gradle:${Versions.KtLint}"
        const val AndroidBenchmark =
            "androidx.benchmark:benchmark-gradle-plugin:${Versions.JetpackBenchmark}"
        const val Dokka = "org.jetbrains.dokka:dokka-gradle-plugin:${Versions.Dokka}"
        const val Bintray = "com.jfrog.bintray.gradle:gradle-bintray-plugin:${Versions.Bintray}"
        const val Unmock = "de.mobilej.unmock:UnMockPlugin:${Versions.Unmock}"
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
        const val Bintray = "com.jfrog.bintray"
        const val Unmock = "de.mobilej.unmock"
    }

    object PluginNamespaces {
        const val Gradle = "org.gradle"
    }
}
