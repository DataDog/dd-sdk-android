/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

// TODO test all from https://github.com/json-schema-org/JSON-Schema-Test-Suite/tree/master/tests/draft2019-09

/**
 * The main Gradle [Task].
 *
 * It will read source JsonSchema files and generate the relevant Kotlin data classes.
 */
open class GenerateJsonSchemaTask : DefaultTask() {


    init {
        group = "datadog"
        description = "Review the Android benchmark results and ensure they fit the provided rules"
    }

    // region Input/Output

    /**
     * The [InputFiles] (E.g.: all the json files in `resources/json`).
     */
    @InputFiles
    fun getInputFiles(): List<File> {
        return getInputDir().listFiles().orEmpty().toList()
            .filter { it.extension == "json" }
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
            .filter { it.name !in ignoredFiles }

        logger.info("Found ${files.size} files in input dir: $inputDir")

        val reader = JsonSchemaReader(inputNameMapping)
        val generator = PokoGenerator(outputDir, targetPackageName)
        files.forEach {
            val type = reader.readSchema(it)
            generator.generate(type)
        }
    }

    private fun getInputDir(): File {
        return File("${project.projectDir.path}${File.separator}${inputDirPath}")
    }

    private fun getOutputDir(): File {
        val srcDir = File(project.projectDir, "src")
        val mainDir = File(srcDir, "main")
        val file = File(mainDir, "kotlin")
        if (!file.exists()) file.mkdirs()
        return file
    }

    // endregion
}
