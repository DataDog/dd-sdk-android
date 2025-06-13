/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("StringLiteralDuplication")

import com.android.build.gradle.LibraryExtension
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.registerSubModuleAggregationTask
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import java.util.Properties

plugins {
    `maven-publish`
    alias(libs.plugins.nexusPublishGradlePlugin)
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
        classpath(libs.sqlDelightGradlePlugin)
        classpath(libs.binaryCompatibilityGradlePlugin)
        classpath(libs.kotlinxSerializationPlugin)
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
    delete(rootProject.layout.buildDirectory)
}

tasks.register("checkAll") {
    dependsOn(
        "lintCheckAll",
        "unitTestAll",
        "instrumentTestAll"
    )
}

registerSubModuleAggregationTask("assembleLibraries", "assemble")
registerSubModuleAggregationTask("assembleLibrariesDebug", "assembleDebug")
registerSubModuleAggregationTask("assembleLibrariesRelease", "assembleRelease")

registerSubModuleAggregationTask("unitTestRelease", "testReleaseUnitTest")
registerSubModuleAggregationTask(
    "unitTestReleaseFeatures",
    "testReleaseUnitTest",
    ":features:"
)
registerSubModuleAggregationTask("unitTestReleaseIntegrations", "testReleaseUnitTest", ":integrations:")

registerSubModuleAggregationTask("unitTestDebug", "testDebugUnitTest")
registerSubModuleAggregationTask(
    "unitTestDebugFeatures",
    "testDebugUnitTest",
    ":features:"
)
registerSubModuleAggregationTask("unitTestDebugIntegrations", "testDebugUnitTest", ":integrations:")

tasks.register("assembleSampleRelease") {
    dependsOn(
        ":sample:kotlin:assembleUs1Release",
        ":sample:wear:assembleUs1Release",
        ":sample:vendor-lib:assembleRelease",
        ":sample:automotive:assembleRelease",
        ":sample:tv:assembleRelease"
    )
}

tasks.register("unitTestTools") {
    dependsOn(
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
registerSubModuleAggregationTask("checkDependencyLicencesAll", "checkDependencyLicenses")

registerSubModuleAggregationTask("checkApiSurfaceChangesAll", "checkApiSurfaceChanges")

registerSubModuleAggregationTask("checkTransitiveDependenciesListAll", "checkTransitiveDependenciesList")

/**
 * Task necessary to be compliant with the shared Android static analysis pipeline
 */
tasks.register("checkGeneratedFiles") {
    dependsOn("checkDependencyLicencesAll")
    dependsOn("checkApiSurfaceChangesAll")
    dependsOn("checkTransitiveDependenciesListAll")
}

registerSubModuleAggregationTask("koverReportAll", "koverXmlReportRelease")
registerSubModuleAggregationTask("koverReportFeatures", "koverXmlReportRelease", ":features:")
registerSubModuleAggregationTask("koverReportIntegrations", "koverXmlReportRelease", ":integrations:")

registerSubModuleAggregationTask("printDetektClasspathAll", "printDetektClasspath")
registerSubModuleAggregationTask("printDetektClasspathFeatures", "printDetektClasspath", ":features:")
registerSubModuleAggregationTask("printDetektClasspathIntegrations", "printDetektClasspath", ":integrations:")

tasks.register("instrumentTestAll") {
    dependsOn(":instrumented:integration:connectedCheck")
}

tasks.register("buildIntegrationTestsArtifacts") {
    dependsOn(":instrumented:integration:assembleDebugAndroidTest")
    dependsOn(":instrumented:integration:assembleDebug")
}

tasks.register("buildNdkIntegrationTestsArtifacts") {
    dependsOn(":features:dd-sdk-android-ndk:assembleDebugAndroidTest")
    // we need this artifact to trick Bitrise
    dependsOn(":instrumented:integration:assembleDebug")
}

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
