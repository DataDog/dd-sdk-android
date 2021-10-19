import com.datadog.gradle.config.nightlyTestsCoverageConfig

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

plugins {
    `maven-publish`
    id("io.github.gradle-nexus.publish-plugin")
}

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { setUrl(com.datadog.gradle.Dependencies.Repositories.Gradle) }
    }

    dependencies {
        classpath(libs.androidToolsPluginGradle)
        classpath(libs.kotlinPluginGradle)
        classpath(libs.ktLintPluginGradle)
        classpath(libs.dokkaPluginGradle)
        classpath(libs.unmockPluginGradle)
        classpath(libs.realmPluginGradle)
        classpath(libs.sqlDelightPluginGradle)
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { setUrl(com.datadog.gradle.Dependencies.Repositories.Jitpack) }
        flatDir { dirs("libs") }
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
        "jacocoReportAll",
        "instrumentTestAll"
    )
}

tasks.register("assembleAll") {
    dependsOn(
        ":dd-sdk-android:assemble",
        ":dd-sdk-android-coil:assemble",
        ":dd-sdk-android-fresco:assemble",
        ":dd-sdk-android-glide:assemble",
        ":dd-sdk-android-ktx:assemble",
        ":dd-sdk-android-ndk:assemble",
        ":dd-sdk-android-rx:assemble",
        ":dd-sdk-android-sqldelight:assemble",
        ":dd-sdk-android-timber:assemble"
    )
}

tasks.register("unitTestRelease") {
    dependsOn(
        ":dd-sdk-android:testReleaseUnitTest",
        ":dd-sdk-android-coil:testReleaseUnitTest",
        ":dd-sdk-android-fresco:testReleaseUnitTest",
        ":dd-sdk-android-glide:testReleaseUnitTest",
        ":dd-sdk-android-ktx:testReleaseUnitTest",
        ":dd-sdk-android-ndk:testReleaseUnitTest",
        ":dd-sdk-android-rx:testReleaseUnitTest",
        ":dd-sdk-android-sqldelight:testReleaseUnitTest",
        ":dd-sdk-android-timber:testReleaseUnitTest"
    )
}

tasks.register("unitTestDebug") {
    dependsOn(
        ":dd-sdk-android:testDebugUnitTest",
        ":dd-sdk-android:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-coil:testDebugUnitTest",
        ":dd-sdk-android-coil:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-fresco:testDebugUnitTest",
        ":dd-sdk-android-fresco:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-glide:testDebugUnitTest",
        ":dd-sdk-android-glide:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-ktx:testDebugUnitTest",
        ":dd-sdk-android-ktx:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-ndk:testDebugUnitTest",
        ":dd-sdk-android-ndk:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-rx:testDebugUnitTest",
        ":dd-sdk-android-rx:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-sqldelight:testDebugUnitTest",
        ":dd-sdk-android-sqldelight:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-timber:testDebugUnitTest",
        ":dd-sdk-android-timber:jacocoTestDebugUnitTestReport"
    )
}

tasks.register("unitTestTools") {
    dependsOn(
        ":sample:kotlin:assembleRelease",
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
        ":dd-sdk-android-fresco:ktlintCheck",
        ":dd-sdk-android-glide:ktlintCheck",
        ":dd-sdk-android-ktx:ktlintCheck",
        ":dd-sdk-android-ndk:ktlintCheck",
        ":dd-sdk-android-rx:ktlintCheck",
        ":dd-sdk-android-sqldelight:ktlintCheck",
        ":dd-sdk-android-timber:ktlintCheck",
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
        ":dd-sdk-android-fresco:lintRelease",
        ":dd-sdk-android-glide:lintRelease",
        ":dd-sdk-android-ktx:lintRelease",
        ":dd-sdk-android-ndk:lintRelease",
        ":dd-sdk-android-rx:lintRelease",
        ":dd-sdk-android-sqldelight:lintRelease",
        ":dd-sdk-android-timber:lintRelease"
    )
}

tasks.register("detektAll") {
    dependsOn(
        ":dd-sdk-android:detekt",
        ":dd-sdk-android-coil:detekt",
        ":dd-sdk-android-fresco:detekt",
        ":dd-sdk-android-glide:detekt",
        ":dd-sdk-android-ktx:detekt",
        ":dd-sdk-android-ndk:detekt",
        ":dd-sdk-android-rx:detekt",
        ":dd-sdk-android-sqldelight:detekt",
        ":dd-sdk-android-timber:detekt",
        ":instrumented:integration:detekt",
        ":instrumented:nightly-tests:detekt",
        ":tools:unit:detekt"
    )
}

tasks.register("jacocoReportAll") {
    dependsOn(
        ":dd-sdk-android:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-android-coil:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-coil:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-android-fresco:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-fresco:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-android-glide:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-glide:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-android-ktx:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-ktx:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-android-ndk:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-ndk:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-android-rx:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-rx:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-android-sqldelight:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-sqldelight:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-android-timber:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-timber:jacocoTestReleaseUnitTestReport",
        ":tools:detekt:jacocoTestReport",
        ":tools:unit:jacocoTestDebugUnitTestReport",
        ":tools:unit:jacocoTestReleaseUnitTestReport"
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

nightlyTestsCoverageConfig(threshold = 0.94f)
