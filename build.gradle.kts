import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.nightlyTestsCoverageConfig

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

plugins {
    `maven-publish`
    id("io.github.gradle-nexus.publish-plugin")
    id("org.jetbrains.kotlinx.kover")
}

version = AndroidConfig.VERSION.name

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { setUrl(com.datadog.gradle.Dependencies.Repositories.Gradle) }
    }

    dependencies {
        classpath(libs.androidToolsGradlePlugin)
        classpath(libs.kotlinGradlePlugin)
        classpath(libs.kotlinSPGradlePlugin)
        classpath(libs.dokkaGradlePlugin)
        classpath(libs.unmockGradlePlugin)
        classpath(libs.realmGradlePlugin)
        classpath(libs.sqlDelightGradlePlugin)
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { setUrl(com.datadog.gradle.Dependencies.Repositories.Jitpack) }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            val sonatypeUsername = System.getenv("OSSRH_USERNAME")
            val sonatypePassword = System.getenv("OSSRH_PASSWORD")
            stagingProfileId.set("378eecbbe2cf9")
            if (sonatypeUsername != null) username.set(sonatypeUsername)
            if (sonatypePassword != null) password.set(sonatypePassword)
        }
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}

tasks.register("checkAll") {
    dependsOn(
        "lintCheckAll",
        "unitTestAll",
        "koverReportAll",
        "instrumentTestAll"
    )
}

tasks.register("assembleAll") {
    dependsOn(
        ":dd-sdk-android:assemble",
        ":dd-sdk-android-coil:assemble",
        ":dd-sdk-android-compose:assemble",
        ":dd-sdk-android-fresco:assemble",
        ":dd-sdk-android-glide:assemble",
        ":dd-sdk-android-ktx:assemble",
        ":dd-sdk-android-rx:assemble",
        ":dd-sdk-android-sqldelight:assemble",
        ":dd-sdk-android-timber:assemble",
        ":dd-sdk-android-tv:assemble",
        ":dd-sdk-android-okhttp:assemble",
        ":library:dd-sdk-android-session-replay:assemble",
        ":library:dd-sdk-android-logs:assemble",
        ":library:dd-sdk-android-ndk:assemble"
    )
}

tasks.register("unitTestRelease") {
    dependsOn(
        ":dd-sdk-android:testReleaseUnitTest",
        ":dd-sdk-android-coil:testReleaseUnitTest",
        ":dd-sdk-android-compose:testReleaseUnitTest",
        ":dd-sdk-android-fresco:testReleaseUnitTest",
        ":dd-sdk-android-glide:testReleaseUnitTest",
        ":dd-sdk-android-ktx:testReleaseUnitTest",
        ":dd-sdk-android-rx:testReleaseUnitTest",
        ":dd-sdk-android-sqldelight:testReleaseUnitTest",
        ":dd-sdk-android-timber:testReleaseUnitTest",
        ":dd-sdk-android-tv:testReleaseUnitTest",
        ":dd-sdk-android-okhttp:testReleaseUnitTest",
        ":library:dd-sdk-android-session-replay:testReleaseUnitTest",
        ":library:dd-sdk-android-logs:testReleaseUnitTest",
        ":library:dd-sdk-android-ndk:testReleaseUnitTest"
    )
}

tasks.register("unitTestDebug") {
    dependsOn(
        ":dd-sdk-android:testDebugUnitTest",
        ":dd-sdk-android-coil:testDebugUnitTest",
        ":dd-sdk-android-compose:testDebugUnitTest",
        ":dd-sdk-android-fresco:testDebugUnitTest",
        ":dd-sdk-android-glide:testDebugUnitTest",
        ":dd-sdk-android-ktx:testDebugUnitTest",
        ":dd-sdk-android-rx:testDebugUnitTest",
        ":dd-sdk-android-sqldelight:testDebugUnitTest",
        ":dd-sdk-android-timber:testDebugUnitTest",
        ":dd-sdk-android-tv:testDebugUnitTest",
        ":dd-sdk-android-okhttp:testDebugUnitTest",
        ":library:dd-sdk-android-session-replay:testDebugUnitTest",
        ":library:dd-sdk-android-logs:testDebugUnitTest",
        ":library:dd-sdk-android-ndk:testDebugUnitTest"
    )
}

tasks.register("unitTestTools") {
    dependsOn(
        ":sample:kotlin:assembleUs1Release",
        ":tools:unit:testReleaseUnitTest"
    )
}

tasks.register("unitTestAll") {
    dependsOn(
        ":unitTestDebug",
        ":unitTestRelease",
        ":unitTestTools"
    )
}

tasks.register("lintCheckAll") {
    dependsOn(
        ":dd-sdk-android:lintRelease",
        ":dd-sdk-android-coil:lintRelease",
        ":dd-sdk-android-compose:lintRelease",
        ":dd-sdk-android-fresco:lintRelease",
        ":dd-sdk-android-glide:lintRelease",
        ":dd-sdk-android-ktx:lintRelease",
        ":dd-sdk-android-rx:lintRelease",
        ":dd-sdk-android-sqldelight:lintRelease",
        ":dd-sdk-android-timber:lintRelease",
        ":dd-sdk-android-tv:lintRelease",
        ":dd-sdk-android-okhttp:lintRelease",
        ":library:dd-sdk-android-session-replay:lintRelease",
        ":library:dd-sdk-android-logs:lintRelease",
        ":library:dd-sdk-android-ndk:lintRelease"
    )
}

tasks.register("checkThirdPartyLicensesAll") {
    dependsOn(
        ":dd-sdk-android:checkThirdPartyLicences",
        ":dd-sdk-android-coil:checkThirdPartyLicences",
        ":dd-sdk-android-compose:checkThirdPartyLicences",
        ":dd-sdk-android-fresco:checkThirdPartyLicences",
        ":dd-sdk-android-glide:checkThirdPartyLicences",
        ":dd-sdk-android-ktx:checkThirdPartyLicences",
        ":dd-sdk-android-rx:checkThirdPartyLicences",
        ":dd-sdk-android-sqldelight:checkThirdPartyLicences",
        ":dd-sdk-android-timber:checkThirdPartyLicences",
        ":dd-sdk-android-tv:checkThirdPartyLicences",
        ":dd-sdk-android-okhttp:checkThirdPartyLicences",
        ":library:dd-sdk-android-session-replay:checkThirdPartyLicences",
        ":library:dd-sdk-android-logs:checkThirdPartyLicences",
        ":library:dd-sdk-android-ndk:checkThirdPartyLicences"
    )
}

tasks.register("checkApiSurfaceChangesAll") {
    dependsOn(
        ":dd-sdk-android:checkApiSurfaceChanges",
        ":dd-sdk-android-coil:checkApiSurfaceChanges",
        ":dd-sdk-android-compose:checkApiSurfaceChanges",
        ":dd-sdk-android-fresco:checkApiSurfaceChanges",
        ":dd-sdk-android-glide:checkApiSurfaceChanges",
        ":dd-sdk-android-ktx:checkApiSurfaceChanges",
        ":dd-sdk-android-rx:checkApiSurfaceChanges",
        ":dd-sdk-android-sqldelight:checkApiSurfaceChanges",
        ":dd-sdk-android-timber:checkApiSurfaceChanges",
        ":dd-sdk-android-tv:checkApiSurfaceChanges",
        ":dd-sdk-android-okhttp:checkApiSurfaceChanges",
        ":library:dd-sdk-android-session-replay:checkApiSurfaceChanges",
        ":library:dd-sdk-android-logs:checkApiSurfaceChanges",
        ":library:dd-sdk-android-ndk:checkApiSurfaceChanges"
    )
}

tasks.register("checkGeneratedFiles") {
    dependsOn("checkApiSurfaceChangesAll")
}

tasks.register("koverReportAll") {
    dependsOn(
        ":dd-sdk-android:koverXmlReport",
        ":dd-sdk-android-coil:koverXmlReport",
        ":dd-sdk-android-compose:koverXmlReport",
        ":dd-sdk-android-fresco:koverXmlReport",
        ":dd-sdk-android-glide:koverXmlReport",
        ":dd-sdk-android-ktx:koverXmlReport",
        ":dd-sdk-android-rx:koverXmlReport",
        ":dd-sdk-android-sqldelight:koverXmlReport",
        ":dd-sdk-android-timber:koverXmlReport",
        ":dd-sdk-android-tv:koverXmlReport",
        ":dd-sdk-android-okhttp:koverXmlReport",
        ":library:dd-sdk-android-session-replay:koverXmlReport",
        ":library:dd-sdk-android-logs:koverXmlReport",
        ":library:dd-sdk-android-ndk:koverXmlReport"
    )
}

tasks.register("instrumentTestAll") {
    dependsOn(":instrumented:integration:connectedCheck")
}

tasks.register("buildIntegrationTestsArtifacts") {
    dependsOn(":instrumented:integration:assembleDebugAndroidTest")
    dependsOn(":instrumented:integration:assembleDebug")
}

tasks.register("buildNightlyTestsArtifacts") {
    dependsOn(":instrumented:nightly-tests:assembleDebugAndroidTest")
    dependsOn(":instrumented:nightly-tests:assembleDebug")
}

tasks.register("buildNdkIntegrationTestsArtifacts") {
    dependsOn(":library:dd-sdk-android-ndk:assembleDebugAndroidTest")
    // we need this artifact to trick Bitrise
    dependsOn(":instrumented:integration:assembleDebug")
}

nightlyTestsCoverageConfig(threshold = 0.90f)
kover {
    isDisabled = false
    disabledProjects = setOf(
        "instrumented",
        "sample",
        "tools",
        "integration",
        "nightly-tests",
        "kotlin",
        "detekt",
        "javabackport",
        "noopfactory",
        "unit"
    )
    instrumentAndroidPackage = false
}
