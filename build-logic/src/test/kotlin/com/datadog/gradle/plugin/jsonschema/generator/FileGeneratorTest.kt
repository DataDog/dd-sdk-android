/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.datadog.gradle.plugin.jsonschema.Animal
import com.datadog.gradle.plugin.jsonschema.Article
import com.datadog.gradle.plugin.jsonschema.Bike
import com.datadog.gradle.plugin.jsonschema.Book
import com.datadog.gradle.plugin.jsonschema.Comment
import com.datadog.gradle.plugin.jsonschema.Company
import com.datadog.gradle.plugin.jsonschema.Conflict
import com.datadog.gradle.plugin.jsonschema.CrossFileOneOfPrimitiveA
import com.datadog.gradle.plugin.jsonschema.CrossFileOneOfPrimitiveB
import com.datadog.gradle.plugin.jsonschema.Customer
import com.datadog.gradle.plugin.jsonschema.DateTime
import com.datadog.gradle.plugin.jsonschema.Delivery
import com.datadog.gradle.plugin.jsonschema.Demo
import com.datadog.gradle.plugin.jsonschema.ExplicitArguments
import com.datadog.gradle.plugin.jsonschema.Foo
import com.datadog.gradle.plugin.jsonschema.Household
import com.datadog.gradle.plugin.jsonschema.Jacket
import com.datadog.gradle.plugin.jsonschema.Location
import com.datadog.gradle.plugin.jsonschema.Message
import com.datadog.gradle.plugin.jsonschema.NoOpLogger
import com.datadog.gradle.plugin.jsonschema.OneOfPrimitiveRef
import com.datadog.gradle.plugin.jsonschema.Opus
import com.datadog.gradle.plugin.jsonschema.Order
import com.datadog.gradle.plugin.jsonschema.Paper
import com.datadog.gradle.plugin.jsonschema.PathArrayWithInteger
import com.datadog.gradle.plugin.jsonschema.PathArrayWithNumber
import com.datadog.gradle.plugin.jsonschema.Person
import com.datadog.gradle.plugin.jsonschema.Product
import com.datadog.gradle.plugin.jsonschema.Shipping
import com.datadog.gradle.plugin.jsonschema.Style
import com.datadog.gradle.plugin.jsonschema.TypeDefinition
import com.datadog.gradle.plugin.jsonschema.User
import com.datadog.gradle.plugin.jsonschema.UserMerged
import com.datadog.gradle.plugin.jsonschema.Version
import com.datadog.gradle.plugin.jsonschema.Video
import com.datadog.gradle.plugin.jsonschema.WeirdCombo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class FileGeneratorTest {

    @Test
    fun `M generate internal model W generate() { internal package }`(@TempDir tempDir: File) {
        // Given
        val testedGenerator = FileGenerator(tempDir, "com.example.internal.model", NoOpLogger(), internalModels = true)

        // When
        testedGenerator.generate(Person)

        // Then
        val generated = File(tempDir, "com/example/internal/model/Person.kt")
        assertThat(generated).content().contains("internal data class Person(")
    }

    @ParameterizedTest
    @MethodSource("data")
    fun `generates a Poko file`(
        inputType: TypeDefinition,
        outputFile: String,
        generateConstructorDefaults: Boolean,
        @TempDir tempDir: File
    ) {
        val clazz = FileGeneratorTest::class.java
        val outputPath = clazz.getResource("/output/$outputFile.kt").file
        val testedGenerator = FileGenerator(
            tempDir,
            "com.example.model",
            NoOpLogger(),
            generateConstructorDefaults = generateConstructorDefaults
        )

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
                    System.err.println("--- GENERATED $outputFile.kt \n")
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
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf(Article, "Article", true),
                arrayOf(Animal, "Animal", true),
                arrayOf(Bike, "Bike", true),
                arrayOf(Book, "Book", true),
                arrayOf(Comment, "Comment", true),
                arrayOf(Company, "Company", true),
                arrayOf(Conflict, "Conflict", true),
                arrayOf(Customer, "Customer", true),
                arrayOf(DateTime, "DateTime", true),
                arrayOf(Delivery, "Delivery", true),
                arrayOf(Demo, "Demo", true),
                arrayOf(Foo, "Foo", true),
                arrayOf(Household, "Household", true),
                arrayOf(Jacket, "Jacket", true),
                arrayOf(Person, "Person", true),
                arrayOf(Location, "Location", true),
                arrayOf(Message, "Message", true),
                arrayOf(Order, "Order", true),
                arrayOf(Opus, "Opus", true),
                arrayOf(Paper, "Paper", true),
                arrayOf(Product, "Product", true),
                arrayOf(Shipping, "Shipping", true),
                arrayOf(Style, "Style", true),
                arrayOf(Order, "Order", true),
                arrayOf(User, "User", true),
                arrayOf(UserMerged, "UserMerged", true),
                arrayOf(Version, "Version", true),
                arrayOf(Video, "Video", true),
                arrayOf(WeirdCombo, "WeirdCombo", true),
                arrayOf(PathArrayWithInteger, "PathArrayWithInteger", true),
                arrayOf(PathArrayWithNumber, "PathArrayWithNumber", true),
                arrayOf(OneOfPrimitiveRef, "OneOfPrimitiveRef", true),
                arrayOf(CrossFileOneOfPrimitiveA, "CrossFileOneOfPrimitiveA", true),
                arrayOf(CrossFileOneOfPrimitiveB, "CrossFileOneOfPrimitiveB", true),
                arrayOf(ExplicitArguments, "ExplicitArguments", false)
            )
        }
    }
}
