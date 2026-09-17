/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import com.datadog.gradle.config.taskConfig
import com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool
import java.nio.file.Paths

class ApiSurfacePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val srcDir = target.layout.projectDirectory
            .dir(Paths.get("src", "main").toString())
        val apiDir = target.layout.projectDirectory
            .dir("api")
        val kotlinSurfaceFile = apiDir.file(FILE_NAME)
        val javaSurfaceFile = apiDir.file("${target.name}.api")
        val compilerMetaFile = apiDir.file("compiler-meta.txt")

        val jsonToModelGenerations = target.tasks.withType<GenerateJsonSchemaTask>()
        val parserClasspath = target.createParserConfiguration()
        val generateApiSurfaceTask = target.tasks
            .register<GenerateApiSurfaceTask>(TASK_GEN_KOTLIN_API_SURFACE) {
                this.srcDir.set(srcDir)
                this.genDir.from(jsonToModelGenerations.map { it.destinationGenDirectory })
                this.parserClasspath.from(parserClasspath)
                this.surfaceFile.set(kotlinSurfaceFile)
            }
        target.tasks
            .register<CheckApiSurfaceTask>(TASK_CHECK_API_SURFACE) {
                this.kotlinSurfaceFile.set(generateApiSurfaceTask.flatMap { it.surfaceFile })
                if (target.plugins.hasPlugin(GEN_JAVA_API_LAYOUT_PLUGIN)) {
                    this.javaSurfaceFile.set(javaSurfaceFile)
                    dependsOn(target.tasks.matching { it.name == TASK_GEN_JAVA_API_SURFACE })
                } else {
                    logger.info(
                        "No Java API layout plugin found, skipping API surface" +
                            " check for Java API surface."
                    )
                }
            }

        val kotlinCompilations = target.tasks.withType<KotlinCompilationTask<KotlinJvmCompilerOptions>>()
        val generateCompilerMetaTask = target.tasks
            .register<GenerateCompilerMetaTask>(TASK_GEN_COMPILER_METADATA) {
                compiledClassesDirectory.set(
                    kotlinCompilations.named("compileDebugKotlin")
                        .flatMap { (it as KotlinCompileTool).destinationDirectory }
                )
                metadataInfoFile.set(compilerMetaFile)
            }

        target.tasks
            .register<CheckCompilerMetaTask>(TASK_CHECK_COMPILER_METADATA) {
                this.metadataInfoFile.set(compilerMetaFile)
                dependsOn(TASK_GEN_COMPILER_METADATA)
            }

        target.taskConfig<KotlinCompilationTask<KotlinJvmCompilerOptions>> {
            if (name == "compileDebugKotlin") {
                finalizedBy(generateCompilerMetaTask)
            }
            // Java API generation task does a clean-up of all files in the output
            // folder, so let it run first
            if (target.plugins.hasPlugin(GEN_JAVA_API_LAYOUT_PLUGIN)) {
                val apiDumpTasks = target.tasks.matching { it.name == TASK_GEN_JAVA_API_SURFACE }
                val isCi = target.providers.environmentVariable("CI").isPresent
                if (isCi) {
                    // Java API generation wires to the release build type, so we can afford triggering compilation
                    // of release type locally when we run debug compilation, but we would like to avoid it on CI
                    if (name == "compileReleaseKotlin") {
                        finalizedBy(apiDumpTasks)
                    }
                } else {
                    finalizedBy(apiDumpTasks)
                }
            }
            finalizedBy(generateApiSurfaceTask)
        }
    }

    /**
     * Classpath the API surface generator parses sources with. It is resolved as a project
     * dependency rather than shipped with this plugin, to keep a second copy of
     * `kotlin-compiler-embeddable` off the buildscript classpath, where it would clash with the
     * one bundled with the Kotlin Gradle plugin.
     */
    private fun Project.createParserConfiguration(): Configuration {
        val kotlinVersion = extensions.getByType<VersionCatalogsExtension>()
            .named("libs")
            .findVersion("kotlin")
            .orElseThrow { IllegalStateException("No `kotlin` version found in the version catalog") }
        return configurations.create(CONFIGURATION_PARSER) {
            isCanBeConsumed = false
            isCanBeResolved = true
            isVisible = false
            dependencies.add(
                this@createParserConfiguration.dependencies
                    .create("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
            )
        }
    }

    companion object {
        const val CONFIGURATION_PARSER = "apiSurfaceParser"
        const val TASK_GEN_KOTLIN_API_SURFACE = "generateApiSurface"
        const val TASK_GEN_COMPILER_METADATA = "generateCompilerMetadata"
        const val TASK_GEN_JAVA_API_SURFACE = "apiDump"
        const val TASK_CHECK_API_SURFACE = "checkApiSurfaceChanges"
        const val TASK_CHECK_COMPILER_METADATA = "checkCompilerMetadataChanges"
        const val FILE_NAME = "apiSurface"
        const val GEN_JAVA_API_LAYOUT_PLUGIN = "binary-compatibility-validator"
    }
}
