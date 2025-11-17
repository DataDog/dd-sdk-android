/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Task that extracts type constants from generated MobileSegment.kt file
 * and generates MobileSegmentConstants.kt with those values.
 */
@org.gradle.api.tasks.CacheableTask
abstract class GenerateMobileSegmentConstantsTask : DefaultTask() {

    init {
        group = "datadog"
        description = "Extract type constants from generated MobileSegment.kt and generate MobileSegmentConstants.kt"
    }

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedMobileSegmentFile: RegularFileProperty

    @get:OutputFile
    abstract val outputConstantsFile: RegularFileProperty

    @TaskAction
    fun performTask() {
        val inputFile = generatedMobileSegmentFile.get().asFile
        val outputFile = outputConstantsFile.get().asFile

        if (!inputFile.exists()) {
            logger.warn(
                "Generated MobileSegment.kt not found at ${inputFile.absolutePath}. Skipping constants generation."
            )
            return
        }

        val content = inputFile.readText()

        // Extract record type constants (Long values)
        // Pattern: public data class MetaRecord(...) : MobileRecord() { ... public val type: Long = 4L
        val recordTypePattern = Regex(
            """public\s+data\s+class\s+(\w+Record)\s*\([^)]*\)\s*:\s*MobileRecord\(\)\s*\{[^}]*?""" +
                """public\s+val\s+type:\s*Long\s*=\s*(\d+)L""",
            RegexOption.DOT_MATCHES_ALL
        )

        val recordTypes = mutableMapOf<String, Long>()
        recordTypePattern.findAll(content).forEach { match ->
            val className = match.groupValues[1]
            val typeValue = match.groupValues[2].toLong()
            recordTypes[className] = typeValue
        }

        // Extract wireframe type constants (String values)
        // Pattern: public data class ShapeWireframe(...) : Wireframe() { ... public val type: String = "shape"
        val wireframeTypePattern = Regex(
            """public\s+data\s+class\s+(\w+Wireframe)\s*\([^)]*\)\s*:\s*Wireframe\(\)\s*\{[^}]*?""" +
                """public\s+val\s+type:\s*String\s*=\s*"([^"]+)"""",
            RegexOption.DOT_MATCHES_ALL
        )

        val wireframeTypes = mutableMapOf<String, String>()
        wireframeTypePattern.findAll(content).forEach { match ->
            val className = match.groupValues[1]
            val typeValue = match.groupValues[2]
            wireframeTypes[className] = typeValue
        }

        // Generate the constants file
        val constantsContent = buildString {
            appendLine("/*")
            appendLine(
                " * Unless explicitly stated otherwise all files in this repository are " +
                    "licensed under the Apache License Version 2.0."
            )
            appendLine(" * This product includes software developed at Datadog (https://www.datadoghq.com/).")
            appendLine(" * Copyright 2016-Present Datadog, Inc.")
            appendLine(" *")
            appendLine(" * This file is auto-generated. Do not edit manually.")
            appendLine(" */")
            appendLine()
            appendLine("package com.datadog.android.sessionreplay.model")
            appendLine()

            // Record type constants
            recordTypes.forEach { (className, value) ->
                val constantName = when (className) {
                    "MetaRecord" -> "RECORD_TYPE_META"
                    "FocusRecord" -> "RECORD_TYPE_FOCUS"
                    "ViewEndRecord" -> "RECORD_TYPE_VIEW_END"
                    "VisualViewportRecord" -> "RECORD_TYPE_VISUAL_VIEWPORT"
                    "MobileFullSnapshotRecord" -> "RECORD_TYPE_FULL_SNAPSHOT"
                    "MobileIncrementalSnapshotRecord" -> "RECORD_TYPE_INCREMENTAL_SNAPSHOT"
                    else -> null
                }
                if (constantName != null) {
                    appendLine("/**")
                    appendLine(" * $className type.")
                    appendLine(
                        " * @see com.datadog.android.sessionreplay.model.MobileSegment.MobileRecord.$className"
                    )
                    appendLine(" */")
                    appendLine("const val $constantName: Long = ${value}L")
                    appendLine()
                }
            }

            // Wireframe type constants
            wireframeTypes.forEach { (className, value) ->
                val constantName = when (className) {
                    "ShapeWireframe" -> "WIREFRAME_TYPE_SHAPE"
                    "TextWireframe" -> "WIREFRAME_TYPE_TEXT"
                    "ImageWireframe" -> "WIREFRAME_TYPE_IMAGE"
                    "PlaceholderWireframe" -> "WIREFRAME_TYPE_PLACEHOLDER"
                    "WebviewWireframe" -> "WIREFRAME_TYPE_WEBVIEW"
                    else -> null
                }
                if (constantName != null) {
                    appendLine("/**")
                    appendLine(" * $className type.")
                    appendLine(
                        " * @see com.datadog.android.sessionreplay.model.MobileSegment.Wireframe.$className"
                    )
                    appendLine(" */")
                    appendLine("const val $constantName: String = \"$value\"")
                    appendLine()
                }
            }
        }

        outputFile.parentFile.mkdirs()
        outputFile.writeText(constantsContent)
        logger.info(
            "Generated MobileSegmentConstants.kt with ${recordTypes.size} record types " +
                "and ${wireframeTypes.size} wireframe types"
        )
    }
}
