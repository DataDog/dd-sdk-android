/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ProfilingManager
import android.os.ProfilingResult
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
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
    private lateinit var mockProfilerCallback: ProfilerCallback

    @Mock
    private lateinit var mockOtherProfilerCallback: ProfilerCallback

    @StringForgery
    private lateinit var fakeInstanceName: String

    private val callbackCaptor = argumentCaptor<Consumer<ProfilingResult>>()

    @StringForgery
    private lateinit var fakePath: String

    private val otherInstanceName: String
        get() = "$fakeInstanceName.suffix"

    private val stubTimeProvider: StubTimeProvider = StubTimeProvider()

    private lateinit var testedProfiler: PerfettoProfiler

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.getSystemService(ProfilingManager::class.java)).doReturn(mockService)
        testedProfiler = PerfettoProfiler(
            timeProvider = stubTimeProvider,
            profilingExecutor = mockExecutorService
        )
        testedProfiler.internalLogger = mockInternalLogger
        testedProfiler.registerProfilingCallback(fakeInstanceName, mockProfilerCallback)
        testedProfiler.registerProfilingCallback(otherInstanceName, mockOtherProfilerCallback)
    }

    @Test
    fun `M request profiling stack sampling W start()`() {
        // When
        testedProfiler.start(mockContext, setOf(fakeInstanceName))

        // Then
        verify(mockService)
            .requestProfiling(
                eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
                any<Bundle>(),
                any<String>(),
                any<CancellationSignal>(),
                any(),
                callbackCaptor.capture()
            )

        val successResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { resultFilePath } doReturn fakePath
        }
        callbackCaptor.firstValue.accept(successResult)
        val captor = argumentCaptor<PerfettoResult>()
        verify(mockProfilerCallback).onSuccess(captor.capture())

        val result = captor.firstValue
        assertThat(result.start).isEqualTo(stubTimeProvider.startTime)
        assertThat(result.start).isEqualTo(stubTimeProvider.endTime)
        assertThat(result.resultFilePath).isEqualTo(fakePath)
    }

    @Test
    fun `M log info message W start()`() {
        // When
        testedProfiler.start(mockContext, setOf(fakeInstanceName))

        // Then
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

        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("Profiling started.")
    }

    @Test
    fun `M request profiling stack sampling only once W several call start(){ same instance }`() {
        // When
        testedProfiler.start(mockContext, setOf(fakeInstanceName))
        testedProfiler.start(mockContext, setOf(fakeInstanceName))

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
    }

    @Test
    fun `M request profiling stack sampling only once W several call start(){ different instance }`() {
        // When
        testedProfiler.start(mockContext, setOf(fakeInstanceName))
        testedProfiler.start(mockContext, setOf(otherInstanceName))

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
        testedProfiler.start(mockContext, setOf(fakeInstanceName))

        // Then
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
                "duration" to fakeDuration,
                "size_bytes" to 0L
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

        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("Profiling finished.")
    }

    @Test
    fun `M return false W isRunning { profiler not started }`() {
        // When
        val status = testedProfiler.isRunning(fakeInstanceName)

        // Then
        assertThat(status).isFalse
    }

    @Test
    fun `M return true W isRunning { profiler started }`() {
        // Given
        testedProfiler.start(mockContext, setOf(fakeInstanceName))

        // When
        val status = testedProfiler.isRunning(fakeInstanceName)

        // Then
        assertThat(status).isTrue
    }

    @Test
    fun `M return false W isRunning in other instance`() {
        // Given
        testedProfiler.start(mockContext, setOf(otherInstanceName))

        // When
        val status = testedProfiler.isRunning(fakeInstanceName)

        // Then
        assertThat(status).isFalse
    }

    @Test
    fun `M return false W isRunning { profiler stopped by same instance}`() {
        // Given
        testedProfiler.start(mockContext, setOf(fakeInstanceName))
        testedProfiler.stop(fakeInstanceName)

        // When
        verify(mockService)
            .requestProfiling(
                eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
                any<Bundle>(),
                any<String>(),
                any<CancellationSignal>(),
                any(),
                callbackCaptor.capture()
            )

        val successResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { resultFilePath } doReturn fakePath
        }
        callbackCaptor.firstValue.accept(successResult)
        val status = testedProfiler.isRunning(fakeInstanceName)

        // Then
        assertThat(status).isFalse
    }

    @Test
    fun `M return true W isRunning { profiler stopped by other instance}`() {
        // Given
        testedProfiler.start(mockContext, setOf(fakeInstanceName))
        testedProfiler.stop(otherInstanceName)

        // When
        val status = testedProfiler.isRunning(fakeInstanceName)

        // Then
        assertThat(status).isTrue
    }

    @Test
    fun `M call the all the instance's callback W several call start()`() {
        // When
        testedProfiler.start(mockContext, setOf(fakeInstanceName, otherInstanceName))

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

        val successResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { resultFilePath } doReturn fakePath
        }
        callbackCaptor.firstValue.accept(successResult)
        verify(mockOtherProfilerCallback).onSuccess(any())
        verify(mockProfilerCallback).onSuccess(any())
    }

    @Test
    fun `M not call the instance's callback W call start without it()`() {
        // When
        testedProfiler.start(mockContext, setOf(fakeInstanceName))

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

        val successResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { resultFilePath } doReturn fakePath
        }
        callbackCaptor.firstValue.accept(successResult)
        verifyNoInteractions(mockOtherProfilerCallback)
        verify(mockProfilerCallback).onSuccess(any())
    }

    @Test
    fun `M not call resultCallback W callback is unregistered`(
        @LongForgery(min = 0L) fakeStartTime: Long,
        @LongForgery(min = 0L) fakeDuration: Long
    ) {
        // Given
        stubTimeProvider.startTime = fakeStartTime
        stubTimeProvider.endTime = fakeStartTime + fakeDuration

        // When
        testedProfiler.start(mockContext, setOf(fakeInstanceName))
        testedProfiler.unregisterProfilingCallback(fakeInstanceName)

        // Then
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
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { resultFilePath } doReturn fakePath
        }

        callbackCaptor.firstValue.accept(mockResult)
        verifyNoInteractions(mockProfilerCallback)
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
