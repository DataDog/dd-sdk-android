/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.utils.getFileCreationTimeMs
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ResultFileTimeTest {

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M return creation time W getResultFileCreationTimeMs() {file exists}`(
        @TempDir tempDir: File
    ) {
        // Given
        val file = File(tempDir, "result.trace").apply { writeText("placeholder") }
        val expectedMs =
            Files.readAttributes(Paths.get(file.absolutePath), BasicFileAttributes::class.java)
                .creationTime().toMillis()

        // When
        val result = getFileCreationTimeMs(file.absolutePath, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(expectedMs)
    }

    @Test
    fun `M return null and log W getResultFileCreationTimeMs() {file missing}`() {
        // When
        val result = getFileCreationTimeMs("/path/that/does/not/exist.trace", mockInternalLogger)

        // Then
        assertThat(result).isNull()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            any<() -> String>(),
            any<Throwable>(),
            eq(false),
            isNull()
        )
    }
}
