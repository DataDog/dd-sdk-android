/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ScheduledWriterTest {

    lateinit var testedWriter: DataWriter<String>

    @Mock
    lateinit var mockDelegateWriter: DataWriter<String>

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedWriter = ScheduledWriter<String>(
            mockDelegateWriter,
            mockExecutorService,
            mockInternalLogger
        )
    }

    @Test
    fun `𝕄 schedule write 𝕎 write(T)`(
        @StringForgery data: String
    ) {
        // Given

        // When
        testedWriter.write(data)

        // Then
        verifyNoInteractions(mockDelegateWriter)
        argumentCaptor<Runnable> {
            verify(mockExecutorService).submit(capture())
            firstValue.run()
            verify(mockDelegateWriter).write(data)
        }

        verifyNoMoreInteractions(mockDelegateWriter, mockExecutorService)
    }

    @Test
    fun `𝕄 drop data and warn 𝕎 write(T) {submit rejected}`(
        @StringForgery data: String,
        @StringForgery errorMessage: String
    ) {
        // Given
        val exception = RejectedExecutionException(errorMessage)
        whenever(mockExecutorService.submit(any())) doThrow exception

        // When
        testedWriter.write(data)

        // Then
        verifyNoInteractions(mockDelegateWriter)
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY)),
            any(),
            same(exception)
        )
    }

    @Test
    fun `𝕄 schedule write 𝕎 write(List)`(
        @StringForgery data: List<String>
    ) {
        // Given

        // When
        testedWriter.write(data)

        // Then
        verifyNoInteractions(mockDelegateWriter)
        argumentCaptor<Runnable> {
            verify(mockExecutorService).submit(capture())
            firstValue.run()
            verify(mockDelegateWriter).write(data)
        }

        verifyNoMoreInteractions(mockDelegateWriter, mockExecutorService)
    }

    @Test
    fun `𝕄 drop data and warn 𝕎 write(List) {submit rejected}`(
        @StringForgery data: List<String>,
        @StringForgery errorMessage: String
    ) {
        // Given
        val exception = RejectedExecutionException(errorMessage)
        whenever(mockExecutorService.submit(any())) doThrow exception

        // When
        testedWriter.write(data)

        // Then
        verifyNoInteractions(mockDelegateWriter)
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY)),
            any(),
            same(exception)
        )
    }
}
