/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
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
        verifyZeroInteractions(mockDelegateWriter)
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
        verifyZeroInteractions(mockDelegateWriter)
        verify(mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            ScheduledWriter.ERROR_REJECTED,
            throwable = exception
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
        verifyZeroInteractions(mockDelegateWriter)
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
        verifyZeroInteractions(mockDelegateWriter)
        verify(mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            ScheduledWriter.ERROR_REJECTED,
            throwable = exception
        )
    }
}
