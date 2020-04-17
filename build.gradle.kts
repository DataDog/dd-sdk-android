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
        ":dd-sdk-android-timber:testDebugUnitTest",
        ":dd-sdk-android-timber:testReleaseUnitTest",
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

tasks.register("ktlintCheckAll") {
    dependsOn(
        ":dd-sdk-android:ktlintCheck",
        ":dd-sdk-android-timber:ktlintCheck",
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
        ":dd-sdk-android-timber:lintRelease"
    )
}

tasks.register("detektAll") {
    dependsOn(
        ":dd-sdk-android:detekt",
        ":dd-sdk-android-timber:detekt",
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
