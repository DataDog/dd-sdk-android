/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("MatchingDeclarationName")

package com.datadog.gradle.config

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import java.io.File
import java.util.Properties

abstract class PrintDetektClassPath : DefaultTask() {

    @get:Input
    abstract val compileClassPath: Property<FileCollection>

    @get:Input
    abstract val projectDirectoryPath: Property<String>

    init {
        group = "datadog"
    }

    @TaskAction
    fun applyTask() {
        val fileTreeClassPathCollector = UnionFileTree(
            DefaultTaskDependencyFactory.withNoAssociatedProject()
        )
        val nonFileTreeClassPathCollector = mutableListOf<FileCollection>()

        if (compileClassPath.get() is FileTree) {
            fileTreeClassPathCollector.addToUnion(compileClassPath.get())
        } else {
            nonFileTreeClassPathCollector += compileClassPath.get()
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

        project.layout.projectDirectory
        val output = result.joinToString(File.pathSeparator) { it.absolutePath }
        File(File(projectDirectoryPath.get()), "detekt_classpath").writeText(output)
    }
}

fun Project.detektCustomConfig() {
    val printDetektClassPathTask = tasks.register<PrintDetektClassPath>("printDetektClasspath") {
        projectDirectoryPath.set(project.projectDir.absolutePath)
    }

    extensions.findByType<LibraryAndroidComponentsExtension>()
        ?.onVariants { variant ->
            if (variant.name == "jvmDebug" || variant.name == "debug") {
                // normally should be only one item anyway
                printDetektClassPathTask.configure {
                    compileClassPath.set(variant.compileClasspath.filter { it.exists() })
                    compileClassPath.finalizeValue()
                }
            }
        }

    tasks.register<Copy>("unzipAarForDetekt") {
        val aarFile = tasks.named<Zip>("bundleReleaseAar", Zip::class.java).flatMap { it.archiveFile }
        from(zipTree(aarFile))
        into(layout.buildDirectory.dir("extracted"))
    }

    tasks.register<JavaExec>("customDetektRules") {
        group = "datadog"
        maxHeapSize = "2g"
        dependsOn("printDetektClasspath")

        classpath = files("${rootDir.absolutePath}/detekt-cli-1.23.8-all.jar")

        args(
            "--config",
            "${rootDir.absolutePath}/detekt_custom_general.yml," +
                "${rootDir.absolutePath}/detekt_custom_safe_calls_android.yml," +
                "${rootDir.absolutePath}/detekt_custom_safe_calls_third_party.yml," +
                "${rootDir.absolutePath}/detekt_custom_unsafe_calls.yml"
        )
        args("--plugins", "${rootDir.absolutePath}/tools/detekt/build/libs/detekt.jar")
        args("-i", projectDir.absolutePath)
        args("-ex", "**/*.kts")
        args("--jvm-target", "11")

        // Resolve project paths at configuration time (config-cache friendly), but defer all
        // detekt_classpath file I/O to execution time. The file is produced by the
        // printDetektClasspath task, so it may not exist when this task is configured (e.g. while
        // computing the task graph for assembleLibrariesRelease). Reading it here would fail task
        // creation and break the whole build.
        val moduleDependencies = collectTransitiveProjectDependencies(project)
        val rootDirPath = rootDir.absolutePath
        val detektClasspathFile = File("${projectDir.absolutePath}/detekt_classpath")

        doFirst {
            val externalDependencies = detektClasspathFile.readText()
            val moduleDependenciesClasses = moduleDependencies.joinToString(":") {
                "$rootDirPath${it.replace(':', '/')}/build/extracted/classes.jar"
            }

            val dependencies = if (moduleDependenciesClasses.isBlank()) {
                externalDependencies
            } else {
                "$externalDependencies:$moduleDependenciesClasses"
            }

            args("-cp", dependencies)
        }
    }
}

private fun collectTransitiveProjectDependencies(project: Project): Set<String> {
    val rootProject = project.rootProject
    val visited = mutableSetOf<String>()
    val queue = ArrayDeque<Project>()
    queue.add(project)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val depPaths = current.configurations
            .filter { it.name == "implementation" || it.name == "api" }
            .flatMap { it.dependencies.filterIsInstance<ProjectDependency>() }
            .map { it.path }
            .filter { visited.add(it) }
        depPaths.mapNotNull { rootProject.findProject(it) }.forEach { queue.add(it) }
    }
    return visited
}
