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
    kotlin("android.extensions")
    kotlin("kapt")
    `maven-publish`
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("thirdPartyLicences")
    id("org.jetbrains.dokka")
    id("com.jfrog.bintray")
    jacoco
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

    flavorDimensions("version", "ill")
    productFlavors {
        register("staging") {
            dimension = "version"
            com.datadog.gradle.config.configureFlavorForSampleApp(this, rootDir)
        }
        register("production") {
            dimension = "version"
            com.datadog.gradle.config.configureFlavorForSampleApp(this, rootDir)
        }

        register("glide") {
            dimension = "ill"
        }
        register("picasso") {
            dimension = "ill"
        }
    }

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
    }
    sourceSets.named("glide") {
        java.srcDir("src/glide/kotlin")
    }
    sourceSets.named("picasso") {
        java.srcDir("src/picasso/kotlin")
    }
    sourceSets.named("test") {
        java.srcDir("src/test/kotlin")
    }
    sourceSets.named("androidTest") {
        java.srcDir("src/androidTest/kotlin")
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
    api(project(":dd-sdk-android-timber"))
    api(project(":dd-sdk-android-glide"))

    // Android dependencies
    implementation(Dependencies.Libraries.AndroidXMultidex)
    implementation(Dependencies.Libraries.AndroidXNavigation)
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.0-beta4")
    implementation("com.google.android.material:material:1.0.0")
    implementation("androidx.vectordrawable:vectordrawable:1.1.0")
    implementation("androidx.navigation:navigation-fragment:2.1.0")
    implementation("androidx.navigation:navigation-ui:2.1.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.1.0")

    // Ktor (local web server)
    implementation("io.ktor:ktor:1.2.5")
    implementation("io.ktor:ktor-server-netty:1.2.5")
    implementation("io.ktor:ktor-gson:1.2.5")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.11.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.11.0") {
        exclude(group = "glide-parent")
    }
    kapt("com.github.bumptech.glide:compiler:4.11.0")
    // Picasso
    implementation("com.squareup.picasso:picasso:2.8")

    implementation(Dependencies.Libraries.Kotlin)
    implementation(Dependencies.Libraries.OkHttp)
    implementation(Dependencies.Libraries.Gson)
    implementation(Dependencies.Libraries.Timber)
}

kotlinConfig()
detektConfig()
ktLintConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
