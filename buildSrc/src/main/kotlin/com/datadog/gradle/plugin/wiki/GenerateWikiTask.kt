/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.wiki

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.PrintWriter
import java.nio.charset.Charset

open class GenerateWikiTask : DefaultTask() {

    @get:InputDirectory
    lateinit var srcDir: File

    @get: InputFile
    lateinit var apiSurface: File

    @get: Input
    lateinit var projectName: String

    @get: OutputDirectory
    lateinit var outputDir: File

    private val pages = mutableListOf<String>()

    init {
        group = "datadog"
        description = "Generate a Github Wiki from Dokka's output"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        outputDir.mkdirs()

        apiSurface.forEachLine {
            val match = apiTypeSignatureRegex.matchEntire(it)
            if (match != null) {
                val typeCanonicalName = match.groupValues[3]
                generateWikiPage(typeCanonicalName)
            }
        }

        val indexFile = File(outputDir, "$projectName.md")
        indexFile.printWriter(Charsets.UTF_8)
            .use { writer ->
                writer.println("### [$projectName]($projectName)\n")
                pages.forEach {
                    writer.println("- [$it]($it)")
                }
                writer.println("\n\n")
            }
    }

    private fun generateWikiPage(typeCanonicalName: String) {
        val packageName = typeCanonicalName.substringBeforeLast('.')
        val typeName = typeCanonicalName.substringAfterLast('.')
        val packageDir = File(srcDir, packageName)
        val typeDir = File(packageDir, convertToDokkaTypeName(typeName))
        val outputFile = File(outputDir, "$typeName.md")
        pages.add(typeName)

        if (typeDir.exists()) {
            logger.info("Combining doc from $typeCanonicalName")
            combine(typeDir, outputFile, typeName)
        } else {
            logger.error("Unable to find $typeDir")
        }
    }

    private fun combine(typeDir: File, outputFile: File, typeName: String) {
        val indexFile = File(typeDir, "index.md")
        val header = mutableListOf<String>()
        val sections = mutableMapOf<String, MutableList<File>>()

        var fileList: MutableList<File>? = null
        indexFile.forEachLineIndexed { i: Int, line: String ->
            if (line.startsWith("## ")) {
                val key = line.substring(3)
                if (key != "Parameters") {
                    // check(sections.containsKey(key)) { "Unknown section \"$key\" for type ${typeDir.name}" }
                    if (!sections.containsKey(key)) {
                        sections[key] = mutableListOf()
                    }
                    fileList = sections[key]
                }
            }

            if (fileList == null) {
                if (i > 2 && line !in noise) header.add(fixLinks(line, typeName))
            } else {
                val match = indexLinkRegex.matchEntire(line)
                if (match != null) {
                    fileList?.add(File(typeDir, match.groupValues[1]))
                }
            }
        }

        outputFile.printWriter(Charsets.UTF_8)
            .use { writer ->
                header.forEach {
                    if (it.matches(codeLineRegex)) {
                        writer.print("> ")
                    }
                    writer.println(it)
                }

                sections.entries.forEach { e ->
                    combineSectionFiles(writer, e.value, e.key, typeName)
                }
            }
    }

    private fun combineSectionFiles(
        writer: PrintWriter,
        files: List<File>?,
        title: String,
        typeName: String
    ) {
        if (files.isNullOrEmpty()) {
            logger.info("    No files for $typeName / $title")
            return
        }

        writer.println("## $title")
        files.forEach {
            appendFile(writer, it, typeName)
        }
    }

    private fun appendFile(writer: PrintWriter, file: File, typeName: String) {
        check(file.exists()) { "Missing file ${file.path}" }
        check(file.canRead()) { "Can't read file ${file.path}" }

        file.forEachLineIndexed { i, line ->
            if (i > 0) {
                if (line !in noise) {
                    if (line.startsWith('#')) {
                        writer.print("##")
                    } else if (line.matches(codeLineRegex) || line.startsWith("@")) {
                        writer.print("> ")
                    }
                    writer.println(fixLinks(line, typeName))
                }
            }
        }
    }

    private fun convertToDokkaTypeName(type: String): String {
        val builder = StringBuilder()
        type.forEach {
            when (it) {
                in 'A'..'Z' -> {
                    builder.append('-')
                    builder.append(it.toLowerCase())
                }
                else -> builder.append(it)
            }
        }
        return builder.toString()
    }

    private fun convertFromDokkaTypeName(type: String): String {
        val builder = StringBuilder()
        var upperCaseNext = false
        type.forEach {
            when (it) {
                '-' -> upperCaseNext = true
                in 'a'..'z' -> {
                    builder.append(if (upperCaseNext) it.toUpperCase() else it)
                    upperCaseNext = false
                }
                else -> builder.append(it)
            }
        }
        return builder.toString()
    }

    private fun fixLinks(line: String, typeName: String): String {
        return markdownLinkRegex.replace(line) {
            val title = it.groupValues[1]
            val href = it.groupValues[2]
            if (href.startsWith("http")) {
                it.value
            } else if (href == "#") {
                "[$title]($title)"
            } else if (href == "index.md") {
                "[$title]($typeName)"
            } else {
                val typeHrefMatch = typeHrefRegex.matchEntire(href)
                if (typeHrefMatch != null) {
                    val type = convertFromDokkaTypeName(typeHrefMatch.groupValues[1])
                    val anchor = convertFromDokkaTypeName(typeHrefMatch.groupValues[2])
                    if (anchor == "index") {
                        "[$title]($type)"
                    } else {
                        "[$title]($type#$anchor)"
                    }
                } else {
                    logger.warn("Unable to parse link href for $title: $href")
                    "[$title](???)"
                }
            }
        }
    }

    fun File.forEachLineIndexed(charset: Charset = Charsets.UTF_8, action: (Int, String) -> Unit) {
        var index = 0
        forEachLine(charset) {
            action(index, it)
            index++
        }
    }

    // endregion

    companion object {

        private val noise = listOf("[androidJvm]\\", "androidJvm")

        private val codeLineRegex =
            Regex("^(open )?(override )?(object|class|enum|data class|interface|annotation class|fun|var|val) (.+)$")
        private val apiTypeSignatureRegex =
            Regex("^(open )?(object|class|enum|data class|interface|annotation class) ([\\w\\d.]+)( .+)?")
        private val markdownLinkRegex = Regex("\\[([^]]+)]\\(([^)]+)\\)")
        private val typeHrefRegex = Regex("(?:[\\w.]+/)*(?:([\\w\\-]+)/)?([\\w\\-]+).md")
        private val indexLinkRegex = Regex("\\| \\[[\\w_\\-&;]+]\\(([\\w/_-]+.md)\\) \\| .* \\|")
    }
}
