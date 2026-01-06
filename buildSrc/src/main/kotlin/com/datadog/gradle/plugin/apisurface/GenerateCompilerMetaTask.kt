/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.internal.ExecException
import java.io.ByteArrayOutputStream
import javax.inject.Inject

abstract class GenerateCompilerMetaTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    @get:OutputFile
    abstract val metadataInfoFile: RegularFileProperty

    @get:InputDirectory
    abstract val compiledClassesDirectory: DirectoryProperty

    init {
        group = "datadog"
        description = "List compiler metadata properties"
    }

    @TaskAction
    fun applyTask() {
        val classesDir = compiledClassesDirectory.get().asFile

        val classFile = classesDir
            .walkTopDown()
            .filter {
                it.extension == "class" &&
                    !it.name.contains("$") &&
                    it.path.contains("datadog") &&
                    !it.path.contains("test", ignoreCase = true)
            }
            .firstOrNull()

        checkNotNull(classFile) {
            "Couldn't find any class file to get compilation metadata, did a search in $classesDir"
        }

        // could try Class.forName, but some modules like Coil have only one class generated and loading it
        // requires 3rd party classpath to be available
        val result = execOperations.execShell("javap", "-v", classFile.absolutePath)
            .lines()
            .map { it.trim() }

        val kotlinAbiVersion = result
            .filter { it.startsWith("mv=[") }
            .first()
            .removePrefix("mv=[")
            .removeSuffix("]")
            .replace(",", ".")

        val jvmBytecodeVersion = result
            .filter { it.startsWith("major version: ") }
            .first()
            .removePrefix("major version: ")
            .toInt()
            .let {
                // Java 25
                require(it <= 69) { "Unsupported JVM major version value: $it" }
                // at least between Java 25 and Java 5 this formula is true
                it - 44
            }

        metadataInfoFile.get().asFile
            .writeText(
                "kotlin_abi_version=$kotlinAbiVersion\n" +
                    "jvm_bytecode_version=$jvmBytecodeVersion\n"
            )
    }

    private fun ExecOperations.execShell(vararg command: String): String {
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        try {
            this.exec {
                commandLine(*command)
                standardOutput = outputStream
                errorOutput = errorStream
            }.assertNormalExitValue()
        } catch (e: ExecException) {
            logger.error(errorStream.toString(Charsets.UTF_8))
            throw e
        }

        return outputStream.toString(Charsets.UTF_8)
    }
}
