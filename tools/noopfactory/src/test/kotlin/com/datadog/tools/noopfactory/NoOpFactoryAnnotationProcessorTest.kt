/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.noopfactory

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class NoOpFactoryAnnotationProcessorTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = ':',
        value = [
            "SimpleInterface.kt:NoOpSimpleInterface.kt",
            "GenericInterface.kt:NoOpGenericInterface.kt",
            "InheritedInterface.kt:NoOpInheritedInterface.kt",
            "AnyGenericInterface.kt:NoOpAnyGenericInterface.kt"
        ]
    )
    fun `implement a NoOp class`(srcFileName: String, genFileName: String) {
        val srcFile = File(javaClass.getResource("/src/$srcFileName").file)
        val genFile = File(javaClass.getResource("/gen/$genFileName").file)
        val kotlinSource = SourceFile.fromPath(srcFile)

        val result = KotlinCompilation().apply {
            sources = listOf(kotlinSource)
            annotationProcessors = listOf(NoOpFactoryAnnotationProcessor())
            inheritClassPath = true
        }.compile()

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.sourcesGeneratedByAnnotationProcessor).hasSize(1)
        val resultContent = result.sourcesGeneratedByAnnotationProcessor.first().readText()
        assertThat(resultContent).isEqualToIgnoringWhitespace(genFile.readText())
    }
}
