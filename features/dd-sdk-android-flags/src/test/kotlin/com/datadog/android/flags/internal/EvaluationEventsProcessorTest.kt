/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.model.BatchedFlagEvaluations
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import com.datadog.android.internal.time.TimeProvider
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class EvaluationEventsProcessorTest {

    @Mock
    lateinit var mockWriter: EvaluationEventWriter

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockScheduledExecutor: ScheduledExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockScheduledFuture: ScheduledFuture<*>

    @LongForgery(min = 1000000L)
    var fakeTimestamp = 0L

    @StringForgery
    lateinit var fakeFlagName: String

    @StringForgery
    lateinit var fakeTargetingKey: String

    @StringForgery
    lateinit var fakeVariantKey: String

    @StringForgery
    lateinit var fakeAllocationKey: String

    @StringForgery
    lateinit var fakeValue: String

    private lateinit var testedProcessor: EvaluationEventsProcessor
    private lateinit var fakeContext: EvaluationContext
    private lateinit var fakeData: PrecomputedFlag

    @BeforeEach
    fun `set up`() {
        testedProcessor = EvaluationEventsProcessor(
            writer = mockWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            flushIntervalMs = 10_000L,
            maxAggregations = 1000
        )

        fakeContext = EvaluationContext(targetingKey = fakeTargetingKey)
        fakeData = PrecomputedFlag(
            variationType = "boolean",
            variationValue = fakeValue,
            doLog = false,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariantKey,
            extraLogging = JSONObject(),
            reason = ResolutionReason.TARGETING_MATCH.name
        )

        Mockito.lenient().whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn fakeTimestamp
    }

    // region Evaluation Processing (processEvaluation)

    @Test
    fun `M aggregate evaluation W processEvaluation() { first evaluation }`() {
        // Given
        // When
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.flush()

        // Then
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter).write(eventCaptor.capture())

        val event = eventCaptor.firstValue
        assertThat(event.flag.key).isEqualTo(fakeFlagName)
        assertThat(event.evaluationCount).isEqualTo(1L)
    }

    @Test
    fun `M aggregate same key W processEvaluation() { identical evaluations }`() {
        // Given
        // When
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.flush()

        // Then
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter, times(1)).write(eventCaptor.capture())

        val event = eventCaptor.firstValue
        assertThat(event.flag.key).isEqualTo(fakeFlagName)
        assertThat(event.evaluationCount).isEqualTo(3L)
    }

    @Test
    fun `M create separate aggregations W processEvaluation() { different flags }`(forge: Forge) {
        // Given
        val anotherFlagName = forge.anAlphabeticalString()

        // When
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.processEvaluation(
            anotherFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.flush()

        // Then
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter, times(2)).write(eventCaptor.capture())

        val events = eventCaptor.allValues
        assertThat(events).hasSize(2)
        assertThat(events.map { it.flag.key }).containsExactlyInAnyOrder(fakeFlagName, anotherFlagName)
    }

    @Test
    fun `M create separate aggregations W processEvaluation() { different targeting keys }`(forge: Forge) {
        // Given
        val anotherContext = EvaluationContext(targetingKey = forge.anAlphabeticalString())

        // When
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.processEvaluation(
            fakeFlagName,
            anotherContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.flush()

        // Then
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter, times(2)).write(eventCaptor.capture())

        val events = eventCaptor.allValues
        assertThat(events).hasSize(2)
        assertThat(
            events.map { it.targetingKey }
        ).containsExactlyInAnyOrder(fakeTargetingKey, anotherContext.targetingKey)
    }

    @Test
    fun `M create separate aggregations W processEvaluation() { different variants }`(forge: Forge) {
        // Given
        val anotherData = fakeData.copy(variationKey = forge.anAlphabeticalString())

        // When
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            anotherData.variationKey,
            anotherData.allocationKey,
            anotherData.reason,
            null,
            null
        )
        testedProcessor.flush()

        // Then
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter, times(2)).write(eventCaptor.capture())

        val events = eventCaptor.allValues
        assertThat(events).hasSize(2)
        assertThat(
            events.map { it.variant?.key }
        ).containsExactlyInAnyOrder(fakeData.variationKey, anotherData.variationKey)
    }

    @Test
    fun `M aggregate by error code W processEvaluation() { same code different messages }`(forge: Forge) {
        // Given
        val errorCode = ErrorCode.TYPE_MISMATCH.name
        val errorMessage1 = "Error message 1: ${forge.anAlphabeticalString()}"
        val errorMessage2 = "Error message 2: ${forge.anAlphabeticalString()}"
        val errorMessage3 = "Error message 3: ${forge.anAlphabeticalString()}"

        // When
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, null, null, null, errorCode, errorMessage1)
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, null, null, null, errorCode, errorMessage2)
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, null, null, null, errorCode, errorMessage3)
        testedProcessor.flush()

        // Then - should aggregate into one event
        val eventCaptor = argumentCaptor<com.datadog.android.flags.model.BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter, times(1)).write(eventCaptor.capture())

        // Verify it used the last error message
        val capturedEvent = eventCaptor.firstValue
        assertThat(capturedEvent.error?.message).isEqualTo(errorMessage3)
        assertThat(capturedEvent.evaluationCount).isEqualTo(3L)
    }

    @Test
    fun `M create separate aggregations W processEvaluation() { different error codes }`() {
        // Given
        val errorCode1 = ErrorCode.FLAG_NOT_FOUND.name
        val errorCode2 = ErrorCode.PROVIDER_NOT_READY.name
        val errorMessage1 = "Flag not found"
        val errorMessage2 = "Provider not ready"

        // When
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, null, null, null, errorCode1, errorMessage1)
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, null, null, null, errorCode2, errorMessage2)
        testedProcessor.flush()

        // Then
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter, times(2)).write(eventCaptor.capture())

        val events = eventCaptor.allValues
        assertThat(events).hasSize(2)
        assertThat(events.map { it.error?.message }).containsExactlyInAnyOrder(errorMessage1, errorMessage2)
    }

    @Test
    fun `M increment count W processEvaluation() { multiple evaluations same key }`() {
        // Given
        val timestamps = listOf(fakeTimestamp, fakeTimestamp + 1000, fakeTimestamp + 2000)
        whenever(mockTimeProvider.getDeviceTimestampMillis())
            .thenReturn(timestamps[0], timestamps[1], timestamps[2])

        // When
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.flush()

        // Then
        val eventCaptor = argumentCaptor<com.datadog.android.flags.model.BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter).write(eventCaptor.capture())

        val event = eventCaptor.firstValue
        assertThat(event.evaluationCount).isEqualTo(3L)
        assertThat(event.firstEvaluation).isEqualTo(timestamps[0])
        assertThat(event.lastEvaluation).isEqualTo(timestamps[2])
    }

    // endregion

    // region processEvaluation

    @Test
    fun `M aggregate evaluation W processEvaluation() { with complete flag data }`() {
        // Given
        // When
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.flush()

        // Then
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter).write(eventCaptor.capture())

        val event = eventCaptor.firstValue
        assertThat(event.flag.key).isEqualTo(fakeFlagName)
        assertThat(event.variant?.key).isEqualTo(fakeVariantKey)
        assertThat(event.allocation?.key).isEqualTo(fakeAllocationKey)
    }

    @Test
    fun `M include variant and allocation W processEvaluation() { in aggregation key }`() {
        // Given
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()

        // When
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.flush()

        // Then
        verify(mockWriter).write(eventCaptor.capture())
        assertThat(eventCaptor.firstValue.variant?.key).isEqualTo(fakeVariantKey)
        assertThat(eventCaptor.firstValue.allocation?.key).isEqualTo(fakeAllocationKey)
        assertThat(eventCaptor.firstValue.error).isNull()
    }

    @Test
    fun `M aggregate evaluation W processEvaluation() { without flag data }`() {
        // Given
        val errorCode = ErrorCode.FLAG_NOT_FOUND.name
        val errorMessage = "Flag not found in repository"

        // When
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, null, null, null, errorCode, errorMessage)
        testedProcessor.flush()

        // Then
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter).write(eventCaptor.capture())

        val event = eventCaptor.firstValue
        assertThat(event.flag.key).isEqualTo(fakeFlagName)
        assertThat(event.error?.message).isEqualTo(errorMessage)
    }

    @Test
    fun `M omit variant and allocation W processEvaluation() { error has no flag data }`() {
        // Given
        val errorCode = ErrorCode.PROVIDER_NOT_READY.name
        val errorMessage = "Provider not initialized"
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()

        // When
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, null, null, null, errorCode, errorMessage)
        testedProcessor.flush()

        // Then
        verify(mockWriter).write(eventCaptor.capture())
        assertThat(eventCaptor.firstValue.variant).isNull()
        assertThat(eventCaptor.firstValue.allocation).isNull()
        assertThat(eventCaptor.firstValue.error?.message).isEqualTo(errorMessage)
        assertThat(eventCaptor.firstValue.runtimeDefaultUsed).isTrue()
    }

    @Test
    fun `M aggregate separately W processEvaluation() { same flag different paths }`() {
        // Given
        val errorCode = ErrorCode.FLAG_NOT_FOUND.name
        val errorMessage = "Not found"

        // When - same flag, but one success and one error
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, null, null, null, errorCode, errorMessage)
        testedProcessor.flush()

        // Then - should create 2 separate aggregations
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter, times(2)).write(eventCaptor.capture())

        val events = eventCaptor.allValues
        assertThat(events).hasSize(2)
        assertThat(events.count { it.error != null }).isEqualTo(1)
        assertThat(events.count { it.variant != null }).isEqualTo(1)
    }

    @Test
    fun `M aggregate same errors W processEvaluation() { same error code }`() {
        // Given
        val errorCode = ErrorCode.TYPE_MISMATCH.name
        val errorMessage1 = "Type mismatch: expected boolean"
        val errorMessage2 = "Type mismatch: expected string"

        // When - same error code, different messages
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, null, null, null, errorCode, errorMessage1)
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, null, null, null, errorCode, errorMessage2)
        testedProcessor.flush()

        // Then - should aggregate (same key)
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter, times(1)).write(eventCaptor.capture())

        val event = eventCaptor.firstValue
        assertThat(event.evaluationCount).isEqualTo(2L)
        assertThat(event.error?.message).isEqualTo(errorMessage2) // Last message wins
    }

    @Test
    fun `M aggregate separately W processEvaluation() { different error codes }`() {
        // Given
        val errorCode1 = ErrorCode.FLAG_NOT_FOUND.name
        val errorCode2 = ErrorCode.PROVIDER_NOT_READY.name

        // When - different error codes
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, null, null, null, errorCode1, "Error 1")
        testedProcessor.processEvaluation(fakeFlagName, fakeContext, null, null, null, errorCode2, "Error 2")
        testedProcessor.flush()

        // Then - should create separate aggregations
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter, times(2)).write(eventCaptor.capture())

        val events = eventCaptor.allValues
        assertThat(events).hasSize(2)
        assertThat(events.map { it.error?.message }).containsExactlyInAnyOrder("Error 1", "Error 2")
    }

    // endregion

    // region flush

    @Test
    fun `M write events W flush() { aggregations exist }`() {
        // Given
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )

        // When
        testedProcessor.flush()

        // Then
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter).write(eventCaptor.capture())

        assertThat(eventCaptor.firstValue.flag.key).isEqualTo(fakeFlagName)
    }

    @Test
    fun `M not write W flush() { empty aggregations }`() {
        // Given
        // When
        testedProcessor.flush()

        // Then
        verify(mockWriter, never()).write(any())
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M flush automatically W processEvaluation() { size limit reached }`() {
        // Given
        val maxAggregations = 5
        val testProcessor = EvaluationEventsProcessor(
            writer = mockWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger,
            maxAggregations = maxAggregations
        )

        // When
        repeat(maxAggregations) { index ->
            val uniqueContext = EvaluationContext(targetingKey = "user-$index")
            testProcessor.processEvaluation(
                fakeFlagName,
                uniqueContext,
                fakeData.variationKey,
                fakeData.allocationKey,
                fakeData.reason,
                null,
                null
            )
        }

        // Then - should have auto-flushed
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter, times(maxAggregations)).write(eventCaptor.capture())

        assertThat(eventCaptor.allValues).hasSize(maxAggregations)
    }

    @Test
    fun `M reset and continue W flush() { after flush clears and accepts new }`() {
        // Given
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.flush()

        // When - new evaluation after flush
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )
        testedProcessor.flush()

        // Then
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter, times(2)).write(eventCaptor.capture())

        val events = eventCaptor.allValues
        assertThat(events).hasSize(2)
        assertThat(events.map { it.evaluationCount }).containsOnly(1L)
    }

    @Test
    fun `M cancel scheduled future W flush() { future exists }`() {
        // Given
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())) doReturn mockScheduledFuture
        testedProcessor.schedulePeriodicFlush()

        // Add evaluation
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )

        // When - manual flush
        testedProcessor.flush()

        // Then - should cancel the existing scheduled future
        verify(mockScheduledFuture).cancel(false)
    }

    @Test
    fun `M reschedule periodic flush W flush() { after completing flush }`() {
        // Given
        var scheduleCount = 0
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())).thenAnswer {
            scheduleCount++
            mockScheduledFuture
        }

        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )

        // When
        testedProcessor.flush()

        // Then - should reschedule periodic flush
        assertThat(scheduleCount).isGreaterThan(0)
        verify(mockScheduledExecutor, atLeastOnce()).schedule(
            any<Runnable>(),
            eq(10_000L),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    // endregion

    // region schedulePeriodicFlush

    @Test
    fun `M schedule flush W schedulePeriodicFlush()`() {
        // Given
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())) doReturn mockScheduledFuture

        // When
        testedProcessor.schedulePeriodicFlush()

        // Then
        verify(mockScheduledExecutor).schedule(
            any<Runnable>(),
            eq(10_000L),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M log error W schedulePeriodicFlush() { executor throws }`(forge: Forge) {
        // Given
        val exception = RejectedExecutionException(forge.anAlphabeticalString())
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any()))
            .doReturn(mockScheduledFuture)
            .doThrow(exception)

        // When
        testedProcessor.schedulePeriodicFlush()
        testedProcessor.schedulePeriodicFlush() // Second call throws

        // Then - should log at WARN level (not ERROR)
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            any<List<InternalLogger.Target>>(),
            any(),
            eq(exception),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M reschedule itself W scheduled task runs()`() {
        // Given
        var taskRunnable: Runnable? = null
        var scheduleCount = 0

        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())).thenAnswer {
            scheduleCount++
            taskRunnable = it.getArgument(0)
            mockScheduledFuture
        }

        // When
        testedProcessor.schedulePeriodicFlush() // Initial schedule
        assertThat(scheduleCount).isEqualTo(1)

        taskRunnable?.run() // Execute the scheduled task

        // Then - should have rescheduled itself
        assertThat(scheduleCount).isEqualTo(2)
        verify(mockScheduledExecutor, times(2)).schedule(
            any<Runnable>(),
            eq(10_000L),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M reschedule W scheduled task runs() { with empty flush }`() {
        // Given
        var taskRunnable: Runnable? = null
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())).thenAnswer {
            taskRunnable = it.getArgument(0)
            mockScheduledFuture
        }

        // When
        testedProcessor.schedulePeriodicFlush()
        taskRunnable?.run() // Execute with no evaluations to flush

        // Then - should not write but should reschedule
        verify(mockWriter, never()).write(any())
        verify(mockScheduledExecutor, times(2)).schedule(
            any<Runnable>(),
            eq(10_000L),
            eq(TimeUnit.MILLISECONDS)
        )
        verifyNoMoreInteractions(mockWriter)
    }

    // endregion

    // region stop

    @Test
    fun `M flush W stop() { no scheduled task }`() {
        // Given
        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn true
        testedProcessor.processEvaluation(
            fakeFlagName,
            fakeContext,
            fakeData.variationKey,
            fakeData.allocationKey,
            fakeData.reason,
            null,
            null
        )

        // When
        testedProcessor.stop()

        // Then
        verify(mockScheduledExecutor).shutdown()
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter).write(eventCaptor.capture())

        assertThat(eventCaptor.firstValue.flag.key).isEqualTo(fakeFlagName)
    }

    @Test
    fun `M not write W stop() { empty aggregations }`() {
        // Given
        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn true

        // When
        testedProcessor.stop()

        // Then
        verify(mockScheduledExecutor).shutdown()
        verify(mockWriter, never()).write(any())
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `M shutdown before cancel W stop() { ensures order }`() {
        // Given
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any()))
            .doReturn(mockScheduledFuture)
            .doThrow(RejectedExecutionException("Executor shutdown")) // flush() tries to reschedule
        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn true
        testedProcessor.schedulePeriodicFlush()

        // When
        testedProcessor.stop()

        // Then - verify shutdown is called first
        val inOrder = org.mockito.Mockito.inOrder(mockScheduledExecutor, mockScheduledFuture)
        inOrder.verify(mockScheduledExecutor).shutdown()
        // Note: cancel may be called multiple times (from stop() and from flush())
        inOrder.verify(mockScheduledFuture, atLeastOnce()).cancel(false)
    }

    @Test
    fun `M force shutdown W stop() { termination timeout }`() {
        // Given
        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn false
        whenever(mockScheduledExecutor.shutdownNow()).thenReturn(emptyList())

        // When
        testedProcessor.stop()

        // Then - shutdownNow should be called when awaitTermination returns false
        verify(mockScheduledExecutor).shutdown()
        verify(mockScheduledExecutor).shutdownNow()
    }

    @Test
    fun `M force shutdown and restore interrupt W stop() { interrupted }`() {
        // Given
        whenever(mockScheduledExecutor.awaitTermination(any(), any())).thenThrow(InterruptedException())
        whenever(mockScheduledExecutor.shutdownNow()).thenReturn(emptyList())

        // When
        testedProcessor.stop()

        // Then - shutdownNow should be called and thread interrupt status restored
        verify(mockScheduledExecutor).shutdown()
        verify(mockScheduledExecutor).shutdownNow()
        assertThat(Thread.interrupted()).isTrue() // Verify interrupt flag was set
    }

    @Test
    fun `M prevent reschedule W stop() { task running during stop }`() {
        // Given
        var taskRunnable: Runnable? = null
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any())).thenAnswer { invocation ->
            taskRunnable = invocation.getArgument(0)
            mockScheduledFuture
        }
        whenever(mockScheduledExecutor.awaitTermination(any(), any())) doReturn true

        testedProcessor.schedulePeriodicFlush()

        // Simulate the executor being shutdown
        whenever(mockScheduledExecutor.schedule(any<Runnable>(), any(), any()))
            .thenThrow(RejectedExecutionException("Executor shutdown"))

        // When - simulate task running during stop
        val stopThread = Thread {
            testedProcessor.stop()
        }
        stopThread.start()

        // Execute the scheduled task (which will try to reschedule)
        taskRunnable?.run()

        stopThread.join()

        // Then - exception should be caught and logged (not crash)
        // Note: stop() calls flush(rescheduleFlush=false) which doesn't reschedule,
        // but the running scheduled task tries to reschedule and gets rejected
        verify(mockInternalLogger, atLeastOnce()).log(
            eq(InternalLogger.Level.WARN),
            any<List<InternalLogger.Target>>(),
            any(),
            any<RejectedExecutionException>(),
            eq(false),
            eq(null)
        )
    }

    // endregion

    // region Thread Safety

    @Test
    fun `M handle concurrent evaluations W processEvaluation() { same key }`() {
        // Given
        val threadCount = 10
        val executionsPerThread = 100

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        // When
        val threads = (1..threadCount).map {
            Thread {
                startLatch.await()
                repeat(executionsPerThread) {
                    testedProcessor.processEvaluation(
                        fakeFlagName,
                        fakeContext,
                        fakeData.variationKey,
                        fakeData.allocationKey,
                        fakeData.reason,
                        null,
                        null
                    )
                }
                finishLatch.countDown()
            }
        }

        threads.forEach { it.start() }
        startLatch.countDown() // Start all at once
        finishLatch.await()

        testedProcessor.flush()

        // Then - should aggregate all into one event
        val eventCaptor = argumentCaptor<com.datadog.android.flags.model.BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter).write(eventCaptor.capture())

        val event = eventCaptor.firstValue
        assertThat(event.evaluationCount).isEqualTo((threadCount * executionsPerThread).toLong())
    }

    @Test
    fun `M handle concurrent evaluations W processEvaluation() { different keys }`() {
        // Given
        val threadCount = 10
        val executionsPerThread = 50

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        // When
        val threads = (1..threadCount).map { threadIndex ->
            Thread {
                startLatch.await()
                repeat(executionsPerThread) { executionIndex ->
                    val uniqueContext = EvaluationContext(
                        targetingKey = "thread-$threadIndex-execution-$executionIndex"
                    )
                    testedProcessor.processEvaluation(
                        fakeFlagName,
                        uniqueContext,
                        fakeData.variationKey,
                        fakeData.allocationKey,
                        fakeData.reason,
                        null,
                        null
                    )
                }
                finishLatch.countDown()
            }
        }

        threads.forEach { it.start() }
        startLatch.countDown()
        finishLatch.await()

        testedProcessor.flush()

        // Then
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter, times(threadCount * executionsPerThread)).write(eventCaptor.capture())

        assertThat(eventCaptor.allValues).hasSize(threadCount * executionsPerThread)
    }

    @Test
    fun `M handle concurrent flush W processEvaluation and flush() { mixed operations }`() {
        // Given
        val processingThreadCount = 5
        val flushThreadCount = 2
        val executionsPerThread = 100

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(processingThreadCount + flushThreadCount)

        // When
        val processingThreads = (1..processingThreadCount).map { threadIndex ->
            Thread {
                startLatch.await()
                repeat(executionsPerThread) { executionIndex ->
                    val context = EvaluationContext(targetingKey = "user-$threadIndex-$executionIndex")
                    testedProcessor.processEvaluation(
                        fakeFlagName,
                        context,
                        fakeData.variationKey,
                        fakeData.allocationKey,
                        fakeData.reason,
                        null,
                        null
                    )
                }
                finishLatch.countDown()
            }
        }

        val flushThreads = (1..flushThreadCount).map {
            Thread {
                startLatch.await()
                repeat(10) {
                    Thread.sleep(5)
                    testedProcessor.flush()
                }
                finishLatch.countDown()
            }
        }

        val allThreads = processingThreads + flushThreads
        allThreads.forEach { it.start() }
        startLatch.countDown()
        finishLatch.await()

        testedProcessor.flush() // Final flush

        // Then - should have written some events
        val eventCaptor = argumentCaptor<BatchedFlagEvaluations.FlagEvaluation>()
        verify(mockWriter, times(processingThreadCount * executionsPerThread)).write(eventCaptor.capture())

        assertThat(eventCaptor.allValues).hasSize(processingThreadCount * executionsPerThread)
    }

    @Test
    fun `M skip concurrent flush W flush() { flush already in progress }`() {
        // Given - populate map with events
        repeat(100) { index ->
            val context = EvaluationContext(targetingKey = "user-$index")
            testedProcessor.processEvaluation(
                fakeFlagName,
                context,
                fakeData.variationKey,
                fakeData.allocationKey,
                fakeData.reason,
                null,
                null
            )
        }

        // Create a slow writer that blocks during write
        val slowWriteLatch = CountDownLatch(1)
        val writeStartedLatch = CountDownLatch(1)
        val slowWriter = object : EvaluationEventWriter {
            override fun write(event: BatchedFlagEvaluations.FlagEvaluation) {
                writeStartedLatch.countDown()
                slowWriteLatch.await() // Block until released
            }
        }

        val slowProcessor = EvaluationEventsProcessor(
            writer = slowWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger
        )

        // Populate slow processor
        repeat(100) { index ->
            val context = EvaluationContext(targetingKey = "user-$index")
            slowProcessor.processEvaluation(
                fakeFlagName,
                context,
                fakeData.variationKey,
                fakeData.allocationKey,
                fakeData.reason,
                null,
                null
            )
        }

        val flush1Started = CountDownLatch(1)
        val flush1Complete = CountDownLatch(1)
        val flush2Complete = CountDownLatch(1)

        // When - trigger flush from two threads simultaneously
        val thread1 = Thread {
            flush1Started.countDown()
            slowProcessor.flush()
            flush1Complete.countDown()
        }

        val thread2 = Thread {
            flush1Started.await() // Wait for thread1 to start
            Thread.sleep(10) // Ensure thread1 enters flush first
            slowProcessor.flush() // Should return immediately
            flush2Complete.countDown()
        }

        thread1.start()
        thread2.start()

        // Wait for both flush calls to be made
        writeStartedLatch.await() // Thread1's flush started writing

        // Then - thread2 should have completed immediately without blocking
        val thread2Completed = flush2Complete.await(100, TimeUnit.MILLISECONDS)
        assertThat(thread2Completed).isTrue() // Thread2 should return immediately

        // Release the slow writer
        slowWriteLatch.countDown()
        flush1Complete.await()

        thread1.join()
        thread2.join()
    }

    @Test
    fun `M handle concurrent new key insertions W processEvaluation() { lock-free }`() {
        // Given - test lock-free putIfAbsent behavior with unique keys
        val threadCount = 10
        val uniqueKeysPerThread = 100

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        // When - threads insert unique keys concurrently (should be lock-free)
        val threads = (1..threadCount).map { threadId ->
            Thread {
                startLatch.await()
                repeat(uniqueKeysPerThread) { index ->
                    val uniqueContext = EvaluationContext("user-$threadId-$index")
                    testedProcessor.processEvaluation(
                        fakeFlagName,
                        uniqueContext,
                        fakeVariantKey,
                        fakeAllocationKey,
                        fakeData.reason,
                        null,
                        null
                    )
                }
                finishLatch.countDown()
            }
        }

        threads.forEach { it.start() }
        startLatch.countDown() // Start all at once
        finishLatch.await()

        testedProcessor.flush()

        // Then - all unique keys should be written (no lost updates)
        verify(mockWriter, times(threadCount * uniqueKeysPerThread)).write(any())
    }

    @Test
    fun `M continue accepting evaluations W flush() { during flush operation }`() {
        // Given - populate initial evaluations
        repeat(50) { index ->
            val context = EvaluationContext(targetingKey = "initial-$index")
            testedProcessor.processEvaluation(
                fakeFlagName,
                context,
                fakeData.variationKey,
                fakeData.allocationKey,
                fakeData.reason,
                null,
                null
            )
        }

        // Create a slow writer to simulate long flush
        val flushInProgress = CountDownLatch(1)
        val continueFlush = CountDownLatch(1)
        val writeCount = java.util.concurrent.atomic.AtomicInteger(0)

        val slowWriter = object : EvaluationEventWriter {
            override fun write(event: BatchedFlagEvaluations.FlagEvaluation) {
                if (writeCount.incrementAndGet() == 1) {
                    flushInProgress.countDown() // Signal first write started
                    continueFlush.await() // Block until signaled
                }
            }
        }

        val slowProcessor = EvaluationEventsProcessor(
            writer = slowWriter,
            timeProvider = mockTimeProvider,
            scheduledExecutor = mockScheduledExecutor,
            internalLogger = mockInternalLogger
        )

        // Populate slow processor
        repeat(50) { index ->
            val context = EvaluationContext(targetingKey = "initial-$index")
            slowProcessor.processEvaluation(
                fakeFlagName,
                context,
                fakeData.variationKey,
                fakeData.allocationKey,
                fakeData.reason,
                null,
                null
            )
        }

        val flushComplete = CountDownLatch(1)

        // When - start flush in background
        val flushThread = Thread {
            slowProcessor.flush()
            flushComplete.countDown()
        }
        flushThread.start()

        // Wait for flush to start writing
        flushInProgress.await()

        // Add new evaluations DURING flush (should go to new map due to swap pattern)
        repeat(25) { index ->
            val context = EvaluationContext(targetingKey = "during-flush-$index")
            slowProcessor.processEvaluation(
                fakeFlagName,
                context,
                fakeData.variationKey,
                fakeData.allocationKey,
                fakeData.reason,
                null,
                null
            )
        }

        // Release the flush
        continueFlush.countDown()
        flushComplete.await()
        flushThread.join()

        // Then - flush again and verify the new evaluations are present
        slowProcessor.flush()

        // Should have written initial 50 + new 25 during flush
        assertThat(writeCount.get()).isEqualTo(75)
    }

    // endregion
}
