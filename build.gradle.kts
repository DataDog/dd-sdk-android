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
        classpath(libs.ktLintGradlePlugin)
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
        "ktlintCheckAll",
        "detektAll",
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
        ":dd-sdk-android-ndk:assemble",
        ":dd-sdk-android-rx:assemble",
        ":dd-sdk-android-session-replay:assemble",
        ":dd-sdk-android-sqldelight:assemble",
        ":dd-sdk-android-timber:assemble",
        ":dd-sdk-android-tv:assemble"
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
        ":dd-sdk-android-ndk:testReleaseUnitTest",
        ":dd-sdk-android-rx:testReleaseUnitTest",
        ":dd-sdk-android-session-replay:testReleaseUnitTest",
        ":dd-sdk-android-sqldelight:testReleaseUnitTest",
        ":dd-sdk-android-timber:testReleaseUnitTest",
        ":dd-sdk-android-tv:testReleaseUnitTest"
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
        ":dd-sdk-android-ndk:testDebugUnitTest",
        ":dd-sdk-android-rx:testDebugUnitTest",
        ":dd-sdk-android-session-replay:testDebugUnitTest",
        ":dd-sdk-android-sqldelight:testDebugUnitTest",
        ":dd-sdk-android-timber:testDebugUnitTest",
        ":dd-sdk-android-tv:testDebugUnitTest"
    )
}

tasks.register("unitTestTools") {
    dependsOn(
        ":sample:kotlin:assembleUs1Release",
        ":tools:detekt:test",
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

tasks.register("ktlintCheckAll") {
    dependsOn(
        ":dd-sdk-android:ktlintCheck",
        ":dd-sdk-android-coil:ktlintCheck",
        ":dd-sdk-android-compose:ktlintCheck",
        ":dd-sdk-android-fresco:ktlintCheck",
        ":dd-sdk-android-glide:ktlintCheck",
        ":dd-sdk-android-ktx:ktlintCheck",
        ":dd-sdk-android-ndk:ktlintCheck",
        ":dd-sdk-android-rx:ktlintCheck",
        ":dd-sdk-android-session-replay:ktlintCheck",
        ":dd-sdk-android-sqldelight:ktlintCheck",
        ":dd-sdk-android-timber:ktlintCheck",
        ":dd-sdk-android-tv:ktlintCheck",
        ":instrumented:integration:ktlintCheck",
        ":instrumented:nightly-tests:ktlintCheck",
        ":tools:detekt:ktlintCheck",
        ":tools:unit:ktlintCheck"
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
        ":dd-sdk-android-ndk:lintRelease",
        ":dd-sdk-android-rx:lintRelease",
        ":dd-sdk-android-session-replay:lintRelease",
        ":dd-sdk-android-sqldelight:lintRelease",
        ":dd-sdk-android-timber:lintRelease",
        ":dd-sdk-android-tv:lintRelease"
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
        ":dd-sdk-android-ndk:checkThirdPartyLicences",
        ":dd-sdk-android-rx:checkThirdPartyLicences",
        ":dd-sdk-android-session-replay:checkThirdPartyLicences",
        ":dd-sdk-android-sqldelight:checkThirdPartyLicences",
        ":dd-sdk-android-timber:checkThirdPartyLicences",
        ":dd-sdk-android-tv:checkThirdPartyLicences"
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
        ":dd-sdk-android-ndk:checkApiSurfaceChanges",
        ":dd-sdk-android-rx:checkApiSurfaceChanges",
        ":dd-sdk-android-session-replay:checkApiSurfaceChanges",
        ":dd-sdk-android-sqldelight:checkApiSurfaceChanges",
        ":dd-sdk-android-timber:checkApiSurfaceChanges",
        ":dd-sdk-android-tv:checkApiSurfaceChanges"
    )
}

tasks.register("checkGeneratedFiles") {
    dependsOn("checkApiSurfaceChangesAll")
}

tasks.register("detektAll") {
    dependsOn(
        ":dd-sdk-android:detektMain",
        ":dd-sdk-android-coil:detektMain",
        ":dd-sdk-android-compose:detektMain",
        ":dd-sdk-android-fresco:detektMain",
        ":dd-sdk-android-glide:detektMain",
        ":dd-sdk-android-ktx:detektMain",
        ":dd-sdk-android-ndk:detektMain",
        ":dd-sdk-android-rx:detektMain",
        ":dd-sdk-android-sqldelight:detektMain",
        ":dd-sdk-android-session-replay:detektMain",
        ":dd-sdk-android-timber:detektMain",
        ":dd-sdk-android-tv:detektMain",
        ":instrumented:integration:detekt",
        ":instrumented:nightly-tests:detekt",
        ":tools:unit:detekt"
    )
}

tasks.register("koverReportAll") {
    dependsOn(
        ":dd-sdk-android:koverXmlReport",
        ":dd-sdk-android-coil:koverXmlReport",
        ":dd-sdk-android-compose:koverXmlReport",
        ":dd-sdk-android-fresco:koverXmlReport",
        ":dd-sdk-android-glide:koverXmlReport",
        ":dd-sdk-android-ktx:koverXmlReport",
        ":dd-sdk-android-ndk:koverXmlReport",
        ":dd-sdk-android-rx:koverXmlReport",
        ":dd-sdk-android-session-replay:koverXmlReport",
        ":dd-sdk-android-sqldelight:koverXmlReport",
        ":dd-sdk-android-timber:koverXmlReport",
        ":dd-sdk-android-tv:koverXmlReport"
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
    dependsOn(":dd-sdk-android-ndk:assembleDebugAndroidTest")
    // we need this artifact to trick Bitrise
    dependsOn(":instrumented:integration:assembleDebug")
}

nightlyTestsCoverageConfig(threshold = 0.91f)
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

