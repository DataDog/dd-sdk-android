/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import android.util.Log
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import kotlin.system.measureTimeMillis
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

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
    lateinit var mockLogHander: LogHandler

    @BeforeEach
    fun `set up`() {
        testedOperation = MoveDataMigrationOperation(
            fakeFromDirectory,
            fakeToDirectory,
            mockFileMover,
            Logger(mockLogHander)
        )
    }

    @Test
    fun `𝕄 warn 𝕎 run() {source dir is null}`() {
        // Given
        testedOperation = MoveDataMigrationOperation(
            null,
            fakeToDirectory,
            mockFileMover,
            Logger(mockLogHander)
        )

        // When
        testedOperation.run()

        // Then
        verifyZeroInteractions(mockFileMover)
        verify(mockLogHander).handleLog(
            Log.WARN,
            MoveDataMigrationOperation.WARN_NULL_SOURCE_DIR
        )
    }

    @Test
    fun `𝕄 warn 𝕎 run() {dest dir is null}`() {
        // Given
        testedOperation = MoveDataMigrationOperation(
            fakeFromDirectory,
            null,
            mockFileMover,
            Logger(mockLogHander)
        )
        whenever(mockFileMover.delete(fakeFromDirectory)) doReturn true

        // When
        testedOperation.run()

        // Then
        verifyZeroInteractions(mockFileMover)
        verify(mockLogHander).handleLog(
            Log.WARN,
            MoveDataMigrationOperation.WARN_NULL_DEST_DIR
        )
    }

    @Test
    fun `𝕄 move data 𝕎 run()`() {
        // Given
        whenever(mockFileMover.moveFiles(fakeFromDirectory, fakeToDirectory)) doReturn true

        // When
        testedOperation.run()

        // Then
        verify(mockFileMover).moveFiles(fakeFromDirectory, fakeToDirectory)
    }

    @Test
    fun `𝕄 retry 𝕎 run() {move fails once}`() {
        // Given
        whenever(mockFileMover.moveFiles(fakeFromDirectory, fakeToDirectory))
            .doReturn(false, true)

        // When
        testedOperation.run()

        // Then
        verify(mockFileMover, times(2)).moveFiles(fakeFromDirectory, fakeToDirectory)
    }

    @Test
    fun `𝕄 retry with 500ms delay 𝕎 run() {move fails once}`() {
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
    fun `𝕄 try 3 times maximum 𝕎 run() {move always fails}`() {
        // Given
        whenever(mockFileMover.moveFiles(fakeFromDirectory, fakeToDirectory))
            .doReturn(false)

        // When
        testedOperation.run()

        // Then
        verify(mockFileMover, times(3)).moveFiles(fakeFromDirectory, fakeToDirectory)
    }

    @Test
    fun `𝕄 retry with 500ms delay 𝕎 run() {move always fails}`() {
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
