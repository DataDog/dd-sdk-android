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

    private lateinit var extension: JsonSchemaExtension

    init {
        group = "datadog"
        description = "Review the Android benchmark results and ensure they fit the provided rules"
    }

    // region Task

    /**
     * The main [TaskAction].
     */
    @TaskAction
    fun performTask() {
        val inputDir = getInputDir()
        val outputDir = getOutputDir()
        val files = getInputFiles()
            .filter { it.name !in extension.ignoredFiles }

        println("Found ${files.size} in input dir: $inputDir")

        files.forEach {
            PokoGenerator(it, outputDir, extension.targetPackageName, extension.nameMapping)
                .generate()
        }
    }

    private fun getInputDir(): File {
        return File("${project.projectDir.path}${File.separator}${extension.inputDirPath}")
    }

    /**
     * The [InputFiles] (E.g.: all the json files in `resources/json`).
     */
    @InputFiles
    private fun getInputFiles(): List<File> {
        return getInputDir().listFiles().orEmpty().toList()
            .filter { it.extension == "json" }
    }

    /**
     * The [Input] package name of generated classes (E.g.: `com.example.model`).
     */
    @Input
    fun getInputPackageName(): String {
        return extension.inputDirPath
    }

    /**
     * The [Input] a list of input file name to ignore
     */
    @Input
    fun getInputIgnoredFiles(): Array<String> {
        return extension.ignoredFiles
    }

    /**
     * The [Input] a list of map from file name to type name
     */
    @Input
    fun getInputNameMapping(): Map<String, String> {
        return extension.nameMapping
    }

    /**
     * The [OutputDirectory] (`src/main/kotlin/{out_package}`).
     */
    @OutputDirectory
    private fun getOutputPackage(): File {
        val topDir = getOutputDir()
        val outputPackageDir = File(
            topDir.absolutePath + File.separator +
                extension.targetPackageName.replace('.', File.separatorChar)
        )
        return outputPackageDir
    }

    internal fun setParams(extension: JsonSchemaExtension) {
        this.extension = extension
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
