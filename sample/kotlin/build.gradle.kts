/*
 * Unless explicitly stated otherwise all pomFilesList in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.Dependencies
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.ktLintConfig
import com.datadog.gradle.implementation

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("thirdPartyLicences")
    id("org.jetbrains.dokka")
    jacoco
    id("realm-android")
    id("com.squareup.sqldelight")
}

sqldelight {
    database("LogsDatabase") {
        packageName = "com.datadog.android.sample"
        dialect = "sqlite:3.24"
        sourceFolders = listOf("sqldelight")
    }
}

android {
    compileSdkVersion(AndroidConfig.TARGET_SDK)
    buildToolsVersion(AndroidConfig.BUILD_TOOLS_VERSION)

    defaultConfig {
        minSdkVersion(AndroidConfig.MIN_SDK)
        targetSdkVersion(AndroidConfig.TARGET_SDK)
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name
        multiDexEnabled = true

        vectorDrawables.useSupportLibrary = true
        externalNativeBuild {
            cmake {
                cppFlags.add("-std=c++14")
            }
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    flavorDimensions("version")
    productFlavors {
        val regions = arrayOf("us1", "us3", "us5", "us1_fed", "eu1", "staging")

        regions.forEachIndexed { index, region ->
            register(region) {
                isDefault = index == 0
                dimension = "version"
                com.datadog.gradle.config.configureFlavorForSampleApp(this, rootDir)
            }
        }
    }

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
    }
    sourceSets.named("test") {
        java.srcDir("src/test/kotlin")
    }
    sourceSets.named("androidTest") {
        java.srcDir("src/androidTest/kotlin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    dexOptions {
        javaMaxHeapSize = "4g"
    }

    packagingOptions {
        exclude("META-INF/*")
    }

    externalNativeBuild {
        cmake {
            path = File("$projectDir/src/main/cpp/CMakeLists.txt")
            version = Dependencies.Versions.CMakeVersion
        }
    }
    ndkVersion = Dependencies.Versions.NdkVersion
}

dependencies {

    api(project(":dd-sdk-android"))
    api(project(":dd-sdk-android-ktx"))
    api(project(":dd-sdk-android-ndk"))
    api(project(":dd-sdk-android-rx"))
    api(project(":dd-sdk-android-timber"))
    api(project(":dd-sdk-android-coil"))
    api(project(":dd-sdk-android-glide"))
    api(project(":dd-sdk-android-fresco"))
    api(project(":dd-sdk-android-sqldelight"))

    implementation(Dependencies.Libraries.Kotlin)

    // Android dependencies
    implementation(Dependencies.Libraries.AndroidXMultidex)
    implementation(Dependencies.Libraries.AndroidXNavigation)
    implementation("androidx.appcompat:appcompat:1.3.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("com.google.android.material:material:1.3.0")
    implementation("androidx.media:media:1.3.1")
    implementation("androidx.vectordrawable:vectordrawable:1.1.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("io.opentracing.contrib:opentracing-rxjava-3:0.1.4") {
        exclude(group = "io.opentracing")
    }

    // Ktor (local web server)
    implementation("io.ktor:ktor:1.4.3")
    implementation("io.ktor:ktor-server-netty:1.4.3")
    implementation("io.ktor:ktor-gson:1.4.3")

    // Image Loading Library
    implementation(Dependencies.Libraries.Coil)
    implementation(Dependencies.Libraries.Fresco)
    implementation(Dependencies.Libraries.Glide)
    implementation(Dependencies.Libraries.Picasso)
    kapt(Dependencies.AnnotationProcessors.Glide)

    // Local Storage
    implementation(Dependencies.Libraries.SQLDelight)
    implementation(Dependencies.Libraries.Room)
    kapt(Dependencies.AnnotationProcessors.Room)

    // Multithreading
    implementation(Dependencies.Libraries.RxJava)
    implementation("com.squareup.retrofit2:adapter-rxjava3:2.9.0")
    implementation("io.reactivex.rxjava3:rxandroid:${Dependencies.Versions.RxJava}")
    implementation(Dependencies.Libraries.Coroutines)

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation(Dependencies.Libraries.OkHttp)
    implementation(Dependencies.Libraries.Gson)

    // Misc
    implementation(Dependencies.Libraries.Timber)
    api("com.facebook.stetho:stetho:1.5.1")
}

kotlinConfig(evaluateWarningsAsErrors = false)
detektConfig()
ktLintConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
