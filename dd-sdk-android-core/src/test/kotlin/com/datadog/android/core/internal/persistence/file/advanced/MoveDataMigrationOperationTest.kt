/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import kotlin.system.measureTimeMillis

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MoveDataMigrationOperationTest {
    lateinit var testedOperation: DataMigrationOperation

    @TempDir
    lateinit var fakeFromDirectory: File

    @TempDir
    lateinit var fakeToDirectory: File

    @Mock
    lateinit var mockFileMover: FileMover

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedOperation = MoveDataMigrationOperation(
            fakeFromDirectory,
            fakeToDirectory,
            mockFileMover,
            mockInternalLogger
        )
    }

    @Test
    fun `M warn W run() {source dir is null}`() {
        // Given
        testedOperation = MoveDataMigrationOperation(
            null,
            fakeToDirectory,
            mockFileMover,
            mockInternalLogger
        )

        // When
        testedOperation.run()

        // Then
        verifyNoInteractions(mockFileMover)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            MoveDataMigrationOperation.WARN_NULL_SOURCE_DIR
        )
    }

    @Test
    fun `M warn W run() {dest dir is null}`() {
        // Given
        testedOperation = MoveDataMigrationOperation(
            fakeFromDirectory,
            null,
            mockFileMover,
            mockInternalLogger
        )
        whenever(mockFileMover.delete(fakeFromDirectory)) doReturn true

        // When
        testedOperation.run()

        // Then
        verifyNoInteractions(mockFileMover)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            MoveDataMigrationOperation.WARN_NULL_DEST_DIR
        )
    }

    @Test
    fun `M move data W run()`() {
        // Given
        whenever(mockFileMover.moveFiles(fakeFromDirectory, fakeToDirectory)) doReturn true

        // When
        testedOperation.run()

        // Then
        verify(mockFileMover).moveFiles(fakeFromDirectory, fakeToDirectory)
    }

    @Test
    fun `M retry W run() {move fails once}`() {
        // Given
        whenever(mockFileMover.moveFiles(fakeFromDirectory, fakeToDirectory))
            .doReturn(false, true)

        // When
        testedOperation.run()

        // Then
        verify(mockFileMover, times(2)).moveFiles(fakeFromDirectory, fakeToDirectory)
    }

    @Test
    fun `M retry with 500ms delay W run() {move fails once}`() {
        // Given
        whenever(mockFileMover.moveFiles(fakeFromDirectory, fakeToDirectory))
            .doReturn(false, true)

        // When
        val duration = measureTimeMillis {
            testedOperation.run()
        }

        // Then
        verify(mockFileMover, times(2)).moveFiles(fakeFromDirectory, fakeToDirectory)
        assertThat(duration).isBetween(500L, 550L)
    }

    @Test
    fun `M try 3 times maximum W run() {move always fails}`() {
        // Given
        whenever(mockFileMover.moveFiles(fakeFromDirectory, fakeToDirectory))
            .doReturn(false)

        // When
        testedOperation.run()

        // Then
        verify(mockFileMover, times(3)).moveFiles(fakeFromDirectory, fakeToDirectory)
    }

    @Test
    fun `M retry with 500ms delay W run() {move always fails}`() {
        // Given
        whenever(mockFileMover.moveFiles(fakeFromDirectory, fakeToDirectory))
            .doReturn(false)

        // When
        val duration = measureTimeMillis {
            testedOperation.run()
        }

        // Then
        verify(mockFileMover, times(3)).moveFiles(fakeFromDirectory, fakeToDirectory)
        assertThat(duration).isBetween(1000L, 1100L)
    }
}
