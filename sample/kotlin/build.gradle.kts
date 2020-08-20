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
        register("gov") {
            dimension = "version"
            com.datadog.gradle.config.configureFlavorForSampleApp(this, rootDir)
        }

        register("glide") {
            dimension = "ill"
        }
        register("picasso") {
            dimension = "ill"
        }
        register("fresco") {
            dimension = "ill"
        }
        register("coil") {
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
    sourceSets.named("fresco") {
        java.srcDir("src/fresco/kotlin")
        resources.srcDirs("src/fresco/res")
    }
    sourceSets.named("coil") {
        java.srcDir("src/coil/kotlin")
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
    api(project(":dd-sdk-android-timber"))
    "coilApi"(project(":dd-sdk-android-coil"))
    "glideApi"(project(":dd-sdk-android-glide"))
    "frescoApi"(project(":dd-sdk-android-fresco"))

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

    // Coil
    "coilImplementation"("io.coil-kt:coil:${Dependencies.Versions.Coil}")

    // Fresco
    "frescoImplementation"("com.facebook.fresco:fresco:${Dependencies.Versions.Fresco}")
    "frescoImplementation"("com.facebook.fresco:imagepipeline-okhttp3:${Dependencies.Versions.Fresco}")

    // Glide
    "glideImplementation"("com.github.bumptech.glide:glide:${Dependencies.Versions.Glide}")
    "glideImplementation"("com.github.bumptech.glide:okhttp3-integration:${Dependencies.Versions.Glide}") {
        exclude(group = "glide-parent")
    }
    "kaptGlide"("com.github.bumptech.glide:compiler:${Dependencies.Versions.Glide}")

    // Picasso
    "picassoImplementation"("com.squareup.picasso:picasso:2.8")

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
