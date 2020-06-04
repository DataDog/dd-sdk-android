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
import org.gradle.api.tasks.InputDirectory
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
    private lateinit var buildDir: File

    init {
        group = "code generation"
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
        val files = inputDir.listFiles() ?: return

        files.forEach {
            PokoGenerator(it, outputDir, extension.targetPackageName)
                .generate()
        }
    }

    /**
     * The [InputDirectory] (E.g.: `resources/json`).
     */
    @InputDirectory
    fun getInputDir(): File {
        return File(extension.inputDirPath)
    }

    /**
     * The [Input] package name of generated classes (E.g.: `com.example.model`).
     */
    @Input
    fun getInputPackageName(): String {
        return extension.inputDirPath
    }

    /**
     * The [OutputDirectory] (`build/generated/kotlin`).
     */
    @OutputDirectory
    fun getOutputDir(): File {
        val generatedDir = File(buildDir, "generated")
        val file = File(generatedDir, "kotlin")
        if (!file.exists()) file.mkdirs()
        return file
    }

    internal fun setParams(buildDir: File, extension: JsonSchemaExtension) {
        this.buildDir = buildDir
        this.extension = extension
    }

    // endregion
}
