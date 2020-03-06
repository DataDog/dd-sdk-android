/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

import com.datadog.gradle.plugin.gitdiff.gitDiffTask

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
    }
}

plugins {
    id("gitDiffConditional")
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

gitDiffTask("unitTestChanged") {
    dependsOnDiff(
        "dd-sdk-android/.*",
        ":dd-sdk-android:testDebugUnitTest",
        ":dd-sdk-android:testReleaseUnitTest",
        ":sample:java:assembleDebug",
        ":sample:kotlin:assembleDebug",
        ":sample:kotlin-timber:assembleDebug"
    )
    dependsOnDiff(
        "dd-sdk-android-timber/.*",
        ":dd-sdk-android-timber:testDebugUnitTest",
        ":dd-sdk-android-timber:testReleaseUnitTest",
        ":sample:kotlin-timber:assembleDebug"
    )
    dependsOnDiff(
        "dd-sdk-androidx-fragments/.*",
        ":dd-sdk-androidx-fragments:testDebugUnitTest",
        ":dd-sdk-androidx-fragments:testReleaseUnitTest"
    )
    dependsOnDiff(
        "dd-sdk-support-fragments/.*",
        ":dd-sdk-support-fragments:testDebugUnitTest",
        ":dd-sdk-support-fragments:testReleaseUnitTest"
    )
    dependsOnDiff(
        "tools/detekt/.*",
        ":tools:detekt:test"
    )
    dependsOnDiff(
        "tools/unit/.*",
        ":tools:unit:testDebugUnitTest",
        ":tools:unit:testReleaseUnitTest"
    )
    dependsOnDiff(
        "sample/java/.*",
        ":sample:java:assembleDebug"
    )
    dependsOnDiff(
        "sample/kotlin/.*",
        ":sample:kotlin:assembleDebug"
    )
    dependsOnDiff(
        "sample/kotlin/.*",
        ":sample:kotlin-timber:assembleDebug"
    )
}

gitDiffTask("unitTestAll") {
    dependsOn(
        ":dd-sdk-android:testDebugUnitTest",
        ":dd-sdk-android:testReleaseUnitTest",
        ":dd-sdk-android-timber:testDebugUnitTest",
        ":dd-sdk-android-timber:testReleaseUnitTest",
        ":dd-sdk-androidx-fragments:testDebugUnitTest",
        ":dd-sdk-androidx-fragments:testReleaseUnitTest",
        ":dd-sdk-support-fragments:testDebugUnitTest",
        ":dd-sdk-support-fragments:testReleaseUnitTest",
        ":sample:java:assembleDebug",
        ":sample:kotlin:assembleDebug",
        ":sample:kotlin-timber:assembleDebug",
        ":tools:detekt:test",
        ":tools:unit:testDebugUnitTest",
        ":tools:unit:testReleaseUnitTest",
        ":sample:java:assembleDebug",
        ":sample:kotlin:assembleDebug",
        ":sample:kotlin-timber:assembleDebug"
    )
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

tasks.register("ktlintCheckAll") {
    dependsOn(
        ":dd-sdk-android:ktlintCheck",
        ":dd-sdk-android-timber:ktlintCheck",
        ":dd-sdk-androidx-fragments:ktlintCheck",
        ":dd-sdk-support-fragments:ktlintCheck",
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
        ":dd-sdk-android-timber:lintDebug",
        ":dd-sdk-android-timber:lintRelease",
        ":dd-sdk-androidx-fragments:lintDebug",
        ":dd-sdk-androidx-fragments:lintRelease",
        ":dd-sdk-support-fragments:lintDebug",
        ":dd-sdk-support-fragments:lintRelease"
    )
}

tasks.register("detektAll") {
    dependsOn(
        ":dd-sdk-android:detekt",
        ":dd-sdk-android-timber:detekt",
        ":dd-sdk-androidx-fragments:detekt",
        ":dd-sdk-support-fragments:detekt",
        ":instrumented:integration:detekt",
        ":instrumented:benchmark:detekt",
        ":tools:unit:detekt"
    )
}

tasks.register("jacocoReportAll") {
    dependsOn(
        ":dd-sdk-android:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-android-timber:jacocoTestDebugUnitTestReport",
        ":dd-sdk-android-timber:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-androidx-fragments:jacocoTestDebugUnitTestReport",
        ":dd-sdk-androidx-fragments:jacocoTestReleaseUnitTestReport",
        ":dd-sdk-support-fragments:jacocoTestDebugUnitTestReport",
        ":dd-sdk-support-fragments:jacocoTestReleaseUnitTestReport",
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
