/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.android.build.api.dsl.CompileOptions
import com.android.build.gradle.LibraryExtension
import com.datadog.gradle.plugin.licenses.DependencyLicensesExtension
import com.datadog.gradle.utils.Version
import org.gradle.api.JavaVersion
import org.gradle.api.Project

object AndroidConfig {

    const val TARGET_SDK = 36
    const val MIN_SDK = 21
    const val MIN_SDK_FOR_WEAR_AND_TV = 23
    const val MIN_SDK_FOR_AUTO = 29
    const val BUILD_TOOLS_VERSION = "36.0.0"

    val VERSION = Version(2, 24, 0, Version.Type.Snapshot)
}

// TODO RUM-628 Switch to Java 17 bytecode
fun CompileOptions.java11() {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

fun CompileOptions.java17() {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

fun Project.androidLibraryConfig() {
    extensionConfig<LibraryExtension> {
        compileSdk = AndroidConfig.TARGET_SDK
        buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

        defaultConfig {
            minSdk = AndroidConfig.MIN_SDK
        }

        compileOptions {
            java11()
        }

        sourceSets.all {
            java.srcDir("src/$name/kotlin")
        }
        sourceSets.named("main") {
            java.srcDir("build/generated/json2kotlin/main/kotlin")
        }
        libraryVariants.configureEach {
            addJavaSourceFoldersToModel(
                layout.buildDirectory.dir("generated/ksp/$name/kotlin").get().asFile
            )
        }

        @Suppress("UnstableApiUsage")
        testOptions {
            unitTests.isReturnDefaultValues = true
        }

        lint {
            warningsAsErrors = true
            abortOnError = true
            checkReleaseBuilds = false
            checkGeneratedSources = true
            ignoreTestSources = true
            disable.addAll(
                listOf(
                    "UseKtx" // https://googlesamples.github.io/android-custom-lint-rules/checks/UseKtx.md.html
                )
            )
        }

        packaging {
            resources {
                excludes += listOf(
                    "META-INF/jvm.kotlin_module",
                    "META-INF/LICENSE.md",
                    "META-INF/LICENSE-notice.md"
                )
            }
        }
    }

    extensionConfig<DependencyLicensesExtension> {
        transitiveDependencies = true
    }
}
