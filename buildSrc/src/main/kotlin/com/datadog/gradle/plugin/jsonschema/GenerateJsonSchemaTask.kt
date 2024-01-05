/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import com.datadog.gradle.plugin.jsonschema.generator.FileGenerator
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

// TODO test all from https://github.com/json-schema-org/JSON-Schema-Test-Suite/tree/master/tests/draft2019-09

/**
 * The main Gradle [Task].
 *
 * It will read source JsonSchema files and generate the relevant Kotlin data classes.
 */
@CacheableTask
open class GenerateJsonSchemaTask : DefaultTask() {

    init {
        group = "datadog"
        description = "Read source JSON schema files and generate the relevant Kotlin data classes"
    }

    // region Input/Output

    /**
     * The [InputFiles] (E.g.: all the json files in `resources/json`). Note, that it will get all
     * files recursively in order to keep Gradle build cache state in a proper way, but only
     * top-level files are used for the model generation (they can be just a reference to the
     * deeper files, so they may receive no changes making Gradle build cache to have
     * a wrong caching decision when deeper files actually change).
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    fun getInputFiles(): List<File> {
        return getInputDir()
            .walkBottomUp()
            .filter {
                it.isFile && it.extension == "json"
            }
            .toList()
    }

    /**
     * The directory from which to read the files json schema files.
     */
    @Input
    var inputDirPath: String = ""

    /**
     * The package name where to generate the models based on the schema files.
     * (E.g.: `com.example.model`).
     */
    @Input
    var targetPackageName: String = ""

    /**
     * The list of schema files to be ignored.
     */
    @Input
    var ignoredFiles: Array<String> = emptyArray()

    /**
     * The mapping of the schema file to the generated model name. Mostly used for merged
     * schemas.
     */
    @Input
    var inputNameMapping: Map<String, String> = emptyMap()

    /**
     * The [OutputDirectory] (`src/main/kotlin/{out_package}`).
     */
    @OutputDirectory
    fun getOutputPackage(): File {
        val topDir = getOutputDir()
        val outputPackageDir = File(
            topDir.absolutePath + File.separator +
                targetPackageName.replace('.', File.separatorChar)
        )
        return outputPackageDir
    }

    // endregion

    // region Task action

    /**
     * The main [TaskAction].
     */
    @TaskAction
    fun performTask() {
        val inputDir = getInputDir()
        val outputDir = getOutputDir()
        val files = getInputFiles()
            .filter {
                it.name !in ignoredFiles && it.parentFile == inputDir
            }

        logger.info("Found ${files.size} files in input dir: $inputDir")

        val reader = JsonSchemaReader(inputNameMapping, logger)
        val generator = FileGenerator(outputDir, targetPackageName, logger)
        files.forEach {
            val type = reader.readSchema(it)
            generator.generate(type)
        }
    }

    private fun getInputDir(): File {
        return File("${project.projectDir.path}${File.separator}$inputDirPath")
    }

    private fun getOutputDir(): File {
        val genDir = File(project.buildDir, "generated")
        val json2kotlinDir = File(genDir, "json2kotlin")
        val mainDir = File(json2kotlinDir, "main")
        val file = File(mainDir, "kotlin")
        if (!file.exists()) file.mkdirs()
        return file
    }

    // endregion
}
