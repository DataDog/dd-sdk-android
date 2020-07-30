/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { setUrl(com.datadog.gradle.Dependencies.Repositories.Gradle) }
        jcenter()
    }

    dependencies {
        classpath(com.datadog.gradle.Dependencies.ClassPaths.AndroidTools)
        classpath(com.datadog.gradle.Dependencies.ClassPaths.AndroidBenchmark)
        classpath(com.datadog.gradle.Dependencies.ClassPaths.Kotlin)
        classpath(com.datadog.gradle.Dependencies.ClassPaths.KtLint)
        classpath(com.datadog.gradle.Dependencies.ClassPaths.Dokka)
        classpath(com.datadog.gradle.Dependencies.ClassPaths.Bintray)
        classpath(com.datadog.gradle.Dependencies.ClassPaths.Unmock)
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { setUrl(com.datadog.gradle.Dependencies.Repositories.Jitpack) }
        jcenter()
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

tasks.register("unitTestAll") {
    dependsOn(
        ":dd-sdk-android:testDebugUnitTest",
        ":dd-sdk-android:testReleaseUnitTest",
        ":dd-sdk-android-glide:testDebugUnitTest",
        ":dd-sdk-android-glide:testReleaseUnitTest",
        ":dd-sdk-android-ktx:testDebugUnitTest",
        ":dd-sdk-android-ktx:testReleaseUnitTest",
        ":dd-sdk-android-timber:testDebugUnitTest",
        ":dd-sdk-android-timber:testReleaseUnitTest",
        ":dd-sdk-android-ndk:testDebugUnitTest",
        ":dd-sdk-android-ndk:testReleaseUnitTest",
        ":sample:java:assembleDebug",
        ":sample:kotlin:assembleDebug",
        ":tools:detekt:test",
        ":tools:unit:testDebugUnitTest",
        ":tools:unit:testReleaseUnitTest"
    )
}

tasks.register("ktlintCheckAll") {
    dependsOn(
        ":dd-sdk-android:ktlintCheck",
        ":dd-sdk-android-glide:ktlintCheck",
        ":dd-sdk-android-ktx:ktlintCheck",
        ":dd-sdk-android-timber:ktlintCheck",
        ":dd-sdk-android-ndk:ktlintCheck",
        ":instrumented:integration:ktlintCheck",
        ":instrumented:benchmark:ktlintCheck",
        ":tools:detekt:ktlintCheck",
        ":tools:unit:ktlintCheck"
    )
}

tasks.register("lintCheckAll") {
    dependsOn(
        ":dd-sdk-android:lintDebug",
        ":dd-sdk-android:lintRelease",
        ":dd-sdk-android-glide:lintDebug",
        ":dd-sdk-android-glide:lintRelease",
        ":dd-sdk-android-ktx:lintDebug",
        ":dd-sdk-android-ktx:lintRelease",
        ":dd-sdk-android-timber:lintDebug",
        ":dd-sdk-android-timber:lintRelease",
        ":dd-sdk-android-ndk:lintDebug",
        ":dd-sdk-android-ndk:lintRelease"
    )
}

tasks.register("detektAll") {
    dependsOn(
        ":dd-sdk-android:detekt",
        ":dd-sdk-android-glide:detekt",
        ":dd-sdk-android-ktx:detekt",
        ":dd-sdk-android-timber:detekt",
        ":dd-sdk-android-ndk:detekt",
        ":instrumented:integration:detekt",
        ":instrumented:benchmark:detekt",
        ":tools:unit:detekt"
    )
}

tasks.register("jacocoReportAll") {
    dependsOn(
        ":dd-sdk-android:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-android-glide:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-glide:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-android-ktx:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-ktx:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-android-ndk:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-ndk:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-android-timber:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-timber:jacocoTestReleaseUnitTestReport",
        ":tools:detekt:jacocoTestReport",
        ":tools:unit:jacocoTestDebugUnitTestReport",
        ":tools:unit:jacocoTestReleaseUnitTestReport"
    )
}

tasks.register("instrumentTestAll") {
    dependsOn(":instrumented:integration:connectedCheck")
    dependsOn(":instrumented:benchmark:connectedCheck")
}

tasks.register("buildIntegrationTestsArtifacts") {
    dependsOn(":instrumented:integration:assembleDebugAndroidTest")
    dependsOn(":instrumented:integration:assembleDebug")
}

tasks.register("buildNdkIntegrationTestsArtifacts") {
    dependsOn(":dd-sdk-android-ndk:assembleDebugAndroidTest")
    // we need this artifact to trick Bitrise
    dependsOn(":instrumented:integration:assembleDebug")
}
