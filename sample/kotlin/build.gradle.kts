/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.Dependencies
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.configureFlavorForSampleApp
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.java17
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.taskConfig

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("com.github.ben-manes.versions")
    id("org.jetbrains.dokka")
    id("io.realm.kotlin")
    id("com.squareup.sqldelight")
    id("com.google.devtools.ksp")
}

sqldelight {
    database("LogsDatabase") {
        packageName = "com.datadog.android.sample"
        dialect = "sqlite:3.24"
        sourceFolders = listOf("sqldelight")
    }
}

@Suppress("StringLiteralDuplication")
android {
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name
        multiDexEnabled = true

        buildFeatures {
            buildConfig = true
        }

        vectorDrawables.useSupportLibrary = true
        externalNativeBuild {
            cmake {
                cppFlags.add("-std=c++14")
            }
        }
    }

    namespace = "com.datadog.android.sample"

    compileOptions {
        java17()
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.androidXComposeCompiler.get()
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    flavorDimensions += listOf("site")
    productFlavors {
        val regions = arrayOf("us1", "us3", "us5", "us1_fed", "eu1", "ap1", "staging")

        regions.forEachIndexed { index, region ->
            register(region) {
                isDefault = index == 0
                dimension = "site"
                configureFlavorForSampleApp(project, this, project.rootDir)
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

    packaging {
        resources {
            excludes += "META-INF/*"
        }
    }

    externalNativeBuild {
        cmake {
            path = File("$projectDir/src/main/cpp/CMakeLists.txt")
            version = Dependencies.Versions.CMake
        }
    }
    ndkVersion = Dependencies.Versions.Ndk

    val e2ePassword = System.getenv("E2E_STORE_PASSWD")
    signingConfigs {
        if (e2ePassword != null) {
            create("release") {
                storeFile = File(project.rootDir, "sample-android.keystore")
                storePassword = e2ePassword
                keyAlias = "dd-sdk-android"
                keyPassword = e2ePassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }

        getByName("release") {
            isMinifyEnabled = false
            if (e2ePassword != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    // Datadog Libraries
    implementation(project(":features:dd-sdk-android-logs"))
    implementation(project(":features:dd-sdk-android-rum"))
    implementation(project(":features:dd-sdk-android-trace"))
    implementation(project(":features:dd-sdk-android-ndk"))
    implementation(project(":features:dd-sdk-android-webview"))
    implementation(project(":features:dd-sdk-android-session-replay"))
    implementation(project(":features:dd-sdk-android-session-replay-material"))
    implementation(project(":integrations:dd-sdk-android-trace-coroutines"))
    implementation(project(":integrations:dd-sdk-android-rum-coroutines"))
    implementation(project(":integrations:dd-sdk-android-rx"))
    implementation(project(":integrations:dd-sdk-android-timber"))
    implementation(project(":integrations:dd-sdk-android-coil"))
    implementation(project(":integrations:dd-sdk-android-glide"))
    implementation(project(":integrations:dd-sdk-android-fresco"))
    implementation(project(":integrations:dd-sdk-android-sqldelight"))
    implementation(project(":integrations:dd-sdk-android-compose"))
    implementation(project(":integrations:dd-sdk-android-okhttp"))

    // Sample Vendor Library
    implementation(project(":sample:vendor-lib"))

    implementation(libs.kotlin)

    // Android dependencies
    implementation(libs.androidXMultidex)
    implementation(libs.bundles.androidXNavigation)
    implementation(libs.androidXAppCompat)
    implementation(libs.androidXConstraintLayout)
    implementation(libs.androidXComposeNavigation)
    implementation(libs.androidXComposeUi)
    implementation(libs.androidXComposeUiTooling)
    implementation(libs.androidXComposeMaterial)
    implementation(libs.googleAccompanistAppCompatTheme)
    implementation(libs.googleAccompanistPager)
    implementation(libs.googleAccompanistPagerIndicators)
    implementation(libs.googleMaterial)
    implementation("androidx.media:media:1.3.1")
    implementation("androidx.vectordrawable:vectordrawable:1.1.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("io.opentracing.contrib:opentracing-rxjava-3:0.1.4") {
        exclude(group = "io.opentracing")
    }

    // Image Loading Library
    implementation(libs.coil)
    implementation(libs.bundles.fresco)
    implementation(libs.bundles.glide)
    implementation(libs.picasso)
    kapt(libs.glideCompiler)

    // Local Storage
    implementation(libs.sqlDelight)
    implementation(libs.realm)
    implementation(libs.room)
    ksp(libs.roomCompiler)

    // Multithreading
    implementation(libs.rxJava3)
    implementation("com.squareup.retrofit2:adapter-rxjava3:2.9.0")
    implementation(libs.rxJava3Android)
    implementation(libs.coroutinesCore)

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation(libs.okHttp)
    implementation(libs.gson)

    // Misc
    implementation(libs.timber)
    api("com.facebook.stetho:stetho:1.6.0")
}

kotlinConfig(evaluateWarningsAsErrors = false)
taskConfig<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}
junitConfig()
javadocConfig()
dependencyUpdateConfig()
