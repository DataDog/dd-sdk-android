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
internal class WipeDataMigrationOperationTest {

    lateinit var testedOperation: DataMigrationOperation

    @TempDir
    lateinit var fakeTargetDirectory: File

    @Mock
    lateinit var mockFileMover: FileMover

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedOperation = WipeDataMigrationOperation(
            fakeTargetDirectory,
            mockFileMover,
            mockInternalLogger
        )
    }

    @Test
    fun `M warn W run() {dir is null}`() {
        // Given
        testedOperation = WipeDataMigrationOperation(
            null,
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
            WipeDataMigrationOperation.WARN_NULL_DIR
        )
    }

    @Test
    fun `M delete dir recursively W run()`() {
        // Given
        whenever(mockFileMover.delete(fakeTargetDirectory)) doReturn true

        // When
        testedOperation.run()

        // Then
        verify(mockFileMover).delete(fakeTargetDirectory)
    }

    @Test
    fun `M retry W run() {delete fails once}`() {
        // Given
        whenever(mockFileMover.delete(fakeTargetDirectory)).doReturn(false, true)

        // When
        testedOperation.run()

        // Then
        verify(mockFileMover, times(2)).delete(fakeTargetDirectory)
    }

    @Test
    fun `M try 3 times maximum W run() {move always fails}`() {
        // Given
        whenever(mockFileMover.delete(fakeTargetDirectory))
            .doReturn(false)

        // When
        testedOperation.run()

        // Then
        verify(mockFileMover, times(3)).delete(fakeTargetDirectory)
    }

    @Test
    fun `M retry with 500ms delay W run() {move always fails}`() {
        // Given
        whenever(mockFileMover.delete(fakeTargetDirectory))
            .doReturn(false)

        // When
        val duration = measureTimeMillis {
            testedOperation.run()
        }

        // Then
        verify(mockFileMover, times(3)).delete(fakeTargetDirectory)
        assertThat(duration).isBetween(1000L, 1100L)
    }
}
