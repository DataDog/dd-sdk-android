/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.gradle.plugin.logsconfig

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateLogsConfigTask : DefaultTask() {

    init {
        group = "datadog"
        description = "Generate Kotlin logging extension functions from logs_config.yaml"
    }

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:Input
    abstract val targetPackageName: Property<String>

    @get:Input
    abstract val loggerClassName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun performTask() {
        val yamlFile = inputFile.get().asFile
        logger.info("Parsing logs config from: ${yamlFile.absolutePath}")

        val config = LogsConfigYamlParser.parse(yamlFile)
        logger.info("Found ${config.logs.size} log entries")

        val outputDir = outputDirectory.get().asFile
        val generator = LogsConfigCodeGenerator(
            packageName = targetPackageName.get(),
            className = loggerClassName.get()
        )
        generator.generate(config, outputDir)

        logger.info("Generated ${loggerClassName.get()} in: ${outputDir.absolutePath}")
    }
}
