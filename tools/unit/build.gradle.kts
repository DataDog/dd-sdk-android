/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.android.build.api.variant.HostTestBuilder
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.java17

plugins {
    // Applied before the Android plugin on purpose (not under "Analysis tools"): AGP 9 applies
    // the Kotlin plugin itself, and ktlint-gradle 14.2.0 registers its Android source-set tasks
    // twice when it comes after the Kotlin plugin.
    id("ktlint")

    // Build
    id("com.android.library")
    id("de.mobilej.unmock")
    id("datadogBuildConfig")
}

android {
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
    }

    namespace = "com.datadog.tools.unit"

    compileOptions {
        java17()
    }

    flavorDimensions += "platform"
    productFlavors {
        register("art") {
            isDefault = false
        }
        register("jvm") {
            isDefault = true
        }
    }

    packaging {
        resources {
            excludes.addAll(listOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md"))
        }
    }
}

androidComponents {
    // AGP 9 disables host (unit) tests for non-test build types by default, so the
    // release unit-test tasks (e.g. testJvmReleaseUnitTest, required by the root
    // :unitTestTools aggregation) are no longer generated. Re-enable them here, mirroring
    // androidLibraryConfig() which this module can't use because of its custom flavors.
    beforeVariants { variant ->
        variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

dependencies {
    implementation(libs.kotlin)
    implementation(libs.bundles.jUnit5)
    implementation(libs.bundles.testTools)
    implementation(libs.gson)

    unmock(libs.robolectric)
}

unMock {
    keepStartingWith("org.json")
}

datadogBuild {
    applyKotlinConfig()
    applyJunitConfig()
}
