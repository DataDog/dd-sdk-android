/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import java.io.File
import java.util.Properties

fun Project.detektCustomConfig() {
    val ext = extensions.findByType<LibraryExtension>()

    tasks.register("printDetektClasspath") {
        group = "datadog"

        doLast {
            val fileTreeClassPathCollector = UnionFileTree(
                DefaultTaskDependencyFactory.withNoAssociatedProject()
            )
            val nonFileTreeClassPathCollector = mutableListOf<FileCollection>()

            val classpath = ext?.libraryVariants.orEmpty()
                .filter { it.name == "jvmDebug" || it.name == "debug" }
                .map { libVariant ->
                    // returns also test part of classpath for now, no idea how to filter it out
                    libVariant.getCompileClasspath(null).filter { it.exists() }
                }
                .firstOrNull()

            if (classpath is FileTree) {
                fileTreeClassPathCollector.addToUnion(classpath)
            } else if (classpath != null) {
                nonFileTreeClassPathCollector += classpath
            }

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

            val output = result.joinToString(File.pathSeparator) { it.absolutePath }
            File(projectDir, "detekt_classpath").writeText(output)
        }
    }

    tasks.register<Copy>("unzipAarForDetekt") {
        from(zipTree(layout.buildDirectory.file("outputs/aar/${project.name}-release.aar")))
        into(layout.buildDirectory.dir("extracted"))
    }

    tasks.register<JavaExec>("customDetektRules") {
        group = "datadog"

        classpath = files("${rootDir.absolutePath}/detekt-cli-1.23.4-all.jar")

        args(
            "--config",
            "${rootDir.absolutePath}/detekt_custom_general.yml," +
                "${rootDir.absolutePath}/detekt_custom_safe_calls.yml," +
                "${rootDir.absolutePath}/detekt_custom_unsafe_calls.yml"
        )
        args("--plugins", "${rootDir.absolutePath}/tools/detekt/build/libs/detekt.jar")
        args("-i", projectDir.absolutePath)
        args("-ex", "**/*.kts")
        args("--jvm-target", "11")

        val moduleDependencies = configurations
            .filter { it.name == "implementation" || it.name == "api" }
            .flatMap { it.dependencies.filterIsInstance<ProjectDependency>() }
            .map { it.path }
            .toSet()
            .let {
                // api configurations have canBeResolved=false, so we cannot go inside them to see transitive
                // module dependencies, so including common modules
                if (project.path == ":dd-sdk-android-internal") {
                    it
                } else if (project.path == ":dd-sdk-android-core") {
                    it + ":dd-sdk-android-internal"
                } else {
                    it + setOf(":dd-sdk-android-core", ":dd-sdk-android-internal")
                }
            }

        val externalDependencies = File("${projectDir.absolutePath}/detekt_classpath").readText()
        val moduleDependenciesClasses = moduleDependencies.map {
            "${rootDir.absolutePath}${it.replace(':', '/')}/build/extracted/classes.jar"
        }.joinToString(":")

        val dependencies = if (moduleDependenciesClasses.isBlank()) {
            externalDependencies
        } else {
            "$externalDependencies:$moduleDependenciesClasses"
        }

        args("-cp", dependencies)
    }
}
