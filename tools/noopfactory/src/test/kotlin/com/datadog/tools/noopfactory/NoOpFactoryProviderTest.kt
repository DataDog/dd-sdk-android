/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.noopfactory

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

internal class NoOpFactoryProviderTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = ':',
        value = [
            "SimpleInterface.kt:NoOpSimpleInterface.kt",
            "GenericInterface.kt:NoOpGenericInterface.kt",
            "InheritedInterface.kt:NoOpInheritedInterface.kt",
            "AnyGenericInterface.kt:NoOpAnyGenericInterface.kt",
            "EnumInterface.kt:NoOpEnumInterface.kt",
            "OverloadedInterface.kt:NoOpOverloadedInterface.kt"
        ]
    )
    fun `implement a NoOp class from interface`(srcFileName: String, genFileName: String) {
        val srcFile = File(javaClass.getResource("/src/$srcFileName")!!.file)
        val genFile = File(javaClass.getResource("/gen/$genFileName")!!.file)
        val kotlinSource = SourceFile.fromPath(srcFile)

        val result = KotlinCompilation().apply {
            inheritClassPath = true
            sources = listOf(kotlinSource)
            symbolProcessorProviders = listOf(NoOpFactoryProvider())
        }.compile()

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        result.assertGeneratedFileEquals(genFileName, genFile.readText())
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "NotAnInterface.kt",
            "NotAnnotatedInterface.kt"
        ]
    )
    fun `ignores invalid types`(srcFileName: String) {
        val srcFile = File(javaClass.getResource("/src/$srcFileName")!!.file)
        val kotlinSource = SourceFile.fromPath(srcFile)

        val result = KotlinCompilation().apply {
            inheritClassPath = true
            sources = listOf(kotlinSource)
            symbolProcessorProviders = listOf(NoOpFactoryProvider())
        }.compile()

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        result.assertNothingGenerated("NoOp$srcFileName")
    }

    // region Internal

    private fun KotlinCompilation.Result.assertNothingGenerated(
        generatedFileName: String
    ) {
        assertThat(sourceFor(generatedFileName))
            .isNull()
    }

    private fun KotlinCompilation.Result.assertGeneratedFileEquals(
        generatedFileName: String,
        expectedContent: String
    ) {
        assertThat(sourceFor(generatedFileName))
            .isEqualTo(expectedContent)
    }

    private fun KotlinCompilation.Result.sourceFor(fileName: String): String? {
        val kspGeneratedSources = getKspGeneratedSources()
        return kspGeneratedSources.find { it.name == fileName }
            ?.readText()
    }

    private fun KotlinCompilation.Result.getKspGeneratedSources(): List<File> {
        val workingDir = outputDirectory.parentFile
        val kspWorkingDir = workingDir.resolve("ksp")
        val kspGeneratedDir = kspWorkingDir.resolve("sources")
        val kotlinGeneratedDir = kspGeneratedDir.resolve("kotlin")
        val javaGeneratedDir = kspGeneratedDir.resolve("java")
        return kotlinGeneratedDir.walk().toList() +
            javaGeneratedDir.walk().toList()
    }

    // endregion
}
