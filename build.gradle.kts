/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.android.build.gradle.LibraryExtension
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.nightlyTestsCoverageConfig
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import java.util.Properties

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
        classpath(libs.binaryCompatibilityGradlePlugin)
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
    this.repositories {
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
        ":integrations:dd-sdk-android-coil:assemble",
        ":integrations:dd-sdk-android-compose:assemble",
        ":integrations:dd-sdk-android-fresco:assemble",
        ":integrations:dd-sdk-android-glide:assemble",
        ":integrations:dd-sdk-android-ktx:assemble",
        ":integrations:dd-sdk-android-rx:assemble",
        ":integrations:dd-sdk-android-sqldelight:assemble",
        ":integrations:dd-sdk-android-timber:assemble",
        ":integrations:dd-sdk-android-tv:assemble",
        ":integrations:dd-sdk-android-okhttp:assemble",
        ":features:dd-sdk-android-session-replay:assemble",
        ":features:dd-sdk-android-session-replay-material:assemble",
        ":features:dd-sdk-android-logs:assemble",
        ":features:dd-sdk-android-ndk:assemble",
        ":features:dd-sdk-android-trace:assemble",
        ":features:dd-sdk-android-webview:assemble",
        ":features:dd-sdk-android-rum:assemble"
    )
}

tasks.register("unitTestRelease") {
    dependsOn(
        ":dd-sdk-android:testReleaseUnitTest",
        ":integrations:dd-sdk-android-coil:testReleaseUnitTest",
        ":integrations:dd-sdk-android-compose:testReleaseUnitTest",
        ":integrations:dd-sdk-android-fresco:testReleaseUnitTest",
        ":integrations:dd-sdk-android-glide:testReleaseUnitTest",
        ":integrations:dd-sdk-android-ktx:testReleaseUnitTest",
        ":integrations:dd-sdk-android-rx:testReleaseUnitTest",
        ":integrations:dd-sdk-android-sqldelight:testReleaseUnitTest",
        ":integrations:dd-sdk-android-timber:testReleaseUnitTest",
        ":integrations:dd-sdk-android-tv:testReleaseUnitTest",
        ":integrations:dd-sdk-android-okhttp:testReleaseUnitTest",
        ":features:dd-sdk-android-session-replay:testReleaseUnitTest",
        ":features:dd-sdk-android-session-replay-material:testReleaseUnitTest",
        ":features:dd-sdk-android-logs:testReleaseUnitTest",
        ":features:dd-sdk-android-ndk:testReleaseUnitTest",
        ":features:dd-sdk-android-trace:testReleaseUnitTest",
        ":features:dd-sdk-android-webview:testReleaseUnitTest",
        ":features:dd-sdk-android-rum:testReleaseUnitTest"
    )
}

tasks.register("unitTestDebug") {
    dependsOn(
        ":dd-sdk-android:testDebugUnitTest",
        ":integrations:dd-sdk-android-coil:testDebugUnitTest",
        ":integrations:dd-sdk-android-compose:testDebugUnitTest",
        ":integrations:dd-sdk-android-fresco:testDebugUnitTest",
        ":integrations:dd-sdk-android-glide:testDebugUnitTest",
        ":integrations:dd-sdk-android-ktx:testDebugUnitTest",
        ":integrations:dd-sdk-android-rx:testDebugUnitTest",
        ":integrations:dd-sdk-android-sqldelight:testDebugUnitTest",
        ":integrations:dd-sdk-android-timber:testDebugUnitTest",
        ":integrations:dd-sdk-android-tv:testDebugUnitTest",
        ":integrations:dd-sdk-android-okhttp:testDebugUnitTest",
        ":features:dd-sdk-android-session-replay:testDebugUnitTest",
        ":features:dd-sdk-android-session-replay-material:testDebugUnitTest",
        ":features:dd-sdk-android-logs:testDebugUnitTest",
        ":features:dd-sdk-android-ndk:testDebugUnitTest",
        ":features:dd-sdk-android-trace:testDebugUnitTest",
        ":features:dd-sdk-android-webview:testDebugUnitTest",
        ":features:dd-sdk-android-rum:testDebugUnitTest"
    )
}

tasks.register("unitTestTools") {
    dependsOn(
        ":sample:kotlin:assembleUs1Release",
        ":tools:unit:testJvmReleaseUnitTest"
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
        ":integrations:dd-sdk-android-coil:lintRelease",
        ":integrations:dd-sdk-android-compose:lintRelease",
        ":integrations:dd-sdk-android-fresco:lintRelease",
        ":integrations:dd-sdk-android-glide:lintRelease",
        ":integrations:dd-sdk-android-ktx:lintRelease",
        ":integrations:dd-sdk-android-rx:lintRelease",
        ":integrations:dd-sdk-android-sqldelight:lintRelease",
        ":integrations:dd-sdk-android-timber:lintRelease",
        ":integrations:dd-sdk-android-tv:lintRelease",
        ":integrations:dd-sdk-android-okhttp:lintRelease",
        ":features:dd-sdk-android-session-replay:lintRelease",
        ":features:dd-sdk-android-session-replay-material:lintRelease",
        ":features:dd-sdk-android-logs:lintRelease",
        ":features:dd-sdk-android-ndk:lintRelease",
        ":features:dd-sdk-android-trace:lintRelease",
        ":features:dd-sdk-android-webview:lintRelease",
        ":features:dd-sdk-android-rum:lintRelease"
    )
}

tasks.register("checkThirdPartyLicensesAll") {
    dependsOn(
        ":dd-sdk-android:checkThirdPartyLicences",
        ":integrations:dd-sdk-android-coil:checkThirdPartyLicences",
        ":integrations:dd-sdk-android-compose:checkThirdPartyLicences",
        ":integrations:dd-sdk-android-fresco:checkThirdPartyLicences",
        ":integrations:dd-sdk-android-glide:checkThirdPartyLicences",
        ":integrations:dd-sdk-android-ktx:checkThirdPartyLicences",
        ":integrations:dd-sdk-android-rx:checkThirdPartyLicences",
        ":integrations:dd-sdk-android-sqldelight:checkThirdPartyLicences",
        ":integrations:dd-sdk-android-timber:checkThirdPartyLicences",
        ":integrations:dd-sdk-android-tv:checkThirdPartyLicences",
        ":integrations:dd-sdk-android-okhttp:checkThirdPartyLicences",
        ":features:dd-sdk-android-session-replay:checkThirdPartyLicences",
        ":features:dd-sdk-android-session-replay-material:checkThirdPartyLicences",
        ":features:dd-sdk-android-logs:checkThirdPartyLicences",
        ":features:dd-sdk-android-ndk:checkThirdPartyLicences",
        ":features:dd-sdk-android-trace:checkThirdPartyLicences",
        ":features:dd-sdk-android-webview:checkThirdPartyLicences",
        ":features:dd-sdk-android-rum:checkThirdPartyLicences"
    )
}

tasks.register("checkApiSurfaceChangesAll") {
    dependsOn(
        "dd-sdk-android:checkApiSurfaceChanges",
        ":integrations:dd-sdk-android-coil:checkApiSurfaceChanges",
        ":integrations:dd-sdk-android-compose:checkApiSurfaceChanges",
        ":integrations:dd-sdk-android-fresco:checkApiSurfaceChanges",
        ":integrations:dd-sdk-android-glide:checkApiSurfaceChanges",
        ":integrations:dd-sdk-android-ktx:checkApiSurfaceChanges",
        ":integrations:dd-sdk-android-rx:checkApiSurfaceChanges",
        ":integrations:dd-sdk-android-sqldelight:checkApiSurfaceChanges",
        ":integrations:dd-sdk-android-timber:checkApiSurfaceChanges",
        ":integrations:dd-sdk-android-tv:checkApiSurfaceChanges",
        ":integrations:dd-sdk-android-okhttp:checkApiSurfaceChanges",
        ":features:dd-sdk-android-session-replay:checkApiSurfaceChanges",
        ":features:dd-sdk-android-session-replay-material:checkApiSurfaceChanges",
        ":features:dd-sdk-android-logs:checkApiSurfaceChanges",
        ":features:dd-sdk-android-ndk:checkApiSurfaceChanges",
        ":features:dd-sdk-android-trace:checkApiSurfaceChanges",
        ":features:dd-sdk-android-webview:checkApiSurfaceChanges",
        ":features:dd-sdk-android-rum:checkApiSurfaceChanges"
    )
}

tasks.register("checkGeneratedFiles") {
    dependsOn("checkApiSurfaceChangesAll")
}

tasks.register("koverReportAll") {
    dependsOn(
        ":dd-sdk-android:koverXmlReport",
        ":integrations:dd-sdk-android-coil:koverXmlReport",
        ":integrations:dd-sdk-android-compose:koverXmlReport",
        ":integrations:dd-sdk-android-fresco:koverXmlReport",
        ":integrations:dd-sdk-android-glide:koverXmlReport",
        ":integrations:dd-sdk-android-ktx:koverXmlReport",
        ":integrations:dd-sdk-android-rx:koverXmlReport",
        ":integrations:dd-sdk-android-sqldelight:koverXmlReport",
        ":integrations:dd-sdk-android-timber:koverXmlReport",
        ":integrations:dd-sdk-android-tv:koverXmlReport",
        ":integrations:dd-sdk-android-okhttp:koverXmlReport",
        ":features:dd-sdk-android-session-replay:koverXmlReport",
        ":features:dd-sdk-android-session-replay-material:koverXmlReport",
        ":features:dd-sdk-android-logs:koverXmlReport",
        ":features:dd-sdk-android-ndk:koverXmlReport",
        ":features:dd-sdk-android-trace:koverXmlReport",
        ":features:dd-sdk-android-webview:koverXmlReport",
        ":features:dd-sdk-android-rum:koverXmlReport"
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
    dependsOn(":features:dd-sdk-android-ndk:assembleDebugAndroidTest")
    // we need this artifact to trick Bitrise
    dependsOn(":instrumented:integration:assembleDebug")
}

nightlyTestsCoverageConfig(threshold = 0.84f)
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

tasks.register("printSdkDebugRuntimeClasspath") {
    val fileTreeClassPathCollector =
        UnionFileTree(DefaultTaskDependencyFactory.withNoAssociatedProject())
    val nonFileTreeClassPathCollector = mutableListOf<FileCollection>()

    allprojects.minus(project).forEach { subproject ->
        val childTask = subproject.tasks.register("printDebugRuntimeClasspath") {
            doLast {
                val ext =
                    subproject.extensions.findByType(LibraryExtension::class.java) ?: return@doLast
                val classpath = ext.libraryVariants
                    .filter { it.name == "jvmDebug" || it.name == "debug" }
                    .map { libVariant ->
                        // returns also test part of classpath for now, no idea how to filter it out
                        libVariant.getCompileClasspath(null).filter { it.exists() }
                    }
                    .first()
                if (classpath is FileTree) {
                    fileTreeClassPathCollector.addToUnion(classpath)
                } else {
                    nonFileTreeClassPathCollector += classpath
                }
            }
        }
        this@register.dependsOn(childTask)
    }
    doLast {
        val fileCollections = mutableListOf<FileCollection>()
        fileCollections.addAll(nonFileTreeClassPathCollector)
        if (!fileTreeClassPathCollector.isEmpty) {
            fileCollections.add(fileTreeClassPathCollector)
        }
        val result = fileCollections.flatMap {
            it.files
        }.toMutableSet()

        val localPropertiesFile = File(project.rootDir, "local.properties")
        if (localPropertiesFile.exists()) {
            val localProperties = Properties().apply {
                localPropertiesFile.inputStream().use { load(it) }
            }
            val sdkDirPath = localProperties["sdk.dir"]
            val androidJarFilePath = listOf(
                sdkDirPath,
                "platforms",
                "android-${AndroidConfig.TARGET_SDK}",
                "android.jar"
            )
            result += File(androidJarFilePath.joinToString(File.separator))
        }

        File("sdk_classpath").writeText(result.joinToString(File.pathSeparator) { it.absolutePath })
    }
}
