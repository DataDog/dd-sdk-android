/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ProfilingManager
import android.os.ProfilingResult
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.internal.profiling.ProfilingAnrDetectedEvent
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.internal.time.DefaultTimeProvider
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler.Companion.APP_LAUNCH_PROFILING_MAX_DURATION_MS
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler.Companion.PROFILING_SAMPLING_RATE_APP_LAUNCH
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler.Companion.PROFILING_SAMPLING_RATE_CONTINUOUS
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.perfetto.StackSampleDetector
import com.datadog.android.profiling.internal.telemetry.ProfilingTelemetry
import com.datadog.android.profiling.internal.time.MutableTimeProvider
import com.datadog.android.profiling.internal.trigger.ProfilingTriggerRegistrar
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class PerfettoProfilerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockService: ProfilingManager

    @Mock
    private lateinit var mockPackageManager: PackageManager

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Mock
    private lateinit var mockExecutorService: ScheduledExecutorService

    @Mock
    private lateinit var mockCancellationSignal: CancellationSignal

    @Mock
    private lateinit var mockProfilerCallback: ProfilerCallback

    @Mock
    private lateinit var mockRegistrar: ProfilingTriggerRegistrar

    @Mock
    private lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    private lateinit var mockStackSampleDetector: StackSampleDetector

    @TempDir
    lateinit var tempDir: File

    private val callbackCaptor = argumentCaptor<Consumer<ProfilingResult>>()

    @LongForgery(min = 1L)
    private var fakeProfilingPackageLongVersionCode: Long = 0L

    @StringForgery
    private lateinit var fakePath: String

    private val stubTimeProvider: StubTimeProvider = StubTimeProvider()

    private lateinit var testedProfiler: PerfettoProfiler

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.getSystemService(ProfilingManager::class.java)).doReturn(mockService)
        whenever(mockContext.packageManager) doReturn mockPackageManager
        stubProfilingModuleVersionCode(fakeProfilingPackageLongVersionCode)
        // ANR registration is only delegated on BAKLAVA+; opt-in for tests.
        whenever(mockBuildSdkVersionProvider.isAtLeastBaklava) doReturn true
        testedProfiler = PerfettoProfiler(
            timeProvider = stubTimeProvider,
            scheduledExecutorService = mockExecutorService,
            triggerRegistrar = mockRegistrar,
            buildSdkVersionProvider = mockBuildSdkVersionProvider,
            profilingTelemetry = ProfilingTelemetry().apply {
                profilingPackageVersionCode = fakeProfilingPackageLongVersionCode
            },
            stackSampleDetector = mockStackSampleDetector
        )
        testedProfiler.internalLogger = mockInternalLogger
        testedProfiler.registerProfilingCallback(mockContext, mockProfilerCallback)
    }

    @Test
    fun `M request profiling stack sampling W start()`() {
        // When
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())

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
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())

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
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())

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
            on { tag } doReturn ProfilingStartReason.APPLICATION_LAUNCH.value
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
                "app_start_info" to null,
                "no_stack_samples" to null
            ),
            "profiling_config" to mapOf(
                "buffer_size" to 5120,
                "sampling_frequency" to PROFILING_SAMPLING_RATE_APP_LAUNCH,
                "profiling_package_version_code" to fakeProfilingPackageLongVersionCode
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
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())

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
            on { tag } doReturn ProfilingStartReason.APPLICATION_LAUNCH.value
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
                "app_start_info" to null,
                "no_stack_samples" to null
            ),
            "profiling_config" to mapOf(
                "buffer_size" to 5120,
                "sampling_frequency" to PROFILING_SAMPLING_RATE_APP_LAUNCH,
                "profiling_package_version_code" to fakeProfilingPackageLongVersionCode
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
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())
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
            on { tag } doReturn ProfilingStartReason.APPLICATION_LAUNCH.value
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
                "app_start_info" to null,
                "no_stack_samples" to null
            ),
            "profiling_config" to mapOf(
                "buffer_size" to 5120,
                "sampling_frequency" to PROFILING_SAMPLING_RATE_APP_LAUNCH,
                "profiling_package_version_code" to fakeProfilingPackageLongVersionCode
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
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())

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
        assertThat(testedProfiler.isRunning()).isFalse
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
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())

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
        assertThat(testedProfiler.isRunning()).isFalse
    }

    @Test
    fun `M notify current status W registerProfilerStatusListener`(@BoolForgery fakeRunning: Boolean) {
        // Given
        if (fakeRunning) {
            testedProfiler.start(mockContext, ProfilingStartReason.CONTINUOUS, emptyMap())
        }
        val mockStatusListener = mock<ProfilingStatusListener>()

        // When
        testedProfiler.registerProfilerStatusListener(mockStatusListener)

        // Then
        verify(mockStatusListener).onProfilingStatusChange(fakeRunning)
    }

    @Test
    fun `M notify running status W start`() {
        // Given
        val mockStatusListener = mock<ProfilingStatusListener>()
        testedProfiler.registerProfilerStatusListener(mockStatusListener)

        // When
        testedProfiler.start(mockContext, ProfilingStartReason.CONTINUOUS, emptyMap())

        // Then
        inOrder(mockStatusListener) {
            verify(mockStatusListener).onProfilingStatusChange(false)
            verify(mockStatusListener).onProfilingStatusChange(true)
        }
    }

    @Test
    fun `M notify stopped status W profiling result received`(@BoolForgery fakeSuccess: Boolean) {
        // Given
        val mockStatusListener = mock<ProfilingStatusListener>()
        testedProfiler.start(mockContext, ProfilingStartReason.CONTINUOUS, emptyMap())
        testedProfiler.registerProfilerStatusListener(mockStatusListener)
        verify(mockService).requestProfiling(
            eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
            any<Bundle>(),
            eq(ProfilingStartReason.CONTINUOUS.value),
            any(),
            eq(mockExecutorService),
            callbackCaptor.capture()
        )
        val fakeResult = mock<ProfilingResult> {
            on { errorCode } doReturn if (fakeSuccess) ProfilingResult.ERROR_NONE else 1
            on { resultFilePath } doReturn fakePath
        }

        // When
        callbackCaptor.firstValue.accept(fakeResult)

        // Then
        inOrder(mockStatusListener) {
            verify(mockStatusListener).onProfilingStatusChange(true)
            verify(mockStatusListener).onProfilingStatusChange(false)
        }
        assertThat(testedProfiler.isRunning()).isFalse()
    }

    @Test
    fun `M keep running status W stop {awaiting result}`() {
        // Given
        testedProfiler.start(mockContext, ProfilingStartReason.CONTINUOUS, emptyMap())
        val mockStatusListener = mock<ProfilingStatusListener>()
        testedProfiler.registerProfilerStatusListener(mockStatusListener)

        // When
        testedProfiler.stop()

        // Then
        verify(mockStatusListener).onProfilingStatusChange(true)
        assertThat(testedProfiler.isRunning()).isTrue()
    }

    @Test
    fun `M stop notifying listener W unregisterProfilerStatusListener`() {
        // Given
        val mockStatusListener = mock<ProfilingStatusListener>()
        testedProfiler.registerProfilerStatusListener(mockStatusListener)

        // When
        testedProfiler.unregisterProfilerStatusListener(mockStatusListener)
        testedProfiler.start(mockContext, ProfilingStartReason.CONTINUOUS, emptyMap())

        // Then
        verify(mockStatusListener).onProfilingStatusChange(false)
    }

    @Test
    fun `M return false W isRunning { profiler not started }`() {
        // When
        val status = testedProfiler.isRunning()

        // Then
        assertThat(status).isFalse
    }

    @Test
    fun `M call onFailure W callback is called {error code}`(
        @IntForgery(min = 1) fakeErrorCode: Int
    ) {
        // Given
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())
        verify(mockService).requestProfiling(any(), any(), any(), any(), any(), callbackCaptor.capture())

        val mockResult = mock<ProfilingResult> {
            on { errorCode } doReturn fakeErrorCode
            on { tag } doReturn ProfilingStartReason.APPLICATION_LAUNCH.value
        }

        // When
        callbackCaptor.firstValue.accept(mockResult)

        // Then
        verify(mockProfilerCallback).onFailure(ProfilingStartReason.APPLICATION_LAUNCH)
        verifyNoMoreInteractions(mockProfilerCallback)
    }

    @Test
    fun `M call onFailure W callback is called {null file path}`() {
        // Given
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())
        verify(mockService).requestProfiling(any(), any(), any(), any(), any(), callbackCaptor.capture())

        val mockResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { resultFilePath } doReturn null
            on { tag } doReturn ProfilingStartReason.APPLICATION_LAUNCH.value
        }

        // When
        callbackCaptor.firstValue.accept(mockResult)

        // Then
        verify(mockProfilerCallback).onFailure(ProfilingStartReason.APPLICATION_LAUNCH)
        verifyNoMoreInteractions(mockProfilerCallback)
    }

    @Test
    fun `M not call onFailure W callback is called {success}`(
        @StringForgery fakePath: String
    ) {
        // Given
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())
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
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())

        // When
        val status = testedProfiler.isRunning()

        // Then
        assertThat(status).isTrue
    }

    @Test
    fun `M return false W isRunning { profiler stopped}`() {
        // Given
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())
        testedProfiler.stop()

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
        val status = testedProfiler.isRunning()
        assertThat(status).isFalse
        verify(mockProfilerCallback).onSuccess(any())
    }

    @Test
    fun `M call the registered callback W start()`() {
        // When
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.APPLICATION_LAUNCH,
            emptyMap()
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
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())
        testedProfiler.unregisterProfilingCallback(mockContext)

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
                emptyMap()
            )
            val stopSignalCaptor = argumentCaptor<CancellationSignal>()
            testedProfiler.stop()

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
                emptyMap()
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
            mapOf(PerfettoProfiler.TELEMETRY_KEY_APP_START_INFO to fakeAppStartInfo)
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
            on { tag } doReturn ProfilingStartReason.APPLICATION_LAUNCH.value
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
                "app_start_info" to fakeAppStartInfo,
                "no_stack_samples" to null
            ),
            "profiling_config" to mapOf(
                "buffer_size" to 5120,
                "sampling_frequency" to PROFILING_SAMPLING_RATE_APP_LAUNCH,
                "profiling_package_version_code" to fakeProfilingPackageLongVersionCode
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
    fun `M include start_reason in telemetry W profiling finishes { startReason }`(
        @Forgery fakeStartReason: ProfilingStartReason,
        @LongForgery(min = 0L) fakeStartTime: Long,
        @LongForgery(min = 0L) fakeDuration: Long
    ) {
        // Given
        stubTimeProvider.startTime = fakeStartTime
        stubTimeProvider.stopTime = fakeStartTime + fakeDuration

        // When
        testedProfiler.start(mockContext, fakeStartReason, emptyMap())

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
            on { tag } doReturn fakeStartReason.value
        }
        callbackCaptor.firstValue.accept(mockResult)

        val messageCaptor = argumentCaptor<() -> String>()
        val expectedSamplingRate = if (fakeStartReason == ProfilingStartReason.APPLICATION_LAUNCH) {
            PROFILING_SAMPLING_RATE_APP_LAUNCH
        } else {
            PROFILING_SAMPLING_RATE_CONTINUOUS
        }
        val expectedProps = mapOf(
            "metric_type" to "profiling session",
            "profiling_session" to mapOf(
                "error_code" to ProfilingResult.ERROR_NONE,
                "start_reason" to fakeStartReason.value,
                "duration" to fakeDuration,
                "callback_delay_ms" to 0L,
                "error_message" to null,
                "file_size" to 0L,
                "stopped_reason" to "timeout",
                "app_start_info" to null,
                "no_stack_samples" to null
            ),
            "profiling_config" to mapOf(
                "buffer_size" to 5120,
                "sampling_frequency" to expectedSamplingRate,
                "profiling_package_version_code" to fakeProfilingPackageLongVersionCode
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
            emptyMap()
        )
        testedProfiler.stop()

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
            on { tag } doReturn ProfilingStartReason.APPLICATION_LAUNCH.value
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
                "app_start_info" to null,
                "no_stack_samples" to null
            ),
            "profiling_config" to mapOf(
                "buffer_size" to 5120,
                "sampling_frequency" to PROFILING_SAMPLING_RATE_APP_LAUNCH,
                "profiling_package_version_code" to fakeProfilingPackageLongVersionCode
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
            emptyMap()
        )
        testedProfiler.stop()

        verify(mockService)
            .requestProfiling(
                eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
                any<Bundle>(),
                any<String>(),
                any<CancellationSignal>(),
                any(),
                callbackCaptor.capture()
            )

        val firstResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { tag } doReturn ProfilingStartReason.APPLICATION_LAUNCH.value
        }
        callbackCaptor.firstValue.accept(firstResult)

        // Given
        stubTimeProvider.reset()
        stubTimeProvider.startTime = fakeStartTime2
        stubTimeProvider.stopTime = fakeStartTime2 + fakeDuration2

        // When
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.CONTINUOUS,
            emptyMap()
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

        val secondResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { tag } doReturn ProfilingStartReason.CONTINUOUS.value
        }
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
                "app_start_info" to null,
                "no_stack_samples" to null
            ),
            "profiling_config" to mapOf(
                "buffer_size" to 5120,
                "sampling_frequency" to PROFILING_SAMPLING_RATE_CONTINUOUS,
                "profiling_package_version_code" to fakeProfilingPackageLongVersionCode
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
            emptyMap()
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
        testedProfiler.stopSignal = mockCancellationSignal

        // When
        testedProfiler.start(
            mockContext,
            startReason,
            emptyMap()
        )

        // Then
        verifyNoInteractions(mockExecutorService)
        verifyNoInteractions(mockCancellationSignal)
    }

    @Test
    fun `M cancel stop signal W app launch timer fires {extendLaunchSession is false}`() {
        // Given
        testedProfiler.setExtendLaunchSession(false)
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.APPLICATION_LAUNCH,
            emptyMap()
        )
        val timerRunnableCaptor = argumentCaptor<Runnable>()
        verify(mockExecutorService).schedule(timerRunnableCaptor.capture(), any(), any())
        testedProfiler.stopSignal = mockCancellationSignal
        whenever(mockCancellationSignal.isCanceled).doReturn(false)

        // When
        timerRunnableCaptor.firstValue.run()

        // Then
        verify(mockCancellationSignal).cancel()
    }

    @Test
    fun `M not cancel stop signal W app launch timer fires {extendLaunchSession is true}`() {
        // Given
        testedProfiler.setExtendLaunchSession(true)
        testedProfiler.start(
            mockContext,
            ProfilingStartReason.APPLICATION_LAUNCH,
            emptyMap()
        )
        val timerRunnableCaptor = argumentCaptor<Runnable>()
        verify(mockExecutorService).schedule(timerRunnableCaptor.capture(), any(), any())
        testedProfiler.stopSignal = mockCancellationSignal
        whenever(mockCancellationSignal.isCanceled).doReturn(false)

        // When
        timerRunnableCaptor.firstValue.run()

        // Then
        verify(mockCancellationSignal, never()).cancel()
    }

    // region ANR trigger registration

    @Test
    fun `M delegate to registrar W registerProfilingCallback`() {
        // Set-up performs 1 registerProfilingCallback call, which delegates to the registrar,
        // passing the profiler's listener.
        verify(mockRegistrar).register(mockContext, testedProfiler.triggerListener)
    }

    @Test
    fun `M call registrar unregister W callback removed`() {
        // When
        testedProfiler.unregisterProfilingCallback(mockContext)

        // Then
        verify(mockRegistrar).unregister(mockContext)
    }

    @Test
    fun `M dispatch to registered callback W triggerListener fires`(
        @Forgery fakeEvent: ProfilingAnrDetectedEvent,
        @Forgery fakeResult: PerfettoResult
    ) {
        // When
        testedProfiler.triggerListener.onAnrDetected(fakeEvent, fakeResult)

        // Then
        verify(mockProfilerCallback).onAnrDetected(fakeEvent, fakeResult)
    }

    @Test
    fun `M propagate logger to triggerRegistrar W internalLogger setter`() {
        // Given
        val anotherLogger = mock<InternalLogger>()

        // When
        testedProfiler.internalLogger = anotherLogger

        // Then
        verify(mockRegistrar).internalLogger = anotherLogger
    }

    @Test
    fun `M not delegate to registrar W registerProfilingCallback {SDK below BAKLAVA}`() {
        // Given
        // Drop interactions recorded by the BAKLAVA-stubbed set-up call.
        reset(mockRegistrar)
        whenever(mockBuildSdkVersionProvider.isAtLeastBaklava) doReturn false

        // When
        testedProfiler.registerProfilingCallback(mockContext, mockProfilerCallback)

        // Then
        verify(mockRegistrar, never()).register(any(), any())
    }

    @Test
    fun `M not delegate to registrar W registerProfilingCallback {ANR trigger disabled}`() {
        // Given
        // Drop interactions recorded by the set-up call (which used the default enabled state).
        reset(mockRegistrar)
        testedProfiler.setAnrTriggerEnabled(false)

        // When
        testedProfiler.registerProfilingCallback(mockContext, mockProfilerCallback)

        // Then
        verify(mockRegistrar, never()).register(any(), any())
    }

    @Test
    fun `M not delegate to registrar W unregisterProfilingCallback {ANR trigger disabled}`() {
        // Given
        testedProfiler.setAnrTriggerEnabled(false)

        // When
        testedProfiler.unregisterProfilingCallback(mockContext)

        // Then
        verify(mockRegistrar, never()).unregister(any())
    }

    @Test
    fun `M not delegate to registrar W unregisterProfilingCallback {SDK below BAKLAVA}`() {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastBaklava) doReturn false

        // When
        testedProfiler.unregisterProfilingCallback(mockContext)

        // Then
        verify(mockRegistrar, never()).unregister(any())
    }

    // endregion

    // region profiling module version resolution

    @Test
    fun `M not look up the profiling module version W start()`() {
        // When
        testedProfiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())

        // Then
        verify(mockPackageManager, never()).getPackageInfo(any<String>(), any<Int>())
    }

    @Test
    fun `M query the package manager once W resolveProfilingPackageVersionCode() {concurrent}`() {
        // Given a lookup that is already in flight when a second caller arrives: the profiler is
        // shared between the content provider, the scheduler executor and the SDK initialization.
        val lookupStarted = CountDownLatch(1)
        val releaseLookup = CountDownLatch(1)
        val mockPackageInfo = mock<PackageInfo> {
            on { longVersionCode } doReturn fakeProfilingPackageLongVersionCode
        }
        whenever(
            mockPackageManager.getPackageInfo(
                "com.google.android.profiling",
                PackageManager.MATCH_APEX
            )
        ) doAnswer {
            lookupStarted.countDown()
            releaseLookup.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            mockPackageInfo
        }
        val firstCaller = Thread { testedProfiler.resolveProfilingPackageVersionCode(mockContext) }
        val secondCaller = Thread { testedProfiler.resolveProfilingPackageVersionCode(mockContext) }

        // When
        firstCaller.start()
        check(lookupStarted.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "lookup never started" }
        secondCaller.start()
        check(awaitBlocked(secondCaller)) { "second caller never waited for the lookup in flight" }
        releaseLookup.countDown()
        firstCaller.join(JOIN_TIMEOUT_MS)
        secondCaller.join(JOIN_TIMEOUT_MS)

        // Then
        verify(mockPackageManager)
            .getPackageInfo("com.google.android.profiling", PackageManager.MATCH_APEX)
        assertThat(testedProfiler.profilingTelemetry.profilingPackageVersionCode)
            .isEqualTo(fakeProfilingPackageLongVersionCode)
    }

    // endregion

    @Test
    fun `M delete trace and notify failure W profiling finishes {no stack samples, affected module version}`(
        @LongForgery(min = 1L, max = FIXED_PROFILING_MODULE_VERSION_CODE) fakeVersionCode: Long,
        forge: Forge
    ) {
        // Given
        val fakeStartReason =
            forge.anElementFrom(ProfilingStartReason.APPLICATION_LAUNCH, ProfilingStartReason.CONTINUOUS)
        val traceFile = File(tempDir, fakePath).apply { writeText(fakePath) }
        stubProfilingModuleVersionCode(fakeVersionCode)
        testedProfiler.resolveProfilingPackageVersionCode(mockContext)
        whenever(mockStackSampleDetector.hasNoStackSamples(traceFile, mockInternalLogger)) doReturn true

        // When
        finishProfiling(fakeStartReason, traceFile.absolutePath)

        // Then
        verify(mockProfilerCallback).onFailure(fakeStartReason)
        verify(mockProfilerCallback, never()).onSuccess(any())
        assertThat(traceFile).doesNotExist()
        assertNoStackSamplesReported(true)
    }

    @Test
    fun `M notify success W profiling finishes {stack samples, affected module version}`(
        @LongForgery(min = 1L, max = FIXED_PROFILING_MODULE_VERSION_CODE) fakeVersionCode: Long,
        forge: Forge
    ) {
        // Given
        val fakeStartReason =
            forge.anElementFrom(listOf(ProfilingStartReason.APPLICATION_LAUNCH, ProfilingStartReason.CONTINUOUS))
        val traceFile = File(tempDir, fakePath).apply { writeText(fakePath) }
        stubProfilingModuleVersionCode(fakeVersionCode)
        testedProfiler.resolveProfilingPackageVersionCode(mockContext)
        whenever(mockStackSampleDetector.hasNoStackSamples(traceFile, mockInternalLogger)) doReturn false

        // When
        finishProfiling(fakeStartReason, traceFile.absolutePath)

        // Then
        verify(mockStackSampleDetector).hasNoStackSamples(traceFile, mockInternalLogger)
        assertSuccessNotified(fakeStartReason, traceFile)
        assertNoStackSamplesReported(false)
    }

    @Test
    fun `M notify success without checking the trace W profiling finishes {fixed module version}`(
        @LongForgery(min = FIXED_PROFILING_MODULE_VERSION_CODE) fakeVersionCode: Long,
        forge: Forge
    ) {
        // Given
        val fakeStartReason =
            forge.anElementFrom(listOf(ProfilingStartReason.APPLICATION_LAUNCH, ProfilingStartReason.CONTINUOUS))
        val traceFile = File(tempDir, fakePath).apply { writeText(fakePath) }
        stubProfilingModuleVersionCode(fakeVersionCode)
        testedProfiler.resolveProfilingPackageVersionCode(mockContext)

        // When
        finishProfiling(fakeStartReason, traceFile.absolutePath)

        // Then
        verifyNoInteractions(mockStackSampleDetector)
        assertSuccessNotified(fakeStartReason, traceFile)
        assertNoStackSamplesReported(null)
    }

    @Test
    fun `M notify success without checking the trace W profiling finishes {unknown module version}`(
        forge: Forge
    ) {
        // Given
        val fakeStartReason =
            forge.anElementFrom(ProfilingStartReason.APPLICATION_LAUNCH, ProfilingStartReason.CONTINUOUS)
        val traceFile = File(tempDir, fakePath).apply { writeText(fakePath) }
        stubProfilingModuleVersionCode(0L)
        testedProfiler.resolveProfilingPackageVersionCode(mockContext)

        // When
        finishProfiling(fakeStartReason, traceFile.absolutePath)

        // Then
        verifyNoInteractions(mockStackSampleDetector)
        assertSuccessNotified(fakeStartReason, traceFile)
        assertNoStackSamplesReported(null)
    }

    @Test
    fun `M notify success without checking the trace W profiling finishes {start reason not checked}`(
        @LongForgery(min = 1L, max = FIXED_PROFILING_MODULE_VERSION_CODE) fakeVersionCode: Long,
        forge: Forge
    ) {
        // Given
        val fakeStartReason = forge.aValueFrom(
            ProfilingStartReason::class.java,
            exclude = listOf(
                ProfilingStartReason.APPLICATION_LAUNCH,
                ProfilingStartReason.CONTINUOUS
            )
        )
        val traceFile = File(tempDir, fakePath).apply { writeText(fakePath) }
        stubProfilingModuleVersionCode(fakeVersionCode)
        testedProfiler.resolveProfilingPackageVersionCode(mockContext)

        // When
        finishProfiling(fakeStartReason, traceFile.absolutePath)

        // Then
        verifyNoInteractions(mockStackSampleDetector)
        assertSuccessNotified(fakeStartReason, traceFile)
        assertNoStackSamplesReported(null)
    }

    private fun finishProfiling(startReason: ProfilingStartReason, resultFilePath: String) {
        testedProfiler.start(mockContext, startReason, emptyMap())
        verify(mockService).requestProfiling(
            eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
            any<Bundle>(),
            any<String>(),
            any<CancellationSignal>(),
            any(),
            callbackCaptor.capture()
        )
        val successResult = mock<ProfilingResult> {
            on { errorCode } doReturn ProfilingResult.ERROR_NONE
            on { this.resultFilePath } doReturn resultFilePath
            on { tag } doReturn startReason.value
        }
        callbackCaptor.firstValue.accept(successResult)
    }

    private fun assertNoStackSamplesReported(expected: Boolean?) {
        val messageCaptor = argumentCaptor<() -> String>()
        val propertiesCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockInternalLogger).logMetric(
            messageCaptor.capture(),
            propertiesCaptor.capture(),
            eq(MethodCallSamplingRate.ALL.rate),
            isNull()
        )
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo(ProfilingTelemetry.TELEMETRY_MSG_PROFILING_SESSION)
        val sessionProperties = propertiesCaptor.firstValue[ProfilingTelemetry.KEY_PROFILING_SESSION] as Map<*, *>
        assertThat(sessionProperties[ProfilingTelemetry.KEY_NO_STACK_SAMPLES]).isEqualTo(expected)
    }

    private fun assertSuccessNotified(startReason: ProfilingStartReason, traceFile: File) {
        argumentCaptor<PerfettoResult> {
            verify(mockProfilerCallback).onSuccess(capture())
            assertThat(firstValue.startReason).isEqualTo(startReason)
            assertThat(firstValue.resultFilePath).isEqualTo(traceFile.absolutePath)
        }
        verify(mockProfilerCallback, never()).onFailure(any())
        assertThat(traceFile).exists()
    }

    /**
     * Waits, at most [LATCH_TIMEOUT_SECONDS], until [thread] is blocked on a monitor held by another thread.
     */
    private fun awaitBlocked(thread: Thread): Boolean {
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(LATCH_TIMEOUT_SECONDS)
        while (thread.state != Thread.State.BLOCKED && System.nanoTime() < deadlineNs) {
            Thread.yield()
        }
        return thread.state == Thread.State.BLOCKED
    }

    private fun stubProfilingModuleVersionCode(versionCode: Long) {
        val mockPackageInfo = mock<PackageInfo> {
            on { longVersionCode } doReturn versionCode
        }
        whenever(
            mockPackageManager.getPackageInfo(
                "com.google.android.profiling",
                PackageManager.MATCH_APEX
            )
        ) doReturn mockPackageInfo
    }

    companion object {
        // First version of the profiling system package which no longer records empty stack sampling traces.
        private const val FIXED_PROFILING_MODULE_VERSION_CODE = 371020780L

        private const val LATCH_TIMEOUT_SECONDS = 5L
        private const val JOIN_TIMEOUT_MS = 5000L
    }

    private class StubTimeProvider : MutableTimeProvider {
        // not really used
        override var delegate: TimeProvider = DefaultTimeProvider()

        var startTime: Long = 0L

        var stopTime: Long = 0L

        var resultCallbackTime: Long = 0L
        private var wallQueryIncrement: Int = 0
        private var elapsedQueryIncrement: Int = 0

        fun reset() {
            wallQueryIncrement = 0
            elapsedQueryIncrement = 0
        }

        override fun getDeviceTimestampMillis(): Long {
            val current = wallQueryIncrement
            wallQueryIncrement++
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

        override fun getDeviceElapsedRealtimeMillis(): Long {
            val current = elapsedQueryIncrement
            elapsedQueryIncrement++
            return when (current) {
                0 -> startTime
                1 -> stopTime
                else -> resultCallbackTime
            }
        }

        override fun getDeviceElapsedRealtimeNanos(): Long = 0L
        override fun getDeviceUptimeMillis(): Long = 0L
    }
}
