/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.noopfactory

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException

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
            "OverloadedInterface.kt:NoOpOverloadedInterface.kt",
            "PublicImplementation.kt:NoOpPublicImplementation.kt",
            "ExperimentalInterface.kt:NoOpExperimentalInterface.kt",
            "Outer.kt:NoOpOuterInner.kt",
            "NestedReturnTypeInterface.kt:NoOpNestedReturnTypeInterface.kt",
            "Outer2.kt:NoOpOuter2Inner2.kt",
            "Outer2.kt:NoOpOuter2.kt",
            "PublicOuterInternalInner.kt:NoOpPublicOuterInternalInner.kt",
            "InternalOuterPublicInner.kt:NoOpInternalOuterPublicInner.kt",
            "InternalOuterInternalInner.kt:NoOpInternalOuterInternalInner.kt",
            "CustomNameInterface.kt:MyCustomNoOp.kt"
        ]
    )
    fun `implement a NoOp class from interface`(srcFileName: String, genFileName: String) {
        val srcFile = File(checkNotNull(javaClass.getResource("/src/$srcFileName")).file)
        val experimentalApiAnnotationFile = File(checkNotNull(javaClass.getResource("/src/ExperimentalApi.kt")).file)
        val genFile = File(checkNotNull(javaClass.getResource("/gen/$genFileName")).file)
        val kotlinSource = SourceFile.fromPath(srcFile)
        val experimentalApiAnnotationSource = SourceFile.fromPath(experimentalApiAnnotationFile)

        val result = KotlinCompilation().apply {
            inheritClassPath = true
            sources = listOf(kotlinSource, experimentalApiAnnotationSource)
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
        val srcFile = File(checkNotNull(javaClass.getResource("/src/$srcFileName")).file)
        val kotlinSource = SourceFile.fromPath(srcFile)

        val result = KotlinCompilation().apply {
            inheritClassPath = true
            sources = listOf(kotlinSource)
            symbolProcessorProviders = listOf(NoOpFactoryProvider())
        }.compile()

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        result.assertNothingGenerated("NoOp$srcFileName")
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = ':',
        value = [
            "PublicOuterPrivateInner.kt:NoOpPublicOuterPrivateInner.kt",
            "PublicOuterProtectedInner.kt:NoOpPublicOuterProtectedInner.kt",
            "InternalOuterPrivateInner.kt:NoOpInternalOuterPrivateInner.kt",
            "InternalOuterProtectedInner.kt:NoOpInternalOuterProtectedInner.kt",
            "PrivateOuterPublicInner.kt:NoOpPrivateOuterPublicInner.kt",
            "PrivateOuterInternalInner.kt:NoOpPrivateOuterInternalInner.kt",
            "PrivateOuterPrivateInner.kt:NoOpPrivateOuterPrivateInner.kt",
            "PrivateOuterProtectedInner.kt:NoOpPrivateOuterProtectedInner.kt"
        ]
    )
    fun `ignores interfaces with restricted visibility`(srcFileName: String, noOpFileName: String) {
        val srcFile = File(checkNotNull(javaClass.getResource("/src/$srcFileName")).file)
        val kotlinSource = SourceFile.fromPath(srcFile)

        val result = KotlinCompilation().apply {
            inheritClassPath = true
            sources = listOf(kotlinSource)
            symbolProcessorProviders = listOf(NoOpFactoryProvider())
        }.compile()

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        result.assertNothingGenerated(noOpFileName)
    }

    @Test
    fun `M report error W two interfaces generate same NoOp name`() {
        // Given
        val srcFile = File(checkNotNull(javaClass.getResource("/src/CollidingInterfaces.kt")).file)

        // When
        val result = KotlinCompilation().apply {
            inheritClassPath = true
            sources = listOf(SourceFile.fromPath(srcFile))
            symbolProcessorProviders = listOf(NoOpFactoryProvider())
        }.compile()

        // Then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).satisfiesAnyOf(
            { msg ->
                assertThat(msg).contains(
                    "NoOp name collision: both 'com.example.OuterInner' and 'com.example.Outer.Inner' " +
                        "would generate 'NoOpOuterInner'. " +
                        "Use @NoOpImplementation(customName = \"...\") on one of them to resolve the conflict."
                )
            },
            { msg ->
                assertThat(msg).contains(
                    "NoOp name collision: both 'com.example.Outer.Inner' and 'com.example.OuterInner' " +
                        "would generate 'NoOpOuterInner'. " +
                        "Use @NoOpImplementation(customName = \"...\") on one of them to resolve the conflict."
                )
            }
        )
    }


    @Test
    fun `M report error W customName conflicts with auto-generated NoOp name`() {
        // Given
        val srcFile = File(checkNotNull(javaClass.getResource("/src/CustomNameCollidingWithGenerated.kt")).file)

        // When
        val result = KotlinCompilation().apply {
            inheritClassPath = true
            sources = listOf(SourceFile.fromPath(srcFile))
            symbolProcessorProviders = listOf(NoOpFactoryProvider())
        }.compile()

        // Then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "customName \"NoOpBar\" for 'com.example.Foo' conflicts with the NoOp already generated " +
                "for 'com.example.Bar'. Choose a different customName."
        )
    }

    @Test
    fun `M report error W two interfaces use same customName`() {
        // Given
        val srcFile = File(checkNotNull(javaClass.getResource("/src/SameCustomName.kt")).file)

        // When
        val result = KotlinCompilation().apply {
            inheritClassPath = true
            sources = listOf(SourceFile.fromPath(srcFile))
            symbolProcessorProviders = listOf(NoOpFactoryProvider())
        }.compile()

        // Then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "customName \"SharedName\" for 'com.example.Beta' conflicts with the NoOp already generated " +
                "for 'com.example.Alpha'. Choose a different customName."
        )
    }

    @Test
    fun `M report error W customName is not a valid Kotlin identifier`() {
        // Given
        val srcFile = File(checkNotNull(javaClass.getResource("/src/InvalidCustomNameInterface.kt")).file)

        // When
        val result = KotlinCompilation().apply {
            inheritClassPath = true
            sources = listOf(SourceFile.fromPath(srcFile))
            symbolProcessorProviders = listOf(NoOpFactoryProvider())
        }.compile()

        // Then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "customName \"My.Invalid.Name\" for 'com.example.InvalidCustomNameInterface' " +
                "is not a valid Kotlin class name. Use only letters, digits, and underscores, " +
                "starting with a letter or underscore."
        )
    }

    @Test
    fun `M report error W generated NoOp name conflicts with existing file`() {
        // Given
        val srcFile = File(checkNotNull(javaClass.getResource("/src/PreExistingFileInterface.kt")).file)

        // When
        val result = KotlinCompilation().apply {
            inheritClassPath = true
            sources = listOf(SourceFile.fromPath(srcFile))
            symbolProcessorProviders = listOf(ThrowingCodeGeneratorProvider())
        }.compile()

        // Then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "Cannot generate 'com.example.NoOpPreExistingFileInterface': file already exists. " +
                "If you created this file manually, rename it or use " +
                "@NoOpImplementation(customName = \"...\") on 'com.example.PreExistingFileInterface'."
        )
    }

    @Test
    fun `M report error W customName conflicts with existing file`() {
        // Given
        val srcFile = File(checkNotNull(javaClass.getResource("/src/PreExistingFileCustomNameInterface.kt")).file)

        // When
        val result = KotlinCompilation().apply {
            inheritClassPath = true
            sources = listOf(SourceFile.fromPath(srcFile))
            symbolProcessorProviders = listOf(ThrowingCodeGeneratorProvider())
        }.compile()

        // Then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "customName \"MyPreexistingNoOp\" for 'com.example.PreExistingFileCustomNameInterface' " +
                "conflicts with an existing file 'com.example.MyPreexistingNoOp'. " +
                "Choose a different customName or rename the existing file."
        )
    }

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

    // KSP 1.8.0 only tracks files created within the same processing round (via an in-memory set)
    // and does not check whether a file already exists on disk from a previous build. This means
    // FileAlreadyExistsException cannot be triggered through a real two-pass compilation with
    // kotlin-compile-testing. ThrowingCodeGeneratorProvider simulates the exception so the
    // error-handling path in NoOpFactorySymbolProcessor can be exercised and verified.
    private class ThrowingCodeGeneratorProvider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            val throwingCodeGenerator = object : CodeGenerator by environment.codeGenerator {
                override fun createNewFile(
                    dependencies: Dependencies,
                    packageName: String,
                    fileName: String,
                    extensionName: String
                ): OutputStream {
                    throw FileAlreadyExistsException("$packageName/$fileName.$extensionName")
                }
            }
            return NoOpFactorySymbolProcessor(throwingCodeGenerator, environment.logger)
        }
    }
}
