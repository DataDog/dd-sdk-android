/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PokoGeneratorTest(
    internal val inputSchema: String,
    internal val outputFile: String
) {

    @get:Rule
    val tempFolderRule = TemporaryFolder()

    lateinit var tempDir: File

    @Test
    fun `generates a Poko file`() {
        tempDir = tempFolderRule.newFolder()
        val clazz = PokoGeneratorTest::class.java
        val inputPath = clazz.getResource("/input/$inputSchema.json").file
        val outputPath = clazz.getResource("/output/$outputFile.kt").file
        val testedGenerator = PokoGenerator(
            File(inputPath),
            tempDir,
            "com.example.model"
        )

        testedGenerator.generate()

        val generatedFile = Files.find(
            Paths.get(tempDir.toURI()),
            Integer.MAX_VALUE,
            { _, attrs -> attrs.isRegularFile }
        ).findFirst().get().toFile()

        val generatedContent = generatedFile.readText(Charsets.UTF_8)
        val outputContent = File(outputPath).readText(Charsets.UTF_8)
        assertThat(generatedContent)
            .overridingErrorMessage(
                "File $outputFile generated from schema $inputSchema didn't match expectation:\n" +
                    "<<<<<<< EXPECTED\n" +
                    outputContent +
                    "=======\n" +
                    generatedContent +
                    "\n>>>>>>> GENERATED\n"
            )
            .isEqualTo(outputContent)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf("minimal", "Person"),
                arrayOf("required", "Product"),
                arrayOf("nested", "Book"),
                arrayOf("arrays", "Article"),
                arrayOf("sets", "Video"),
                arrayOf("definition", "Customer"),
                arrayOf("definition_with_id", "Customer"),
                arrayOf("enum", "Style"),
                arrayOf("constant", "Location"),
                arrayOf("nested_enum", "DateTime"),
                arrayOf("description", "Opus"),
                arrayOf("top_level_definition", "Foo"),
                arrayOf("types", "Demo"),
                arrayOf("all_of", "User"),
                arrayOf("external_description", "Delivery"),
                arrayOf("external_nested_description", "Shipping")
            )
        }
    }
}
