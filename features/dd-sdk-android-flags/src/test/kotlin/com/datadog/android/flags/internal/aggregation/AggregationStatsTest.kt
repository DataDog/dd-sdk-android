/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.android.flags.model.UnparsedFlag
import com.datadog.android.flags.utils.forge.ForgeConfigurator
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class AggregationStatsTest {

    @LongForgery(min = 1000000L)
    var fakeTimestamp = 0L

    @StringForgery
    lateinit var fakeTargetingKey: String

    @StringForgery
    lateinit var fakeVariantKey: String

    @StringForgery
    lateinit var fakeAllocationKey: String

    @StringForgery
    lateinit var fakeValue: String

    @StringForgery
    lateinit var fakeFlagName: String

    private lateinit var fakeContext: EvaluationContext
    private lateinit var fakeData: UnparsedFlag
    private lateinit var fakeAggregationKey: AggregationKey

    @BeforeEach
    fun `set up`() {
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
        fakeAggregationKey = AggregationKey(
            flagKey = fakeFlagName,
            variantKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            targetingKey = fakeTargetingKey,
            errorCode = null
        )
    }

    // region recordEvaluation

    @Test
    fun `M increment count W recordEvaluation()`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData.reason, null)

        // When
        stats.recordEvaluation(fakeTimestamp + 1000, null)
        stats.recordEvaluation(fakeTimestamp + 2000, null)

        // Then
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        assertThat(event.evaluationCount).isEqualTo(3L)
    }

    @Test
    fun `M update timestamps W recordEvaluation() and toEvaluationEvent()`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData.reason, null)
        val laterTimestamp = fakeTimestamp + 5000

        // When
        stats.recordEvaluation(laterTimestamp, null)

        // Then - lastEvaluation updated, firstEvaluation unchanged, timestamp equals firstEvaluation
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        assertThat(event.lastEvaluation).isEqualTo(laterTimestamp)
        assertThat(event.firstEvaluation).isEqualTo(fakeTimestamp)
        assertThat(event.timestamp).isEqualTo(event.firstEvaluation)
    }

    @Test
    fun `M preserve first evaluation timestamp W recordEvaluation() { multiple calls }`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData.reason, null)

        // When
        repeat(10) { index ->
            stats.recordEvaluation(fakeTimestamp + (index + 1) * 1000, null)
        }

        // Then
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        assertThat(event.firstEvaluation).isEqualTo(fakeTimestamp)
    }

    @Test
    fun `M update error message and count W recordEvaluation() { multiple errors same code }`(forge: Forge) {
        // Given
        val errorMessage1 = "First error: ${forge.anAlphabeticalString()}"
        val errorMessage2 = "Second error: ${forge.anAlphabeticalString()}"
        val errorMessage3 = "Third error: ${forge.anAlphabeticalString()}"
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData.reason, errorMessage1)

        // When
        stats.recordEvaluation(fakeTimestamp + 1000, errorMessage2)
        stats.recordEvaluation(fakeTimestamp + 2000, errorMessage3)

        // Then - last error message and aggregated count
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        assertThat(event.error?.message).isEqualTo(errorMessage3)
        assertThat(event.evaluationCount).isEqualTo(3L)
    }

    // endregion

    // region toEvaluationEvent

    @Test
    fun `M create event with correct fields W toEvaluationEvent() { successful match }`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData.reason, null)

        // When
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)

        // Then
        assertThat(event.timestamp).isEqualTo(fakeTimestamp)
        assertThat(event.flag.key).isEqualTo(fakeFlagName)
        assertThat(event.variant?.key).isEqualTo(fakeVariantKey)
        assertThat(event.allocation?.key).isEqualTo(fakeAllocationKey)
        assertThat(event.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(event.evaluationCount).isEqualTo(1L)
        assertThat(event.firstEvaluation).isEqualTo(fakeTimestamp)
        assertThat(event.lastEvaluation).isEqualTo(fakeTimestamp)
        assertThat(event.runtimeDefaultUsed).isFalse()
        assertThat(event.error).isNull()
        assertThat(event.targetingRule).isNull()
        assertThat(event.context).isNull()
    }

    @Test
    fun `M set runtime default true W toEvaluationEvent() { DEFAULT or ERROR reason }`(forge: Forge) {
        // Given - DEFAULT reason
        val defaultData = PrecomputedFlag(
            variationType = "boolean",
            variationValue = fakeValue,
            doLog = false,
            allocationKey = "",
            variationKey = "",
            extraLogging = JSONObject(),
            reason = ResolutionReason.DEFAULT.name
        )
        val defaultStats = AggregationStats(fakeTimestamp, fakeContext, defaultData.reason, null)
        val keyWithoutVariant = fakeAggregationKey.copy(variantKey = null, allocationKey = null)

        // When - DEFAULT reason
        val defaultEvent = defaultStats.toEvaluationEvent(fakeFlagName, keyWithoutVariant)

        // Then
        assertThat(defaultEvent.runtimeDefaultUsed).isTrue()
        assertThat(defaultEvent.variant).isNull()
        assertThat(defaultEvent.allocation).isNull()

        // Given - ERROR reason (reason = null, no flag data)
        val errorMessage = forge.anAlphabeticalString()
        val errorStats = AggregationStats(fakeTimestamp, fakeContext, null, errorMessage)
        val keyWithError = fakeAggregationKey.copy(
            variantKey = null,
            allocationKey = null,
            errorCode = "FLAG_NOT_FOUND"
        )

        // When - ERROR reason
        val errorEvent = errorStats.toEvaluationEvent(fakeFlagName, keyWithError)

        // Then
        assertThat(errorEvent.runtimeDefaultUsed).isTrue()
        assertThat(errorEvent.error?.message).isEqualTo(errorMessage)
    }

    @Test
    fun `M set runtime default false W toEvaluationEvent() { MATCHED or TARGETING_MATCH reason }`() {
        // Given - MATCHED reason
        val matchedStats = AggregationStats(fakeTimestamp, fakeContext, fakeData.reason, null)

        // When
        val matchedEvent = matchedStats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)

        // Then
        assertThat(matchedEvent.runtimeDefaultUsed).isFalse()

        // Given - TARGETING_MATCH reason
        val targetingData = PrecomputedFlag(
            variationType = "boolean",
            variationValue = fakeValue,
            doLog = false,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariantKey,
            extraLogging = JSONObject(),
            reason = ResolutionReason.TARGETING_MATCH.name
        )
        val targetingStats = AggregationStats(fakeTimestamp, fakeContext, targetingData.reason, null)

        // When
        val targetingEvent = targetingStats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)

        // Then
        assertThat(targetingEvent.runtimeDefaultUsed).isFalse()
    }

    @Test
    fun `M set runtime default false W toEvaluationEvent() { unrecognized reason }`(forge: Forge) {
        // Given - unrecognized reasons are treated like normal matches (not DEFAULT/ERROR)
        val unrecognizedReason = forge.anAlphabeticalString()
        val unrecognizedData = PrecomputedFlag(
            variationType = "boolean",
            variationValue = fakeValue,
            doLog = false,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariantKey,
            extraLogging = JSONObject(),
            reason = unrecognizedReason
        )
        val stats = AggregationStats(fakeTimestamp, fakeContext, unrecognizedData.reason, null)

        // When
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)

        // Then - unrecognized reasons should not set runtimeDefaultUsed for forward compatibility
        assertThat(event.runtimeDefaultUsed).isFalse()
    }

    @Test
    fun `M handle error message W toEvaluationEvent() { error present or absent }`(forge: Forge) {
        // Given - error provided
        val errorMessage = forge.anAlphabeticalString()
        val statsWithError = AggregationStats(fakeTimestamp, fakeContext, fakeData.reason, errorMessage)

        // When
        val eventWithError = statsWithError.toEvaluationEvent(fakeFlagName, fakeAggregationKey)

        // Then
        assertThat(eventWithError.error?.message).isEqualTo(errorMessage)

        // Given - no error
        val statsWithoutError = AggregationStats(fakeTimestamp, fakeContext, fakeData.reason, null)

        // When
        val eventWithoutError = statsWithoutError.toEvaluationEvent(fakeFlagName, fakeAggregationKey)

        // Then
        assertThat(eventWithoutError.error).isNull()
    }

    @Test
    fun `M use aggregation key fields W toEvaluationEvent() { key overrides data }`(forge: Forge) {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData.reason, null)
        val keyWithDifferentValues = AggregationKey(
            flagKey = forge.anAlphabeticalString(),
            variantKey = forge.anAlphabeticalString(),
            allocationKey = forge.anAlphabeticalString(),
            targetingKey = forge.anAlphabeticalString(),
            errorCode = null
        )

        // When
        val event = stats.toEvaluationEvent(forge.anAlphabeticalString(), keyWithDifferentValues)

        // Then
        assertThat(event.variant?.key).isEqualTo(keyWithDifferentValues.variantKey)
        assertThat(event.allocation?.key).isEqualTo(keyWithDifferentValues.allocationKey)
        assertThat(event.targetingKey).isEqualTo(keyWithDifferentValues.targetingKey)
    }

    @Test
    fun `M handle null variant and allocation W toEvaluationEvent() { key has null fields }`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData.reason, null)
        
        // When - null variant
        val keyWithNullVariant = fakeAggregationKey.copy(variantKey = null)
        val eventWithNullVariant = stats.toEvaluationEvent(fakeFlagName, keyWithNullVariant)

        // Then
        assertThat(eventWithNullVariant.variant).isNull()

        // When - null allocation
        val keyWithNullAllocation = fakeAggregationKey.copy(allocationKey = null)
        val eventWithNullAllocation = stats.toEvaluationEvent(fakeFlagName, keyWithNullAllocation)

        // Then
        assertThat(eventWithNullAllocation.allocation).isNull()
    }

    // endregion

    // region Thread Safety

    @Test
    fun `M handle concurrent recordEvaluation calls W recordEvaluation() { multiple threads }`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData.reason, null)
        val threadCount = 10
        val executionsPerThread = 100
        val expectedCount = threadCount * executionsPerThread + 1 // +1 for initial construction

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        // When
        val threads = (1..threadCount).map { threadIndex ->
            Thread {
                startLatch.await()
                repeat(executionsPerThread) { executionIndex ->
                    stats.recordEvaluation(
                        fakeTimestamp + (threadIndex * 1000) + executionIndex,
                        null
                    )
                }
                finishLatch.countDown()
            }
        }

        threads.forEach { it.start() }
        startLatch.countDown() // Start all threads at once
        finishLatch.await()

        // Then
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        assertThat(event.evaluationCount).isEqualTo(expectedCount.toLong())
    }

    @Test
    fun `M produce consistent snapshot W toEvaluationEvent() { called during concurrent updates }`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData.reason, null)
        val updateThreadCount = 5
        val snapshotThreadCount = 5
        val executionsPerThread = 100

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(updateThreadCount + snapshotThreadCount)
        val inconsistentSnapshots = AtomicInteger(0)

        // When
        val updateThreads = (1..updateThreadCount).map {
            Thread {
                startLatch.await()
                repeat(executionsPerThread) { index ->
                    stats.recordEvaluation(fakeTimestamp + index, null)
                }
                finishLatch.countDown()
            }
        }

        val snapshotThreads = (1..snapshotThreadCount).map {
            Thread {
                startLatch.await()
                repeat(executionsPerThread) {
                    val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
                    // Verify snapshot consistency
                    if (event.firstEvaluation > event.lastEvaluation) {
                        inconsistentSnapshots.incrementAndGet()
                    }
                    if (event.timestamp != event.firstEvaluation) {
                        inconsistentSnapshots.incrementAndGet()
                    }
                }
                finishLatch.countDown()
            }
        }

        val allThreads = updateThreads + snapshotThreads
        allThreads.forEach { it.start() }
        startLatch.countDown()
        finishLatch.await()

        // Then
        assertThat(inconsistentSnapshots.get()).isEqualTo(0)
    }

    @Test
    fun `M handle concurrent error message updates W recordEvaluation() { multiple threads }`(forge: Forge) {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData.reason, null)
        val threadCount = 10
        val executionsPerThread = 50

        val errorMessages = (1..threadCount).map { "Error from thread $it: ${forge.anAlphabeticalString()}" }

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        // When
        val threads = (1..threadCount).map { threadIndex ->
            Thread {
                startLatch.await()
                repeat(executionsPerThread) { executionIndex ->
                    stats.recordEvaluation(
                        fakeTimestamp + (threadIndex * 1000) + executionIndex,
                        errorMessages[threadIndex - 1]
                    )
                }
                finishLatch.countDown()
            }
        }

        threads.forEach { it.start() }
        startLatch.countDown()
        finishLatch.await()

        // Then
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        // Last error message should be one of the error messages
        assertThat(errorMessages).contains(event.error?.message)
    }

    // endregion

    // region Out of order timestamps

    @Test
    fun `M maintain correct range W recordEvaluation() { out of order arrival }`() {
        // Given - initial timestamp at 5000ms
        val initialTimestamp = 5000L
        val stats = AggregationStats(initialTimestamp, fakeContext, fakeData.reason, null)

        // When - events arrive out of order
        stats.recordEvaluation(10000L, null) // Late event
        stats.recordEvaluation(2000L, null) // Early event (out of order)
        stats.recordEvaluation(7000L, null) // Middle event (out of order)

        // Then - should track actual min and max
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        assertThat(event.firstEvaluation).isEqualTo(2000L) // Minimum
        assertThat(event.lastEvaluation).isEqualTo(10000L) // Maximum
        assertThat(event.evaluationCount).isEqualTo(4L)
    }

    @Test
    fun `M handle same timestamp W recordEvaluation() { multiple evaluations at same time }`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData.reason, null)

        // When - multiple evaluations at exact same timestamp
        repeat(5) {
            stats.recordEvaluation(fakeTimestamp, null)
        }

        // Then - timestamps shouldn't change, but count should increment
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        assertThat(event.firstEvaluation).isEqualTo(fakeTimestamp)
        assertThat(event.lastEvaluation).isEqualTo(fakeTimestamp)
        assertThat(event.evaluationCount).isEqualTo(6L) // 1 initial + 5 recorded
    }

    @Test
    fun `M handle large time jumps W recordEvaluation() { forward and backward }`() {
        // Given - test both forward and backward large jumps
        val stats = AggregationStats(50000L, fakeContext, fakeData.reason, null)

        // When - large time jump forward (e.g., NTP sync, time zone change)
        val futureTimestamp = 1000000000L
        stats.recordEvaluation(futureTimestamp, null)

        // Then - lastEvaluation should update
        val eventAfterForward = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        assertThat(eventAfterForward.lastEvaluation).isEqualTo(futureTimestamp)

        // When - large time jump backward (e.g., clock adjustment)
        val pastTimestamp = 1000L
        stats.recordEvaluation(pastTimestamp, null)

        // Then - firstEvaluation should update, lastEvaluation unchanged
        val eventAfterBackward = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        assertThat(eventAfterBackward.firstEvaluation).isEqualTo(pastTimestamp)
        assertThat(eventAfterBackward.lastEvaluation).isEqualTo(futureTimestamp)
        assertThat(eventAfterBackward.evaluationCount).isEqualTo(3L)
    }

    @Test
    fun `M handle concurrent out-of-order evaluations W recordEvaluation() { thread safety }`() {
        // Given
        val initialTimestamp = 50000L
        val stats = AggregationStats(initialTimestamp, fakeContext, fakeData.reason, null)
        val threadCount = 10
        val executionsPerThread = 100

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threadCount)

        // When - threads send evaluations with intentionally out-of-order timestamps
        val threads = (1..threadCount).map { threadIndex ->
            Thread {
                startLatch.await()
                repeat(executionsPerThread) { executionIndex ->
                    // Some threads go backwards, some forward, creating complex out-of-order scenario
                    val timestamp = if (threadIndex % 2 == 0) {
                        // Even threads: go backwards from initial
                        initialTimestamp - (threadIndex * 1000L) - executionIndex
                    } else {
                        // Odd threads: go forwards from initial
                        initialTimestamp + (threadIndex * 1000L) + executionIndex
                    }
                    stats.recordEvaluation(timestamp, null)
                }
                finishLatch.countDown()
            }
        }

        threads.forEach { it.start() }
        startLatch.countDown()
        finishLatch.await()

        // Then - should have correct min/max despite chaos
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)

        // Calculate expected min and max
        val minTimestamp = initialTimestamp - (10 * 1000L) - 99 // Thread 10, last execution
        val maxTimestamp = initialTimestamp + (9 * 1000L) + 99 // Thread 9, last execution

        assertThat(event.firstEvaluation).isEqualTo(minTimestamp)
        assertThat(event.lastEvaluation).isEqualTo(maxTimestamp)
        assertThat(event.firstEvaluation).isLessThan(event.lastEvaluation)
        assertThat(event.evaluationCount).isEqualTo((threadCount * executionsPerThread + 1).toLong())
    }

    // endregion
}
