/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.android.build.gradle.LibraryExtension
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.nightlyTestsCoverageConfig
import com.datadog.gradle.config.registerSubModuleAggregationTask
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import java.util.Properties

plugins {
    `maven-publish`
    id("io.github.gradle-nexus.publish-plugin")
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
        mavenLocal()
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

registerSubModuleAggregationTask("assembleLibraries", "assemble")
registerSubModuleAggregationTask("unitTestRelease", "testReleaseUnitTest")
registerSubModuleAggregationTask("unitTestDebug", "testDebugUnitTest")

tasks.register("unitTestTools") {
    dependsOn(
        ":sample:kotlin:assembleUs1Release",
        ":sample:wear:assembleUs1Release",
        ":sample:vendor-lib:assembleRelease",
        ":tools:unit:testJvmReleaseUnitTest",
        ":tools:detekt:test",
        ":tools:lint:test",
        ":tools:noopfactory:test"
    )
}

tasks.register("unitTestAll") {
    dependsOn(
        ":unitTestDebug",
        ":unitTestRelease",
        ":unitTestTools"
    )
}

registerSubModuleAggregationTask("lintCheckAll", "lintRelease") {
    dependsOn(":tools:lint:lint")
}
registerSubModuleAggregationTask("checkThirdPartyLicensesAll", "checkThirdPartyLicences")

registerSubModuleAggregationTask("checkApiSurfaceChangesAll", "checkApiSurfaceChanges")

/**
 * Task necessary to be compliant with the shared Android static analysis pipeline
 */
tasks.register("checkGeneratedFiles") {
    dependsOn("checkApiSurfaceChangesAll")
}

registerSubModuleAggregationTask("koverReportAll", "koverXmlReportRelease")

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

nightlyTestsCoverageConfig(threshold = 0.85f)

tasks.register("printSdkDebugRuntimeClasspath") {
    val fileTreeClassPathCollector = UnionFileTree(
        DefaultTaskDependencyFactory.withNoAssociatedProject()
    )
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

        val envSdkHome = System.getenv("ANDROID_SDK_ROOT")
        if (!envSdkHome.isNullOrBlank()) {
            val androidJarFilePath = listOf(
                envSdkHome,
                "platforms",
                "android-${AndroidConfig.TARGET_SDK}",
                "android.jar"
            )
            result += File(androidJarFilePath.joinToString(File.separator))
        }

        File("sdk_classpath").writeText(result.joinToString(File.pathSeparator) { it.absolutePath })
    }
}
