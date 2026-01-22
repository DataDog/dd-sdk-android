/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

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
        fakeData = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.MATCHED.name
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
    fun `M increment count W recordEvaluation()`(forge: Forge) {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, null)

        // When
        stats.recordEvaluation(fakeTimestamp + 1000, null)
        stats.recordEvaluation(fakeTimestamp + 2000, null)

        // Then
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        assertThat(event.evaluationCount).isEqualTo(3L)
    }

    @Test
    fun `M update last evaluation timestamp W recordEvaluation()`(forge: Forge) {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, null)
        val laterTimestamp = fakeTimestamp + 5000

        // When
        stats.recordEvaluation(laterTimestamp, null)

        // Then
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        assertThat(event.lastEvaluation).isEqualTo(laterTimestamp)
    }

    @Test
    fun `M preserve first evaluation timestamp W recordEvaluation() { multiple calls }`(forge: Forge) {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, null)

        // When
        repeat(10) { index ->
            stats.recordEvaluation(fakeTimestamp + (index + 1) * 1000, null)
        }

        // Then
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        assertThat(event.firstEvaluation).isEqualTo(fakeTimestamp)
    }

    @Test
    fun `M update last error message W recordEvaluation() { multiple errors same code }`(forge: Forge) {
        // Given
        val errorMessage1 = "First error: ${forge.anAlphabeticalString()}"
        val errorMessage2 = "Second error: ${forge.anAlphabeticalString()}"
        val errorMessage3 = "Third error: ${forge.anAlphabeticalString()}"
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, errorMessage1)

        // When
        stats.recordEvaluation(fakeTimestamp + 1000, errorMessage2)
        stats.recordEvaluation(fakeTimestamp + 2000, errorMessage3)

        // Then
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        assertThat(event.error?.message).isEqualTo(errorMessage3)
    }

    @Test
    fun `M keep aggregated count W recordEvaluation() { same error code different messages }`(forge: Forge) {
        // Given
        val errorMessage1 = "Error details: ${forge.anAlphabeticalString()}"
        val errorMessage2 = "Error details: ${forge.anAlphabeticalString()}"
        val errorMessage3 = "Error details: ${forge.anAlphabeticalString()}"
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, errorMessage1)

        // When
        stats.recordEvaluation(fakeTimestamp + 1000, errorMessage2)
        stats.recordEvaluation(fakeTimestamp + 2000, errorMessage3)

        // Then
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)
        assertThat(event.evaluationCount).isEqualTo(3L)
        assertThat(event.error?.message).isEqualTo(errorMessage3) // Last message
    }

    // endregion

    // region toEvaluationEvent

    @Test
    fun `M create event with correct fields W toEvaluationEvent() { successful match }`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, null)

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
    fun `M set runtime default true W toEvaluationEvent() { DEFAULT reason }`() {
        // Given
        val defaultData = UnparsedFlag(
            value = fakeValue,
            variationKey = null,
            allocationKey = null,
            reason = ResolutionReason.DEFAULT.name
        )
        val stats = AggregationStats(fakeTimestamp, fakeContext, defaultData, null)
        val keyWithoutVariant = fakeAggregationKey.copy(variantKey = null, allocationKey = null)

        // When
        val event = stats.toEvaluationEvent(fakeFlagName, keyWithoutVariant)

        // Then
        assertThat(event.runtimeDefaultUsed).isTrue()
        assertThat(event.variant).isNull()
        assertThat(event.allocation).isNull()
    }

    @Test
    fun `M set runtime default true W toEvaluationEvent() { ERROR reason }`(forge: Forge) {
        // Given
        val errorMessage = forge.anAlphabeticalString()
        val errorData = UnparsedFlag(
            value = fakeValue,
            variationKey = null,
            allocationKey = null,
            reason = ResolutionReason.ERROR.name
        )
        val stats = AggregationStats(fakeTimestamp, fakeContext, errorData, errorMessage)
        val keyWithError = fakeAggregationKey.copy(variantKey = null, allocationKey = null, errorCode = "FLAG_NOT_FOUND")

        // When
        val event = stats.toEvaluationEvent(fakeFlagName, keyWithError)

        // Then
        assertThat(event.runtimeDefaultUsed).isTrue()
        assertThat(event.error?.message).isEqualTo(errorMessage)
    }

    @Test
    fun `M set runtime default false W toEvaluationEvent() { MATCHED reason }`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, null)

        // When
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)

        // Then
        assertThat(event.runtimeDefaultUsed).isFalse()
    }

    @Test
    fun `M set runtime default false W toEvaluationEvent() { TARGETING_MATCH reason }`() {
        // Given
        val targetingData = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.TARGETING_MATCH.name
        )
        val stats = AggregationStats(fakeTimestamp, fakeContext, targetingData, null)

        // When
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)

        // Then
        assertThat(event.runtimeDefaultUsed).isFalse()
    }

    @Test
    fun `M set runtime default false W toEvaluationEvent() { unrecognized reason }`(forge: Forge) {
        // Given - unrecognized reasons are treated like normal matches (not DEFAULT/ERROR)
        val unrecognizedReason = forge.anAlphabeticalString()
        val unrecognizedData = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = unrecognizedReason
        )
        val stats = AggregationStats(fakeTimestamp, fakeContext, unrecognizedData, null)

        // When
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)

        // Then - unrecognized reasons should not set runtimeDefaultUsed for forward compatibility
        assertThat(event.runtimeDefaultUsed).isFalse()
    }

    @Test
    fun `M include error message W toEvaluationEvent() { error provided }`(forge: Forge) {
        // Given
        val errorMessage = forge.anAlphabeticalString()
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, errorMessage)

        // When
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)

        // Then
        assertThat(event.error?.message).isEqualTo(errorMessage)
    }

    @Test
    fun `M exclude error W toEvaluationEvent() { no error }`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, null)

        // When
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)

        // Then
        assertThat(event.error).isNull()
    }

    @Test
    fun `M use timestamp as first evaluation W toEvaluationEvent() { single evaluation }`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, null)

        // When
        val event = stats.toEvaluationEvent(fakeFlagName, fakeAggregationKey)

        // Then
        assertThat(event.timestamp).isEqualTo(fakeTimestamp)
        assertThat(event.timestamp).isEqualTo(event.firstEvaluation)
    }

    @Test
    fun `M use aggregation key fields W toEvaluationEvent() { key overrides data }`(forge: Forge) {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, null)
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
    fun `M handle null variant W toEvaluationEvent() { key has null variant }`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, null)
        val keyWithNullVariant = fakeAggregationKey.copy(variantKey = null)

        // When
        val event = stats.toEvaluationEvent(fakeFlagName, keyWithNullVariant)

        // Then
        assertThat(event.variant).isNull()
    }

    @Test
    fun `M handle null allocation W toEvaluationEvent() { key has null allocation }`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, null)
        val keyWithNullAllocation = fakeAggregationKey.copy(allocationKey = null)

        // When
        val event = stats.toEvaluationEvent(fakeFlagName, keyWithNullAllocation)

        // Then
        assertThat(event.allocation).isNull()
    }

    // endregion

    // region Thread Safety

    @Test
    fun `M handle concurrent recordEvaluation calls W recordEvaluation() { multiple threads }`() {
        // Given
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, null)
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
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, null)
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
        val stats = AggregationStats(fakeTimestamp, fakeContext, fakeData, null)
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
}
