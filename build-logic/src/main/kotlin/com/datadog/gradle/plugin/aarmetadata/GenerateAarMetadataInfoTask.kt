/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.aarmetadata

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

abstract class GenerateAarMetadataInfoTask : DefaultTask() {

    @get:InputFile
    abstract val aarFile: RegularFileProperty

    @get:OutputFile
    abstract val aarMetadataFile: RegularFileProperty

    init {
        group = "datadog"
        description = "List the AAR metadata properties of the produced AAR"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        val aar = aarFile.get().asFile

        val properties = ZipFile(aar).use { zip ->
            val entry = zip.getEntry(AAR_METADATA_ENTRY)
            checkNotNull(entry) {
                "Couldn't find $AAR_METADATA_ENTRY entry in the ${aar.absolutePath}"
            }
            zip.getInputStream(entry)
                .bufferedReader()
                .readLines()
        }
            // comment lines are holding generation time, which is not stable
            .filterNot { it.isBlank() || it.trimStart().startsWith("#") }
            .sorted()

        logger.lifecycle(
            "AAR metadata of ${aar.name}:\n" + properties.joinToString("\n")
        )

        aarMetadataFile.get().asFile
            .writeText(properties.joinToString(separator = "\n", postfix = "\n"))
    }

    // endregion

    companion object {
        private const val AAR_METADATA_ENTRY = "META-INF/com/android/build/gradle/aar-metadata.properties"
    }
}
