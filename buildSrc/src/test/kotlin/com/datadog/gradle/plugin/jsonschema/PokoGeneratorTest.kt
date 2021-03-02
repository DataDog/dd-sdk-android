/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PokoGeneratorTest(
    internal val inputType: TypeDefinition,
    internal val outputFile: String
) {

    @get:Rule
    val tempFolderRule = TemporaryFolder()

    lateinit var tempDir: File

    @Test
    fun `generates a Poko file`() {
        tempDir = tempFolderRule.newFolder()
        val clazz = PokoGeneratorTest::class.java
        val outputPath = clazz.getResource("/output/$outputFile.kt").file
        val testedGenerator = PokoGenerator(tempDir, "com.example.model")

        testedGenerator.generate(inputType)

        val generatedFile = Files.find(
            Paths.get(tempDir.toURI()),
            Integer.MAX_VALUE,
            { _, attrs -> attrs.isRegularFile }
        ).findFirst().get().toFile()

        val generatedContent = generatedFile.readText(Charsets.UTF_8)
        val expectedContent = File(outputPath).readText(Charsets.UTF_8)

        if (generatedContent != expectedContent) {
            val genLines = generatedContent.lines()
            val expLines = expectedContent.lines()
            for (i in 0 until minOf(genLines.size, expLines.size)) {
                if (genLines[i] != expLines[i]) {
                    System.err.println(generatedContent)
                    throw AssertionError(
                        "File $outputFile generated from \n$inputType didn't match expectation:\n" +
                            "First error on line ${i + 1}:\n" +
                            "<<<<<<< EXPECTED\n" +
                            expLines[i] +
                            "\n=======\n" +
                            genLines[i] +
                            "\n>>>>>>> GENERATED\n"
                    )
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {1}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf(Article, "Article"),
                arrayOf(Book, "Book"),
                arrayOf(Comment, "Comment"),
                arrayOf(Conflict, "Conflict"),
                arrayOf(Customer, "Customer"),
                arrayOf(DateTime, "DateTime"),
                arrayOf(Delivery, "Delivery"),
                arrayOf(Demo, "Demo"),
                arrayOf(Foo, "Foo"),
                arrayOf(Person, "Person"),
                arrayOf(Location, "Location"),
                arrayOf(Message, "Message"),
                arrayOf(Order, "Order"),
                arrayOf(Opus, "Opus"),
                arrayOf(Product, "Product"),
                arrayOf(Shipping, "Shipping"),
                arrayOf(Style, "Style"),
                arrayOf(Order, "Order"),
                arrayOf(Version, "Version"),
                arrayOf(Video, "Video"),
                arrayOf(User, "User"),
                arrayOf(UserMerged, "UserMerged"),
                arrayOf(Bike, "Bike")
            )
        }
    }
}
