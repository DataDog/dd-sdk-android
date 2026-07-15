/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.utils

import com.datadog.android.api.InternalLogger
import com.datadog.android.profiling.forge.Configurator
import fr.xgouchet.elmyr.Forge
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
import org.mockito.quality.Strictness
import java.io.File

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class FileSizeTest {

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M return 0 W fileSizeSafe() {null path}`() {
        // When
        val size = fileSizeSafe(null, mockInternalLogger)

        // Then
        assertThat(size).isZero()
    }

    @Test
    fun `M return 0 W fileSizeSafe() {empty path}`() {
        // When
        val size = fileSizeSafe("", mockInternalLogger)

        // Then
        assertThat(size).isZero()
    }

    @Test
    fun `M return 0 W fileSizeSafe() {missing file, logger set}`(@TempDir tempDir: File) {
        // Given
        val missing = File(tempDir, "does-not-exist.trace")

        // When
        val size = fileSizeSafe(missing.absolutePath, mockInternalLogger)

        // Then
        assertThat(size).isZero()
    }

    @Test
    fun `M return file size W fileSizeSafe() {valid file, logger set}`(
        @TempDir tempDir: File,
        forge: Forge
    ) {
        // Given
        val content = forge.aString(size = 256)
        val file = File(tempDir, "trace.bin").apply { writeText(content) }

        // When
        val size = fileSizeSafe(file.absolutePath, mockInternalLogger)

        // Then
        assertThat(size).isEqualTo(file.length())
    }

    @Test
    fun `M return file size W fileSizeSafe() {valid file, logger null}`(
        @TempDir tempDir: File,
        forge: Forge
    ) {
        // Given
        val content = forge.aString(size = 256)
        val file = File(tempDir, "trace.bin").apply { writeText(content) }

        // When
        val size = fileSizeSafe(file.absolutePath, null)

        // Then
        assertThat(size).isEqualTo(file.length())
    }

    @Test
    fun `M return 0 W fileSizeSafe() {missing file, logger null}`(@TempDir tempDir: File) {
        // Given
        val missing = File(tempDir, "does-not-exist.trace")

        // When
        val size = fileSizeSafe(missing.absolutePath, null)

        // Then
        assertThat(size).isZero()
    }
}
