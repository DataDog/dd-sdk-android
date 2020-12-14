/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle

object Dependencies {

    object Versions {
        // Commons
        const val Kotlin = "1.4.0"
        const val Gson = "2.8.6"
        const val OkHttp = "3.12.6"
        const val KronosNTP = "0.0.1-alpha10"

        // Android
        const val AndroidToolsPlugin = "4.0.1"
        const val AndroidXAnnotations = "1.1.0"
        const val AndroidXAppCompat = "1.2.0"
        const val AndroidXCore = "1.3.1"
        const val AndroidXMultidex = "2.0.1"
        const val AndroidXNavigation = "2.3.0"
        const val AndroidXRecyclerView = "1.1.0"
        const val AndroidXWorkManager = "2.4.0"

        // DD-TRACE-OT
        const val OpenTracing = "0.32.0"

        // JUnit
        const val JUnitJupiter = "5.6.2"
        const val JUnitPlatform = "1.6.2"
        const val JUnitVintage = "5.6.2"
        const val JunitMockitoExt = "3.4.6"

        // Tests Tools
        const val AssertJ = "0.2.1"
        const val Elmyr = "1.2.0"
        const val Jacoco = "0.8.4"
        const val MockitoKotlin = "2.2.0"
        const val JetpackBenchmark = "1.0.0"

        // Tools
        const val Detekt = "1.6.0"
        const val KtLint = "9.4.0"
        const val Dokka = "1.4.10"
        const val Bintray = "1.8.4"
        const val Unmock = "0.7.5"
        const val Robolectric = "4.4_r1-robolectric-r2" // Use lowest API

        // AndroidJunit
        const val AndroidJunitRunner = "1.3.0"
        const val AndroidExtJunit = "1.1.2"
        const val AndroidJunitCore = "1.3.0"
        const val Espresso = "3.3.0"

        // Sample Apps
        const val ConstraintLayout = "2.0.1"
        const val GoogleMaterial = "1.0.0"

        // Integrations
        const val Coil = "1.0.0"
        const val Fresco = "2.3.0"
        const val Glide = "4.11.0"
        const val Picasso = "2.8"
        const val Realm = "6.0.2"
        const val Room = "2.2.5"
        const val RxJava = "3.0.0"
        const val SQLDelight = "1.4.3"
        const val Timber = "4.7.1"
        const val Coroutines = "1.3.9"

        // NDK
        const val NdkVersion = "21.3.6528147"
        const val CMakeVersion = "3.10.2"
    }

    object Libraries {

        @JvmField
        val OpenTracing = arrayOf(
            "io.opentracing:opentracing-api:${Versions.OpenTracing}",
            "io.opentracing:opentracing-noop:${Versions.OpenTracing}",
            "io.opentracing:opentracing-util:${Versions.OpenTracing}"
        )
        const val Kotlin = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin}"
        const val KotlinReflect = "org.jetbrains.kotlin:kotlin-reflect:${Versions.Kotlin}"

        const val Gson = "com.google.code.gson:gson:${Versions.Gson}"
        const val AssertJ = "net.wuerl.kotlin:assertj-core-kotlin:${Versions.AssertJ}"

        const val OkHttp = "com.squareup.okhttp3:okhttp:${Versions.OkHttp}"
        const val KronosNTP = "com.lyft.kronos:kronos-android:${Versions.KronosNTP}"

        const val AndroidXMultidex = "androidx.multidex:multidex:${Versions.AndroidXMultidex}"

        val JetpackBenchmark = arrayOf(
            "androidx.benchmark:benchmark-junit4:${Versions.JetpackBenchmark}",
            "androidx.test.ext:junit:1.1.1"
        )

        const val AndroidXAnnotation =
            "androidx.annotation:annotation:${Versions.AndroidXAnnotations}"
        const val AndroidXAppCompat = "androidx.appcompat:appcompat:${Versions.AndroidXAppCompat}"
        const val AndroidXCore = "androidx.core:core:${Versions.AndroidXCore}"
        val AndroidXNavigation = arrayOf(
            "androidx.navigation:navigation-fragment:${Versions.AndroidXNavigation}",
            "androidx.navigation:navigation-runtime-ktx:${Versions.AndroidXNavigation}"
        )
        const val AndroidXRecyclerView =
            "androidx.recyclerview:recyclerview:${Versions.AndroidXRecyclerView}"
        const val AndroidXWorkManager = "androidx.work:work-runtime:${Versions.AndroidXWorkManager}"

        @JvmField
        val JUnit5 = arrayOf(
            "org.junit.platform:junit-platform-launcher:${Versions.JUnitPlatform}",
            "org.junit.vintage:junit-vintage-engine:${Versions.JUnitVintage}",
            "org.junit.jupiter:junit-jupiter:${Versions.JUnitJupiter}",
            "org.mockito:mockito-junit-jupiter:${Versions.JunitMockitoExt}"
        )

        @JvmField
        val TestTools = arrayOf(
            AssertJ,
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

        // Integrations

        const val Coil = "io.coil-kt:coil:${Versions.Coil}"
        val Fresco = arrayOf(
            "com.facebook.fresco:fresco:${Versions.Fresco}",
            "com.facebook.fresco:imagepipeline-okhttp3:${Versions.Fresco}"
        )
        val Glide = arrayOf(
            "com.github.bumptech.glide:annotations:${Versions.Glide}",
            "com.github.bumptech.glide:glide:${Versions.Glide}",
            "com.github.bumptech.glide:okhttp3-integration:${Versions.Glide}"
        )
        const val Picasso = "com.squareup.picasso:picasso:${Versions.Picasso}"

        const val Room = "androidx.room:room-runtime:${Versions.Room}"
        const val RxJava = "io.reactivex.rxjava3:rxjava:${Versions.RxJava}"
        const val SQLDelight = "com.squareup.sqldelight:android-driver:${Versions.SQLDelight}"
        const val Timber = "com.jakewharton.timber:timber:${Versions.Timber}"

        val Coroutines = arrayOf(
            "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.Coroutines}",
            "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.Coroutines}"
        )

        // Tools

        const val DetektCli = "io.gitlab.arturbosch.detekt:detekt-cli:${Versions.Detekt}"
        const val DetektApi = "io.gitlab.arturbosch.detekt:detekt-api:${Versions.Detekt}"
        const val DetektTest = "io.gitlab.arturbosch.detekt:detekt-test:${Versions.Detekt}"
        const val OkHttpMock = "com.squareup.okhttp3:mockwebserver:${Versions.OkHttp}"
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
        const val Realm = "io.realm:realm-gradle-plugin:${Versions.Realm}"
        const val SQLDelight = "com.squareup.sqldelight:gradle-plugin:${Versions.SQLDelight}"
    }

    object Repositories {
        const val Gradle = "https://plugins.gradle.org/m2/"
        const val Google = "https://maven.google.com"
        const val Jitpack = "https://jitpack.io"
    }

    object AnnotationProcessors {
        const val Glide = "com.github.bumptech.glide:compiler:${Versions.Glide}"
        const val Room = "androidx.room:room-compiler:${Versions.Room}"
    }
}
