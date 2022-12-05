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

    private val types = mutableListOf<Pair<String, File>>()

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
                val typeCanonicalName = match.groupValues[4]
                val packageName = typeCanonicalName.substringBeforeLast('.')
                val typeName = typeCanonicalName.substringAfterLast('.')
                val packageDir = File(srcDir, packageName)
                val typeDir = File(packageDir, convertToDokkaTypeName(typeName))
                types.add(typeName to typeDir)
            }
        }

        while (types.isNotEmpty()) {
            val (typeName, typeDir) = types.removeFirst()
            generateWiki(typeName, typeDir)
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

    private fun generateWiki(typeName: String, typeDir: File) {
        val outputFile = File(outputDir, "$typeName.md")
        if (!typeName.contains('.')) {
            pages.add(typeName)
        }

        if (typeDir.exists()) {
            logger.info("Combining doc from $typeName")
            combine(typeDir, outputFile, typeName)
        } else {
            logger.error("Unable to find $typeDir")
        }
    }

    private fun combine(typeDir: File, outputFile: File, typeName: String) {
        val indexFile = File(typeDir, "index.md")
        val keptLines = mutableListOf<String>()
        val sections = mutableMapOf<String, MutableList<File>>()
        val subtypesLinks = mutableListOf<String>()

        var currentSection = ""
        var currentFileList: MutableList<File>? = null
        indexFile.forEachLineIndexed { i: Int, line: String ->
            if (line.startsWith("## ")) {
                val sectionName = line.substring(3)
                if (sectionName != "Parameters") {
                    if (!sections.containsKey(sectionName)) {
                        sections[sectionName] = mutableListOf()
                    }
                    currentFileList = sections[sectionName]
                    currentSection = sectionName
                }
            }

            if (currentFileList == null) {
                if (i > 2 && line !in noise) keptLines.add(fixLinks(line, typeName))
            } else {
                val linkMatch = contentLinkRegex.matchEntire(line)
                val subtypeMatch = subTypeLinkRegex.matchEntire(line)
                if (linkMatch != null) {
                    currentFileList?.add(File(typeDir, linkMatch.groupValues[1]))
                } else if (subtypeMatch != null) {
                    if (currentSection == "Entries") {
                        // Enum entries are in a separate file that we want to include
                        currentFileList?.add(File(typeDir, subtypeMatch.groupValues[2]))
                    } else if (currentSection == "Types") {
                        val subtypeName = subtypeMatch.groupValues[1]
                        val link = subtypeMatch.groupValues[2]
                        val subFolder = File(typeDir, link.substringBeforeLast('/'))
                        types.add("$typeName.$subtypeName" to subFolder)
                        subtypesLinks.add("$typeName.$subtypeName")
                    } else {
                        logger.warn("Found unknown subtype for $typeName: $currentSection ($line)")
                    }
                }
            }
        }

        outputFile.printWriter(Charsets.UTF_8)
            .use { writer ->
                keptLines.forEach {
                    if (it.matches(codeLineRegex)) {
                        writer.print("> ")
                    }
                    writer.println(it)
                }

                if (subtypesLinks.isNotEmpty()) {
                    writer.println("## Types")
                    writer.println()
                    subtypesLinks.forEach {
                        writer.println("[$it]($it)")
                        writer.println()
                    }
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
            writer.println()
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
                convertHrefLink(href, typeName, title)
            }
        }
    }

    private fun convertHrefLink(
        href: String,
        typeName: String,
        title: String
    ): String {
        val typeHrefMatch = typeHrefRegex.matchEntire(href)
        return if (typeHrefMatch != null) {
            val dokkaParents = typeHrefMatch.groupValues[1].split('/').filter { it.isNotEmpty() }
            val dokkaFile = typeHrefMatch.groupValues[2]
            var levelsUp = 0
            var hasPackage = false
            val typeNameSections = mutableListOf<String>()
            val sections = typeName.split('.').toMutableList()
            dokkaParents.forEach {
                if (it == "..") {
                    levelsUp++
                    if (sections.isNotEmpty()) sections.removeLast()
                } else if (it.contains('.')) {
                    hasPackage = true
                    sections.clear()
                } else {
                    typeNameSections.add(it)
                    sections.add(it)
                }
            }
            val targetType = sections.joinToString(".") { convertFromDokkaTypeName(it) }
            val matchingType = findExistingFile(targetType)
            val anchorName = convertFromDokkaTypeName(dokkaFile)
            if (matchingType.isNotEmpty()) {
                if (targetType == typeName && anchorName != "index") {
                    "[$title]($matchingType#$anchorName)"
                } else {
                    "[$title]($matchingType)"
                }
            } else if (anchorName != "index") {
                "[$title]($typeName#$anchorName)"
            } else {
                logger.warn("Unmatched link href for $title in $typeName: $href")
                title
            }
        } else {
            logger.error("Unable to parse link href for $title:\n -$href\n -from $typeName")
            title
        }
    }

    private fun findExistingFile(type: String): String {
        if (type.isBlank()) {
            logger.warn("Trying to link to a blank type?")
            return ""
        }
        var matchingType = type
        val existingFiles = outputDir.list()
        if (existingFiles == null) {
            logger.error("No file yet in output directory $outputDir")
            return ""
        }

        while ("$matchingType.md" !in existingFiles) {
            if (matchingType.contains('.')) {
                matchingType = matchingType.substringBeforeLast('.')
            } else {
                logger.error(
                    "No matching file found for type '$type'. " +
                            "Try running the task again."
                )
                return ""
            }
        }

        return matchingType
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
            Regex("^(open )?(override )?(object|class|enum|data class|interface|annotation class|abstract class|fun|var|val) (.+)$")
        private val apiTypeSignatureRegex =
            Regex("^(DEPRECATED )?(open )?(object|class|enum|data class|interface|annotation class|abstract class) ([\\w\\d.]+)(<[\\w:\\s<>]+)?( .+)?")
        private val markdownLinkRegex = Regex("\\[([^]]+)]\\(([^)]+)\\)")
        private val typeHrefRegex = Regex("^([\\w\\-/.]+/)?([\\w\\-]+).md(#[\\w%-]+)?$")
        private val contentLinkRegex = Regex("\\| \\[[\\w_\\-&;]+]\\(([\\w_-]+.md)\\) \\| (.*) \\|")
        private val subTypeLinkRegex =
            Regex("\\| \\[([\\w_\\-&;]+)]\\(([\\w_-]+/index\\.md)\\) \\| .* \\|")
    }
}
