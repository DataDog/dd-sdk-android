/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ProfilingManager
import android.os.ProfilingResult
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.PerfettoProfiler
import com.datadog.android.profiling.internal.PerfettoResult
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService
import java.util.function.Consumer

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class PerfettoProfilerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockService: ProfilingManager

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Mock
    private lateinit var mockExecutorService: ExecutorService

    @Mock
    private lateinit var mockOnPerfettoResult: (PerfettoResult) -> Unit

    private val stubTimeProvider: StubTimeProvider = StubTimeProvider()

    private lateinit var testedProfiler: PerfettoProfiler

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.getSystemService(ProfilingManager::class.java)).doReturn(mockService)
        testedProfiler = PerfettoProfiler(
            internalLogger = mockInternalLogger,
            timeProvider = stubTimeProvider,
            profilingExecutor = mockExecutorService,
            onProfilingSuccess = mockOnPerfettoResult
        )
    }

    @Test
    fun `M request profiling stack sampling W start()`(
        @StringForgery fakePath: String
    ) {
        // When
        testedProfiler.start(mockContext)

        // Then
        val resultCallbackCaptor = argumentCaptor<Consumer<ProfilingResult>>()

        verify(mockService)
            .requestProfiling(
                eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
                any<Bundle>(),
                any<String>(),
                any<CancellationSignal>(),
                any(),
                resultCallbackCaptor.capture()
            )

        val messageCaptor = argumentCaptor<() -> String>()
        val expectedProps = mapOf(
            "profiling" to mapOf(
                "tag" to "ApplicationLaunch"
            )
        )
        verify(mockInternalLogger)
            .log(
                eq(InternalLogger.Level.INFO),
                eq(InternalLogger.Target.TELEMETRY),
                messageCaptor.capture(),
                isNull(),
                eq(true),
                eq(expectedProps)
            )
        val successResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { resultFilePath } doReturn fakePath
        }
        resultCallbackCaptor.firstValue.accept(successResult)
        val captor = argumentCaptor<PerfettoResult>()
        verify(mockOnPerfettoResult).invoke(captor.capture())

        val result = captor.firstValue
        assertEquals(stubTimeProvider.startTime, result.start)
        assertEquals(stubTimeProvider.endTime, result.end)
        assertEquals(fakePath, result.resultFilePath)

        assertEquals("Profiling started.", messageCaptor.firstValue.invoke())
    }

    @Test
    fun `M request profiling stack sampling only once W several call start()`() {
        // When
        testedProfiler.start(mockContext)

        // Then
        verify(mockService)
            .requestProfiling(
                eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
                any<Bundle>(),
                any<String>(),
                any<CancellationSignal>(),
                any(),
                any()
            )

        // When
        testedProfiler.start(mockContext)

        // Then
        verifyNoMoreInteractions(mockService)
    }

    @Test
    fun `M send telemetry W profiling finishes`(
        @StringForgery fakeErrorMessage: String,
        @LongForgery(min = 0L) fakeStartTime: Long,
        @LongForgery(min = 0L) fakeDuration: Long
    ) {
        // Given
        stubTimeProvider.startTime = fakeStartTime
        stubTimeProvider.endTime = fakeStartTime + fakeDuration

        // When
        testedProfiler.start(mockContext)

        // Then
        val callbackCaptor = argumentCaptor<Consumer<ProfilingResult>>()
        verify(mockService)
            .requestProfiling(
                eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
                any<Bundle>(),
                any<String>(),
                any<CancellationSignal>(),
                any(),
                callbackCaptor.capture()
            )

        val mockResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_FAILED_PROFILING_IN_PROGRESS
            on { errorMessage } doReturn fakeErrorMessage
        }

        callbackCaptor.firstValue.accept(mockResult)

        val messageCaptor = argumentCaptor<() -> String>()
        val expectedProps = mapOf(
            "profiling" to mapOf(
                "error_code" to ProfilingResult.ERROR_FAILED_PROFILING_IN_PROGRESS,
                "tag" to "ApplicationLaunch",
                "error_message" to fakeErrorMessage,
                "duration" to fakeDuration
            )
        )
        verify(mockInternalLogger)
            .log(
                eq(InternalLogger.Level.INFO),
                eq(InternalLogger.Target.TELEMETRY),
                messageCaptor.capture(),
                isNull(),
                eq(true),
                eq(expectedProps)
            )

        assertEquals("Profiling finished.", messageCaptor.firstValue.invoke())
    }

    private class StubTimeProvider : TimeProvider {
        var startTime: Long = 0L
        var endTime: Long = 0L
        private var queryIncrement: Int = 0

        override fun getDeviceTimestamp(): Long {
            return if (queryIncrement++ == 0) {
                startTime
            } else {
                endTime
            }
        }

        override fun getServerTimestamp(): Long = 0L

        override fun getServerOffsetNanos(): Long = 0L

        override fun getServerOffsetMillis(): Long = 0L
    }
}
