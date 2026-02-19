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
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateLogsConfigTask : DefaultTask() {

    init {
        group = "datadog"
        description = "Generate Kotlin logging functions from YAML files in src/main/logs/"
    }

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputDirectory
    abstract val inputDirectory: DirectoryProperty

    @get:Input
    abstract val targetPackageName: Property<String>

    @get:Input
    abstract val loggerClassName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun performTask() {
        val logsDir = inputDirectory.get().asFile
        val yamlFiles = logsDir.listFiles { file ->
            file.isFile && (file.extension == "yaml" || file.extension == "yml")
        }?.sorted() ?: emptyList()

        if (yamlFiles.isEmpty()) {
            logger.info("No YAML files found in ${logsDir.absolutePath}, skipping generation")
            return
        }

        val allLogs = yamlFiles.flatMap { file ->
            logger.info("Parsing logs config from: ${file.name}")
            LogsConfigYamlParser.parse(file).logs
        }
        val mergedConfig = LogsConfig(logs = allLogs)
        logger.info("Found ${mergedConfig.logs.size} log entries across ${yamlFiles.size} file(s)")

        val outputDir = outputDirectory.get().asFile
        val generator = LogsConfigCodeGenerator(
            packageName = targetPackageName.get(),
            className = loggerClassName.get()
        )
        generator.generate(mergedConfig, outputDir)

        logger.info("Generated ${loggerClassName.get()} in: ${outputDir.absolutePath}")
    }
}
