/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.gradle.plugin.logsconfig

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class GenerateLogsConfigTask : DefaultTask() {

    init {
        group = "datadog"
        description = "Generate Kotlin logging/metric functions from YAML files in src/main/logs/ and src/main/metrics/"
    }

    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputDirectory
    abstract val inputDirectory: DirectoryProperty

    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputDirectory
    abstract val metricsInputDirectory: DirectoryProperty

    @get:Input
    abstract val targetPackageName: Property<String>

    @get:Input
    abstract val loggerClassName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun performTask() {
        val logEntries = collectYamlFiles(inputDirectory).flatMap { file ->
            logger.info("Parsing logs config from: ${file.name}")
            LogsConfigYamlParser.parseLogs(file)
        }
        val metricEntries = collectYamlFiles(metricsInputDirectory).flatMap { file ->
            logger.info("Parsing metrics config from: ${file.name}")
            LogsConfigYamlParser.parseMetrics(file)
        }

        val allEntries = logEntries + metricEntries
        if (allEntries.isEmpty()) {
            logger.info("No log or metric entries found, skipping generation")
            return
        }

        val mergedConfig = LogsConfig(logs = allEntries)
        logger.info("Found ${mergedConfig.logs.size} entries (${logEntries.size} logs, ${metricEntries.size} metrics)")

        val outputDir = outputDirectory.get().asFile
        val generator = LogsConfigCodeGenerator(
            packageName = targetPackageName.get(),
            className = loggerClassName.get()
        )
        generator.generate(mergedConfig, outputDir)

        logger.info("Generated ${loggerClassName.get()} in: ${outputDir.absolutePath}")
    }

    private fun collectYamlFiles(directory: DirectoryProperty): List<File> {
        if (!directory.isPresent) return emptyList()
        val dir = directory.get().asFile
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file ->
            file.isFile && (file.extension == "yaml" || file.extension == "yml")
        }?.sorted() ?: emptyList()
    }
}
