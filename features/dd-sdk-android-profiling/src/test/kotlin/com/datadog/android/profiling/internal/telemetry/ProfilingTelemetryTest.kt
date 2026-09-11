/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.telemetry

import android.os.ProfilingResult
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.profiling.internal.ProfilingStartReason
import fr.xgouchet.elmyr.annotation.IntForgery
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ProfilingTelemetryTest {

    @Mock
    private lateinit var mockLogger: InternalLogger

    @LongForgery(min = 1L)
    private var fakeProfilingPackageVersionCode: Long = 0L

    private lateinit var testedTelemetry: ProfilingTelemetry

    @BeforeEach
    fun `set up`() {
        testedTelemetry = ProfilingTelemetry().apply {
            profilingPackageVersionCode = fakeProfilingPackageVersionCode
        }
    }

    @Test
    @Suppress
    fun `M dispatch SessionEnd through logMetric W report() {logger set}`(
        @StringForgery fakeErrorMessage: String,
        @LongForgery(min = 0L) fakeDuration: Long,
        @LongForgery fakeClientClockDriftMs: Long,
        @IntForgery(min = 1, max = 8) fakeErrorCode: Int
    ) {
        // Given
        testedTelemetry.internalLogger = mockLogger
        val event = ProfilingTelemetryEvent.SessionEnd(
            startReason = ProfilingStartReason.APPLICATION_LAUNCH.value,
            appStartInfo = null,
            errorCode = fakeErrorCode,
            errorMessage = fakeErrorMessage,
            fileSize = 0L,
            durationMs = fakeDuration,
            resultCallbackDelayMs = 0L,
            clientClockDriftMs = fakeClientClockDriftMs,
            stopReason = ProfilingTelemetry.STOPPED_REASON_ERROR,
            bufferSizeKb = 5120,
            samplingFrequencyHz = 201
        )

        // When
        testedTelemetry.report(event)

        // Then
        val messageCaptor = argumentCaptor<() -> String>()
        val expectedProps = mapOf(
            ProfilingTelemetry.KEY_METRIC_TYPE to ProfilingTelemetry.METRIC_TYPE_PROFILING_SESSION,
            ProfilingTelemetry.KEY_PROFILING_SESSION to mapOf(
                ProfilingTelemetry.KEY_ERROR_CODE to fakeErrorCode,
                ProfilingTelemetry.KEY_START_REASON to ProfilingStartReason.APPLICATION_LAUNCH.value,
                ProfilingTelemetry.KEY_DURATION to fakeDuration,
                ProfilingTelemetry.KEY_CALLBACK_DELAY to 0L,
                ProfilingTelemetry.KEY_CLIENT_CLOCK_DRIFT to fakeClientClockDriftMs,
                ProfilingTelemetry.KEY_ERROR_MESSAGE to fakeErrorMessage,
                ProfilingTelemetry.KEY_FILE_SIZE to 0L,
                ProfilingTelemetry.KEY_STOPPED_REASON to ProfilingTelemetry.STOPPED_REASON_ERROR,
                ProfilingTelemetry.KEY_APP_START_INFO to null
            ),
            // the value type is pinned to Any? so that the literals below stay Int: letting it be
            // inferred widens them to Long to match the version code, and the verify -> eq further down
            // then fails since 5120L != 5120
            ProfilingTelemetry.KEY_PROFILING_CONFIG to mapOf<String, Any?>(
                ProfilingTelemetry.KEY_BUFFER_SIZE to 5120,
                ProfilingTelemetry.KEY_SAMPLING_FREQUENCY to 201,
                ProfilingTelemetry.KEY_PROFILING_PACKAGE_VERSION_CODE to fakeProfilingPackageVersionCode
            )
        )
        verify(mockLogger).logMetric(
            messageCaptor.capture(),
            eq(expectedProps),
            eq(MethodCallSamplingRate.ALL.rate),
            isNull()
        )
        assertThat(messageCaptor.firstValue.invoke())
            .isEqualTo(ProfilingTelemetry.TELEMETRY_MSG_PROFILING_SESSION)
    }

    @Test
    fun `M dispatch AnrTriggerResult through logMetric W report() {logger set}`(
        @StringForgery fakeErrorMessage: String,
        @LongForgery fakeClientClockDriftMs: Long,
        @IntForgery(min = 0, max = 8) fakeErrorCode: Int
    ) {
        // Given
        testedTelemetry.internalLogger = mockLogger
        val event = ProfilingTelemetryEvent.AnrTriggerResult(
            errorCode = fakeErrorCode,
            errorMessage = fakeErrorMessage,
            fileSize = 0L,
            callbackDelayMs = null,
            clientClockDriftMs = fakeClientClockDriftMs,
            droppedAsStale = false
        )

        // When
        testedTelemetry.report(event)

        // Then
        val messageCaptor = argumentCaptor<() -> String>()
        val expectedProps = mapOf(
            ProfilingTelemetry.KEY_METRIC_TYPE to ProfilingTelemetry.METRIC_TYPE_PROFILING_TRIGGER,
            ProfilingTelemetry.KEY_PROFILING_SESSION to mapOf(
                ProfilingTelemetry.KEY_START_REASON to ProfilingTelemetry.ANR_PROFILING_TRIGGER_START_REASON,
                ProfilingTelemetry.KEY_ERROR_CODE to fakeErrorCode,
                ProfilingTelemetry.KEY_ERROR_MESSAGE to fakeErrorMessage,
                ProfilingTelemetry.KEY_FILE_SIZE to 0L,
                ProfilingTelemetry.KEY_CALLBACK_DELAY to null,
                ProfilingTelemetry.KEY_CLIENT_CLOCK_DRIFT to fakeClientClockDriftMs,
                ProfilingTelemetry.KEY_DROPPED_AS_STALE to false
            ),
            ProfilingTelemetry.KEY_PROFILING_CONFIG to mapOf(
                ProfilingTelemetry.KEY_PROFILING_PACKAGE_VERSION_CODE to fakeProfilingPackageVersionCode
            )
        )
        verify(mockLogger).logMetric(
            messageCaptor.capture(),
            eq(expectedProps),
            eq(MethodCallSamplingRate.ALL.rate),
            isNull()
        )
        assertThat(messageCaptor.firstValue.invoke())
            .isEqualTo(ProfilingTelemetry.TELEMETRY_MSG_PROFILING_SESSION)
    }

    @Test
    fun `M queue events W report() {logger null}`() {
        // Given
        val event = ProfilingTelemetryEvent.AnrTriggerResult(
            errorCode = ProfilingResult.ERROR_NONE,
            errorMessage = null,
            fileSize = 0L,
            callbackDelayMs = null,
            clientClockDriftMs = 0L,
            droppedAsStale = false
        )

        // When
        testedTelemetry.report(event)

        // Then
        verifyNoInteractions(mockLogger)
    }

    @Test
    fun `M flush queued events W internalLogger set after report()`() {
        // Given
        val firstEvent = ProfilingTelemetryEvent.AnrTriggerResult(
            errorCode = ProfilingResult.ERROR_NONE,
            errorMessage = null,
            fileSize = 0L,
            callbackDelayMs = null,
            clientClockDriftMs = 0L,
            droppedAsStale = false
        )
        val secondEvent = ProfilingTelemetryEvent.AnrTriggerResult(
            errorCode = ProfilingResult.ERROR_FAILED_PROFILING_IN_PROGRESS,
            errorMessage = "in_progress",
            fileSize = 0L,
            callbackDelayMs = null,
            clientClockDriftMs = 0L,
            droppedAsStale = false
        )
        testedTelemetry.report(firstEvent)
        testedTelemetry.report(secondEvent)

        // When
        testedTelemetry.internalLogger = mockLogger

        // Then
        verify(mockLogger, times(2)).logMetric(
            any(),
            any(),
            eq(MethodCallSamplingRate.ALL.rate),
            isNull()
        )
    }

    @Test
    fun `M not re-flush W internalLogger set twice`() {
        // Given
        val event = ProfilingTelemetryEvent.AnrTriggerResult(
            errorCode = ProfilingResult.ERROR_NONE,
            errorMessage = null,
            fileSize = 0L,
            callbackDelayMs = null,
            clientClockDriftMs = 0L,
            droppedAsStale = false
        )
        testedTelemetry.report(event)
        testedTelemetry.internalLogger = mockLogger
        // First flush already happened.

        // When
        testedTelemetry.internalLogger = mockLogger

        // Then
        verify(mockLogger, times(1)).logMetric(
            any(),
            any(),
            eq(MethodCallSamplingRate.ALL.rate),
            isNull()
        )
    }

    @Test
    fun `M not dispatch W internalLogger set to null`() {
        // Given
        testedTelemetry.internalLogger = null
        val event = ProfilingTelemetryEvent.AnrTriggerResult(
            errorCode = ProfilingResult.ERROR_NONE,
            errorMessage = null,
            fileSize = 0L,
            callbackDelayMs = null,
            clientClockDriftMs = 0L,
            droppedAsStale = false
        )

        // When
        testedTelemetry.report(event)

        // Then
        verifyNoInteractions(mockLogger)
    }
}
