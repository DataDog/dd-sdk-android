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
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler.Companion.APP_LAUNCH_PROFILING_MAX_DURATION_MS
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler.Companion.PROFILING_SAMPLING_RATE
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
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
    private lateinit var mockExecutorService: ScheduledExecutorService

    @Mock
    private lateinit var mockStopSignal: CancellationSignal

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
            scheduledExecutorService = mockExecutorService
        )
        testedProfiler.internalLogger = mockInternalLogger
        testedProfiler.registerProfilingCallback(fakeInstanceName, mockProfilerCallback)
        testedProfiler.registerProfilingCallback(otherInstanceName, mockOtherProfilerCallback)
    }

    @Test
    fun `M request profiling stack sampling W start()`() {
        // When
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))

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
        assertThat(result.resultFilePath).isEqualTo(fakePath)
    }

    @Test
    fun `M request profiling stack sampling only once W several call start(){ same instance }`() {
        // When
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))

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
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(otherInstanceName))

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
    fun `M send telemetry W profiling finishes {with timeout}`(
        @StringForgery fakeErrorMessage: String,
        @LongForgery(min = 0L) fakeStartTime: Long,
        @LongForgery(min = 0L) fakeDuration: Long
    ) {
        // Given
        stubTimeProvider.startTime = fakeStartTime
        stubTimeProvider.stopTime = fakeStartTime + fakeDuration

        // When
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))

        // Then
        val stopSignalCaptor = argumentCaptor<CancellationSignal>()
        verify(mockService)
            .requestProfiling(
                eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
                any<Bundle>(),
                any<String>(),
                stopSignalCaptor.capture(),
                any(),
                callbackCaptor.capture()
            )

        val mockResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { errorMessage } doReturn fakeErrorMessage
        }

        callbackCaptor.firstValue.accept(mockResult)

        val messageCaptor = argumentCaptor<() -> String>()
        val expectedProps = mapOf(
            "metric_type" to "profiling session",
            "profiling_session" to mapOf(
                "error_code" to ProfilingResult.ERROR_NONE,
                "error_message" to fakeErrorMessage,
                "start_reason" to ProfilingStartReason.APPLICATION_LAUNCH.value,
                "duration" to fakeDuration,
                "callback_delay_ms" to 0L,
                "file_size" to 0L,
                "stopped_reason" to "timeout",
                "app_start_info" to null
            ),
            "profiling_config" to mapOf(
                "buffer_size" to 5120,
                "sampling_frequency" to PROFILING_SAMPLING_RATE
            )
        )
        verify(mockInternalLogger)
            .logMetric(
                messageCaptor.capture(),
                eq(expectedProps),
                eq(MethodCallSamplingRate.ALL.rate),
                isNull()
            )

        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("[Mobile Metric] Profiling Session")
    }

    @Test
    fun `M send telemetry W profiling finishes {with error}`(
        @StringForgery fakeErrorMessage: String,
        @LongForgery(min = 0L) fakeStartTime: Long,
        @LongForgery(min = 0L) fakeDuration: Long,
        @IntForgery(min = 1, max = 8) fakeErrorCode: Int
    ) {
        // Given
        stubTimeProvider.startTime = fakeStartTime
        stubTimeProvider.stopTime = fakeStartTime + fakeDuration

        // When
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))

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
            on { errorCode } doReturn fakeErrorCode
            on { errorMessage } doReturn fakeErrorMessage
        }

        callbackCaptor.firstValue.accept(mockResult)

        val messageCaptor = argumentCaptor<() -> String>()
        val expectedProps = mapOf(
            "metric_type" to "profiling session",
            "profiling_session" to mapOf(
                "error_code" to fakeErrorCode,
                "start_reason" to ProfilingStartReason.APPLICATION_LAUNCH.value,
                "duration" to fakeDuration,
                "callback_delay_ms" to 0L,
                "error_message" to fakeErrorMessage,
                "file_size" to 0L,
                "stopped_reason" to "error",
                "app_start_info" to null
            ),
            "profiling_config" to mapOf(
                "buffer_size" to 5120,
                "sampling_frequency" to PROFILING_SAMPLING_RATE
            )
        )
        verify(mockInternalLogger)
            .logMetric(
                messageCaptor.capture(),
                eq(expectedProps),
                eq(MethodCallSamplingRate.ALL.rate),
                isNull()
            )

        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("[Mobile Metric] Profiling Session")
    }

    @Test
    fun `M send metric telemetry W internalLogger is assigned later`(
        @StringForgery fakeErrorMessage: String,
        @LongForgery(min = 0L) fakeStartTime: Long,
        @LongForgery(min = 0L) fakeDuration: Long
    ) {
        // Given
        stubTimeProvider.startTime = fakeStartTime
        stubTimeProvider.stopTime = fakeStartTime + fakeDuration
        testedProfiler.internalLogger = null

        // When
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))
        testedProfiler.internalLogger = mockInternalLogger

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

        // Then
        val messageCaptor = argumentCaptor<() -> String>()
        val expectedProps = mapOf(
            "metric_type" to "profiling session",
            "profiling_session" to mapOf(
                "error_code" to ProfilingResult.ERROR_FAILED_PROFILING_IN_PROGRESS,
                "start_reason" to ProfilingStartReason.APPLICATION_LAUNCH.value,
                "duration" to fakeDuration,
                "callback_delay_ms" to 0L,
                "error_message" to fakeErrorMessage,
                "file_size" to 0L,
                "stopped_reason" to "error",
                "app_start_info" to null
            ),
            "profiling_config" to mapOf(
                "buffer_size" to 5120,
                "sampling_frequency" to PROFILING_SAMPLING_RATE
            )
        )
        verify(mockInternalLogger)
            .logMetric(
                messageCaptor.capture(),
                eq(expectedProps),
                eq(MethodCallSamplingRate.ALL.rate),
                isNull()
            )

        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("[Mobile Metric] Profiling Session")
    }

    @Test
    fun `M isRunning is false W callback is called {no error}`(
        @LongForgery(min = 0L) fakeStartTime: Long,
        @LongForgery(min = 0L) fakeDuration: Long
    ) {
        // Given
        stubTimeProvider.startTime = fakeStartTime
        stubTimeProvider.stopTime = fakeStartTime + fakeDuration

        // When
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))

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
        }

        callbackCaptor.firstValue.accept(mockResult)

        // Then
        assertThat(testedProfiler.isRunning(fakeInstanceName)).isFalse
    }

    @Test
    fun `M isRunning is false W callback is called {with error}`(
        @IntForgery(min = 1) fakeErrorCode: Int,
        @LongForgery(min = 0L) fakeStartTime: Long,
        @LongForgery(min = 0L) fakeDuration: Long
    ) {
        // Given
        stubTimeProvider.startTime = fakeStartTime
        stubTimeProvider.stopTime = fakeStartTime + fakeDuration

        // When
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))

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
            on { errorCode } doReturn fakeErrorCode
        }

        callbackCaptor.firstValue.accept(mockResult)

        // Then
        assertThat(testedProfiler.isRunning(fakeInstanceName)).isFalse
    }

    @Test
    fun `M return false W isRunning { profiler not started }`() {
        // When
        val status = testedProfiler.isRunning(fakeInstanceName)

        // Then
        assertThat(status).isFalse
    }

    @Test
    fun `M call onFailure W callback is called {error code}`(
        @IntForgery(min = 1) fakeErrorCode: Int
    ) {
        // Given
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))
        verify(mockService).requestProfiling(any(), any(), any(), any(), any(), callbackCaptor.capture())

        val mockResult = mock<ProfilingResult> {
            on { errorCode } doReturn fakeErrorCode
            on { tag } doReturn ProfilingStartReason.APPLICATION_LAUNCH.value
        }

        // When
        callbackCaptor.firstValue.accept(mockResult)

        // Then
        verify(mockProfilerCallback).onFailure(ProfilingStartReason.APPLICATION_LAUNCH.value)
        verifyNoMoreInteractions(mockProfilerCallback)
    }

    @Test
    fun `M call onFailure W callback is called {null file path}`() {
        // Given
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))
        verify(mockService).requestProfiling(any(), any(), any(), any(), any(), callbackCaptor.capture())

        val mockResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { resultFilePath } doReturn null
            on { tag } doReturn ProfilingStartReason.APPLICATION_LAUNCH.value
        }

        // When
        callbackCaptor.firstValue.accept(mockResult)

        // Then
        verify(mockProfilerCallback).onFailure(ProfilingStartReason.APPLICATION_LAUNCH.value)
        verifyNoMoreInteractions(mockProfilerCallback)
    }

    @Test
    fun `M not call onFailure W callback is called {success}`(
        @StringForgery fakePath: String
    ) {
        // Given
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))
        verify(mockService).requestProfiling(any(), any(), any(), any(), any(), callbackCaptor.capture())

        val mockResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { resultFilePath } doReturn fakePath
            on { tag } doReturn ProfilingStartReason.APPLICATION_LAUNCH.value
        }

        // When
        callbackCaptor.firstValue.accept(mockResult)

        // Then
        verify(mockProfilerCallback).onSuccess(any())
        verifyNoMoreInteractions(mockProfilerCallback)
    }

    @Test
    fun `M return true W isRunning { profiler started }`() {
        // Given
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))

        // When
        val status = testedProfiler.isRunning(fakeInstanceName)

        // Then
        assertThat(status).isTrue
    }

    @Test
    fun `M return false W isRunning in other instance`() {
        // Given
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(otherInstanceName))

        // When
        val status = testedProfiler.isRunning(fakeInstanceName)

        // Then
        assertThat(status).isFalse
    }

    @Test
    fun `M return false W isRunning { profiler stopped by same instance}`() {
        // Given
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))
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

        // Then
        val status = testedProfiler.isRunning(fakeInstanceName)
        assertThat(status).isFalse
    }

    @Test
    fun `M return true W isRunning { profiler stopped by other instance}`() {
        // Given
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))
        testedProfiler.stop(otherInstanceName)

        // When
        val status = testedProfiler.isRunning(fakeInstanceName)

        // Then
        assertThat(status).isTrue
    }

    @Test
    fun `M call the all the instance's callback W several call start()`() {
        // When
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.APPLICATION_LAUNCH,
            emptyMap(),
            setOf(fakeInstanceName, otherInstanceName)
        )

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
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))

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
        stubTimeProvider.stopTime = fakeStartTime + fakeDuration

        // When
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap(), setOf(fakeInstanceName))
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

    @Test
    fun `M not have the same stop signal W start&stop multiple times`() {
        inOrder(mockService) {
            // When
            // First request
            testedProfiler.start(
                mockContext,
                ProfilingStartReason.APPLICATION_LAUNCH,
                emptyMap(),
                setOf(fakeInstanceName)
            )
            val stopSignalCaptor = argumentCaptor<CancellationSignal>()
            testedProfiler.stop(fakeInstanceName)

            verify(mockService).requestProfiling(
                eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
                any<Bundle>(),
                any<String>(),
                stopSignalCaptor.capture(),
                any(),
                callbackCaptor.capture()
            )
            // Then
            val successResult = mock<ProfilingResult> {
                on { errorCode } doReturn ProfilingResult.ERROR_NONE
                on { resultFilePath } doReturn fakePath
            }
            callbackCaptor.firstValue.accept(successResult)

            // Second request
            testedProfiler.start(
                mockContext,
                ProfilingStartReason.APPLICATION_LAUNCH,
                emptyMap(),
                setOf(fakeInstanceName)
            )
            verify(mockService).requestProfiling(
                eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
                any<Bundle>(),
                any<String>(),
                stopSignalCaptor.capture(),
                any(),
                callbackCaptor.capture()
            )
            assertThat(stopSignalCaptor.firstValue).isNotSameAs(stopSignalCaptor.secondValue)
        }
    }

    @Test
    fun `M include app_start_info in telemetry W profiling finishes { additionalAttributes contains app_start_info }`(
        @StringForgery fakeAppStartInfo: String,
        @LongForgery(min = 0L) fakeStartTime: Long,
        @LongForgery(min = 0L) fakeDuration: Long
    ) {
        // Given
        stubTimeProvider.startTime = fakeStartTime
        stubTimeProvider.stopTime = fakeStartTime + fakeDuration

        // When
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.APPLICATION_LAUNCH,
            mapOf(PerfettoProfiler.TELEMETRY_KEY_APP_START_INFO to fakeAppStartInfo),
            setOf(fakeInstanceName)
        )

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
        }
        callbackCaptor.firstValue.accept(mockResult)

        val messageCaptor = argumentCaptor<() -> String>()
        val expectedProps = mapOf(
            "metric_type" to "profiling session",
            "profiling_session" to mapOf(
                "error_code" to ProfilingResult.ERROR_NONE,
                "start_reason" to ProfilingStartReason.APPLICATION_LAUNCH.value,
                "duration" to fakeDuration,
                "callback_delay_ms" to 0L,
                "error_message" to null,
                "file_size" to 0L,
                "stopped_reason" to "timeout",
                "app_start_info" to fakeAppStartInfo
            ),
            "profiling_config" to mapOf(
                "buffer_size" to 5120,
                "sampling_frequency" to PROFILING_SAMPLING_RATE
            )
        )
        verify(mockInternalLogger)
            .logMetric(
                messageCaptor.capture(),
                eq(expectedProps),
                eq(MethodCallSamplingRate.ALL.rate),
                isNull()
            )
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("[Mobile Metric] Profiling Session")
    }

    @ParameterizedTest(name = "startReason: {0}")
    @EnumSource(ProfilingStartReason::class)
    internal fun `M include start_reason in telemetry W profiling finishes { startReason }`(
        startReason: ProfilingStartReason,
        @LongForgery(min = 0L) fakeStartTime: Long,
        @LongForgery(min = 0L) fakeDuration: Long
    ) {
        // Given
        stubTimeProvider.startTime = fakeStartTime
        stubTimeProvider.stopTime = fakeStartTime + fakeDuration

        // When
        testedProfiler.start(mockContext, startReason, emptyMap(), setOf(fakeInstanceName))

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
        }
        callbackCaptor.firstValue.accept(mockResult)

        val messageCaptor = argumentCaptor<() -> String>()
        val expectedProps = mapOf(
            "metric_type" to "profiling session",
            "profiling_session" to mapOf(
                "error_code" to ProfilingResult.ERROR_NONE,
                "start_reason" to startReason.value,
                "duration" to fakeDuration,
                "callback_delay_ms" to 0L,
                "error_message" to null,
                "file_size" to 0L,
                "stopped_reason" to "timeout",
                "app_start_info" to null
            ),
            "profiling_config" to mapOf(
                "buffer_size" to 5120,
                "sampling_frequency" to PROFILING_SAMPLING_RATE
            )
        )
        verify(mockInternalLogger)
            .logMetric(
                messageCaptor.capture(),
                eq(expectedProps),
                eq(MethodCallSamplingRate.ALL.rate),
                isNull()
            )
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("[Mobile Metric] Profiling Session")
    }

    @Test
    fun `M send telemetry with correct callback_delay_ms W profiling finishes {after manual stop}`(
        @StringForgery fakeErrorMessage: String,
        @LongForgery(min = 1L, max = 100_000L) fakeStartTime: Long,
        @LongForgery(min = 1L, max = 100_000L) fakeStopDelta: Long,
        @LongForgery(min = 1L, max = 100_000L) fakeCallbackDelta: Long
    ) {
        // Given
        stubTimeProvider.startTime = fakeStartTime
        stubTimeProvider.stopTime = fakeStartTime + fakeStopDelta
        stubTimeProvider.resultCallbackTime = fakeStartTime + fakeStopDelta + fakeCallbackDelta

        // When
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.APPLICATION_LAUNCH,
            emptyMap(),
            setOf(fakeInstanceName)
        )
        testedProfiler.stop(fakeInstanceName)

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
            on { errorMessage } doReturn fakeErrorMessage
        }
        callbackCaptor.firstValue.accept(mockResult)

        // Then
        val messageCaptor = argumentCaptor<() -> String>()
        val expectedProps = mapOf(
            "metric_type" to "profiling session",
            "profiling_session" to mapOf(
                "error_code" to ProfilingResult.ERROR_NONE,
                "error_message" to fakeErrorMessage,
                "start_reason" to ProfilingStartReason.APPLICATION_LAUNCH.value,
                "duration" to fakeStopDelta,
                "callback_delay_ms" to fakeCallbackDelta,
                "file_size" to 0L,
                "stopped_reason" to "manual",
                "app_start_info" to null
            ),
            "profiling_config" to mapOf(
                "buffer_size" to 5120,
                "sampling_frequency" to PROFILING_SAMPLING_RATE
            )
        )
        verify(mockInternalLogger)
            .logMetric(
                messageCaptor.capture(),
                eq(expectedProps),
                eq(MethodCallSamplingRate.ALL.rate),
                isNull()
            )
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("[Mobile Metric] Profiling Session")
    }

    @Test
    fun `M report correct duration & 0 delay W 2nd session timeout {after 1st  manually stopped}`(
        @LongForgery(min = 1L, max = 100_000L) fakeStartTime1: Long,
        @LongForgery(min = 1L, max = 100_000L) fakeStartTime2: Long,
        @LongForgery(min = 1L, max = 100_000L) fakeDuration2: Long
    ) {
        // Given
        stubTimeProvider.startTime = fakeStartTime1
        stubTimeProvider.stopTime = fakeStartTime1 + 5000L
        stubTimeProvider.resultCallbackTime = fakeStartTime1 + 6000L

        testedProfiler.start(
            mockContext,
            ProfilingStartReason.APPLICATION_LAUNCH,
            emptyMap(),
            setOf(fakeInstanceName)
        )
        testedProfiler.stop(fakeInstanceName)

        verify(mockService)
            .requestProfiling(
                eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
                any<Bundle>(),
                any<String>(),
                any<CancellationSignal>(),
                any(),
                callbackCaptor.capture()
            )

        val firstResult =
            mock<ProfilingResult> { on { errorCode } doReturn ProfilingResult.ERROR_NONE }
        callbackCaptor.firstValue.accept(firstResult)

        // Given
        stubTimeProvider.reset()
        stubTimeProvider.startTime = fakeStartTime2
        stubTimeProvider.stopTime = fakeStartTime2 + fakeDuration2

        // When
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.CONTINUOUS,
            emptyMap(),
            fakeDuration2.toInt()
        )

        val callbackCaptor2 = argumentCaptor<Consumer<ProfilingResult>>()
        verify(mockService, times(2))
            .requestProfiling(
                eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
                any<Bundle>(),
                any<String>(),
                any<CancellationSignal>(),
                any(),
                callbackCaptor2.capture()
            )

        val secondResult =
            mock<ProfilingResult> { on { errorCode } doReturn ProfilingResult.ERROR_NONE }
        callbackCaptor2.lastValue.accept(secondResult)

        // Then
        val expectedProps = mapOf(
            "metric_type" to "profiling session",
            "profiling_session" to mapOf(
                "error_code" to ProfilingResult.ERROR_NONE,
                "error_message" to null,
                "start_reason" to ProfilingStartReason.CONTINUOUS.value,
                "duration" to fakeDuration2,
                "callback_delay_ms" to 0L,
                "file_size" to 0L,
                "stopped_reason" to "timeout",
                "app_start_info" to null
            ),
            "profiling_config" to mapOf(
                "buffer_size" to 5120,
                "sampling_frequency" to PROFILING_SAMPLING_RATE
            )
        )
        verify(mockInternalLogger).logMetric(
            any(),
            eq(expectedProps),
            eq(MethodCallSamplingRate.ALL.rate),
            isNull()
        )
    }

    @Test
    fun `M schedule timer with 10s delay W start{startReason is APPLICATION_LAUNCH}()`() {
        // When
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.APPLICATION_LAUNCH,
            emptyMap(),
            setOf(fakeInstanceName)
        )

        // Then
        verify(mockExecutorService).schedule(
            any<Runnable>(),
            eq(APP_LAUNCH_PROFILING_MAX_DURATION_MS),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M not schedule timer W start {startReason is not APPLICATION_LAUNCH}()`(forge: Forge) {
        // Given
        val startReason = forge.aValueFrom(
            ProfilingStartReason::class.java,
            listOf(ProfilingStartReason.APPLICATION_LAUNCH)
        )
        testedProfiler.stopSignal = mockStopSignal

        // When
        testedProfiler.start(
            mockContext,
            startReason,
            emptyMap(),
            setOf(fakeInstanceName)
        )

        // Then
        verifyNoInteractions(mockExecutorService)
        verifyNoInteractions(mockStopSignal)
    }

    @Test
    fun `M cancel stop signal W app launch timer fires {extendLaunchSession is false}`() {
        // Given
        testedProfiler.setExtendLaunchSession(false)
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.APPLICATION_LAUNCH,
            emptyMap(),
            setOf(fakeInstanceName)
        )
        val timerRunnableCaptor = argumentCaptor<Runnable>()
        verify(mockExecutorService).schedule(timerRunnableCaptor.capture(), any(), any())
        testedProfiler.stopSignal = mockStopSignal
        whenever(mockStopSignal.isCanceled).doReturn(false)

        // When
        timerRunnableCaptor.firstValue.run()

        // Then
        verify(mockStopSignal).cancel()
    }

    @Test
    fun `M not cancel stop signal W app launch timer fires {extendLaunchSession is true}`() {
        // Given
        testedProfiler.setExtendLaunchSession(true)
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.APPLICATION_LAUNCH,
            emptyMap(),
            setOf(fakeInstanceName)
        )
        val timerRunnableCaptor = argumentCaptor<Runnable>()
        verify(mockExecutorService).schedule(timerRunnableCaptor.capture(), any(), any())
        testedProfiler.stopSignal = mockStopSignal
        whenever(mockStopSignal.isCanceled).doReturn(false)

        // When
        timerRunnableCaptor.firstValue.run()

        // Then
        verify(mockStopSignal, never()).cancel()
    }

    @Test
    fun `M request profiling W start(durationMs)`(
        @IntForgery(min = 1000, max = 60_000) fakeDurationMs: Int
    ) {
        // When
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.CONTINUOUS,
            emptyMap(),
            fakeDurationMs
        )

        // Then
        verify(mockService).requestProfiling(
            eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
            any<Bundle>(),
            any<String>(),
            any<CancellationSignal>(),
            any(),
            any()
        )
    }

    @Test
    fun `M not request profiling W start(durationMs) {app launch session running}`(
        @IntForgery(min = 1000, max = 60_000) fakeDurationMs: Int
    ) {
        // Given
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.APPLICATION_LAUNCH,
            emptyMap(),
            setOf(fakeInstanceName)
        )

        // When
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.CONTINUOUS,
            emptyMap(),
            fakeDurationMs
        )

        // Then
        verify(mockService, times(1)).requestProfiling(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `M not request profiling W start(durationMs) {continuous session already running}`(
        @IntForgery(min = 1000, max = 60_000) fakeDurationMs: Int
    ) {
        // Given
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.CONTINUOUS,
            emptyMap(),
            fakeDurationMs
        )

        // When
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.CONTINUOUS,
            emptyMap(),
            fakeDurationMs
        )

        // Then
        verify(mockService, times(1)).requestProfiling(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `M isRunning returns true for any instance W start(durationMs)`(
        @IntForgery(min = 1000, max = 60_000) fakeDurationMs: Int
    ) {
        // When
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.CONTINUOUS,
            emptyMap(),
            fakeDurationMs
        )

        // Then
        assertThat(testedProfiler.isRunning(fakeInstanceName)).isTrue
        assertThat(testedProfiler.isRunning(otherInstanceName)).isTrue
    }

    @Test
    fun `M isRunning returns false W continuous profiling result received`(
        @IntForgery(min = 1000, max = 60_000) fakeDurationMs: Int
    ) {
        // Given
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.CONTINUOUS,
            emptyMap(),
            fakeDurationMs
        )
        verify(mockService).requestProfiling(
            any(),
            any(),
            any(),
            any(),
            any(),
            callbackCaptor.capture()
        )
        val successResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { resultFilePath } doReturn fakePath
        }

        // When
        callbackCaptor.firstValue.accept(successResult)

        // Then
        assertThat(testedProfiler.isRunning(fakeInstanceName)).isFalse
        assertThat(testedProfiler.isRunning(otherInstanceName)).isFalse
    }

    @Test
    fun `M notify all registered callbacks W continuous profiling result received`(
        @IntForgery(min = 1000, max = 60_000) fakeDurationMs: Int
    ) {
        // Given
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.CONTINUOUS,
            emptyMap(),
            fakeDurationMs
        )
        verify(mockService).requestProfiling(
            any(),
            any(),
            any(),
            any(),
            any(),
            callbackCaptor.capture()
        )
        val successResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { resultFilePath } doReturn fakePath
        }

        // When
        callbackCaptor.firstValue.accept(successResult)

        // Then
        verify(mockProfilerCallback).onSuccess(any())
        verify(mockOtherProfilerCallback).onSuccess(any())
    }

    @Test
    fun `M not notify unregistered callback W continuous profiling result received`(
        @IntForgery(min = 1000, max = 60_000) fakeDurationMs: Int
    ) {
        // Given
        testedProfiler.unregisterProfilingCallback(otherInstanceName)
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.CONTINUOUS,
            emptyMap(),
            fakeDurationMs
        )
        verify(mockService).requestProfiling(
            any(),
            any(),
            any(),
            any(),
            any(),
            callbackCaptor.capture()
        )
        val successResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { resultFilePath } doReturn fakePath
        }

        // When
        callbackCaptor.firstValue.accept(successResult)

        // Then
        verify(mockProfilerCallback).onSuccess(any())
        verifyNoInteractions(mockOtherProfilerCallback)
    }

    @Test
    fun `M allow start(durationMs) from within onSuccess callback W app launch result received`(
        @IntForgery(min = 1000, max = 60_000) fakeDurationMs: Int
    ) {
        // Given
        // ProfilingFeature path where onAppLaunchProfilingComplete() → profiler.start(durationMs).
        whenever(mockProfilerCallback.onSuccess(any())).thenAnswer {
            testedProfiler.start(
                mockContext,
                ProfilingStartReason.CONTINUOUS,
                emptyMap(),
                fakeDurationMs
            )
        }
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.APPLICATION_LAUNCH,
            emptyMap(),
            setOf(fakeInstanceName)
        )
        verify(mockService).requestProfiling(
            any(),
            any(),
            any(),
            any(),
            any(),
            callbackCaptor.capture()
        )
        val successResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { resultFilePath } doReturn fakePath
        }

        // When
        callbackCaptor.firstValue.accept(successResult)

        // Then
        verify(mockService, times(2)).requestProfiling(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `M cancel stop signal W stop() called {during continuous profiling}`(
        @IntForgery(min = 1000, max = 60_000) fakeDurationMs: Int
    ) {
        // Given
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.CONTINUOUS,
            emptyMap(),
            fakeDurationMs
        )
        testedProfiler.stopSignal = mockStopSignal

        // When
        testedProfiler.stop(fakeInstanceName)

        // Then
        verify(mockStopSignal).cancel()
    }

    @Test
    fun `M call onFailure for all callbacks W continuous profiling ends {with error}`(
        @IntForgery(min = 1000, max = 60_000) fakeDurationMs: Int
    ) {
        // Given
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.CONTINUOUS,
            emptyMap(),
            fakeDurationMs
        )
        verify(mockService).requestProfiling(
            any(),
            any(),
            any(),
            any(),
            any(),
            callbackCaptor.capture()
        )
        val errorResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_FAILED_PROFILING_IN_PROGRESS
            on { tag } doReturn ProfilingStartReason.CONTINUOUS.value
        }

        // When
        callbackCaptor.firstValue.accept(errorResult)

        // Then
        verify(mockProfilerCallback).onFailure(ProfilingStartReason.CONTINUOUS.value)
        verify(mockOtherProfilerCallback).onFailure(ProfilingStartReason.CONTINUOUS.value)
    }

    @Test
    fun `M allow start(durationMs) from within onFailure callback W continuous result received {with error}`(
        @IntForgery(min = 1000, max = 60_000) fakeDurationMs: Int,
        @IntForgery(min = 1000, max = 60_000) fakeDurationMs2: Int
    ) {
        // Given
        whenever(mockProfilerCallback.onFailure(any())).thenAnswer {
            testedProfiler.start(
                mockContext,
                ProfilingStartReason.CONTINUOUS,
                emptyMap(),
                fakeDurationMs2
            )
        }
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.CONTINUOUS,
            emptyMap(),
            fakeDurationMs
        )
        verify(mockService).requestProfiling(
            any(),
            any(),
            any(),
            any(),
            any(),
            callbackCaptor.capture()
        )
        val errorResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_FAILED_PROFILING_IN_PROGRESS
            on { tag } doReturn ProfilingStartReason.CONTINUOUS.value
        }

        // When
        callbackCaptor.firstValue.accept(errorResult)

        // Then
        verify(mockService, times(2)).requestProfiling(any(), any(), any(), any(), any(), any())
    }

    // endregion

    private class StubTimeProvider : TimeProvider {
        var startTime: Long = 0L

        var stopTime: Long = 0L

        var resultCallbackTime: Long = 0L
        private var queryIncrement: Int = 0

        fun reset() {
            queryIncrement = 0
        }

        override fun getDeviceTimestampMillis(): Long {
            val current = queryIncrement
            queryIncrement++
            return when (current) {
                0 -> startTime
                1 -> stopTime
                else -> resultCallbackTime
            }
        }

        override fun getServerTimestampMillis(): Long = 0L

        override fun getDeviceElapsedTimeNanos(): Long = 0L

        override fun getServerOffsetNanos(): Long = 0L

        override fun getServerOffsetMillis(): Long = 0L

        override fun getDeviceElapsedRealtimeMillis(): Long = 0L
    }
}
