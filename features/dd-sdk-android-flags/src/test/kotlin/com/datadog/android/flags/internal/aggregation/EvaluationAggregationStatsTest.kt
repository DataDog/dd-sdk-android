/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
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

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EvaluationAggregationStatsTest {

    @LongForgery(min = 1000000L)
    var fakeFirstTimestamp = 0L

    @LongForgery(min = 1000000L)
    var fakeLastTimestamp = 0L

    @IntForgery(min = 1)
    var fakeCount = 0

    @StringForgery
    lateinit var fakeTargetingKey: String

    @StringForgery
    lateinit var fakeVariantKey: String

    @StringForgery
    lateinit var fakeAllocationKey: String

    @StringForgery
    lateinit var fakeFlagName: String

    @StringForgery
    lateinit var fakeApplicationId: String

    @StringForgery
    lateinit var fakeViewName: String

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    private lateinit var fakeContext: EvaluationContext
    private lateinit var fakeEvaluationAggregationKey: EvaluationAggregationKey

    @BeforeEach
    fun `set up`() {
        fakeContext = EvaluationContext(targetingKey = fakeTargetingKey)
        fakeEvaluationAggregationKey = EvaluationAggregationKey(
            flagKey = fakeFlagName,
            variantKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            targetingKey = fakeTargetingKey,
            viewName = fakeViewName,
            errorCode = null
        )
        // Ensure lastTimestamp >= firstTimestamp
        if (fakeLastTimestamp < fakeFirstTimestamp) {
            fakeLastTimestamp = fakeFirstTimestamp + 1000
        }
    }

    // region toEvaluationEvent

    @Test
    fun `M create event with correct fields W toEvaluationEvent() { successful match }`() {
        // Given
        val stats = createStats()

        // When
        val event = stats.toEvaluationEvent(fakeDatadogContext)

        // Then
        assertThat(event.timestamp).isEqualTo(fakeFirstTimestamp)
        assertThat(event.flag.key).isEqualTo(fakeFlagName)
        assertThat(event.variant?.key).isEqualTo(fakeVariantKey)
        assertThat(event.allocation?.key).isEqualTo(fakeAllocationKey)
        assertThat(event.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(event.evaluationCount).isEqualTo(fakeCount.toLong())
        assertThat(event.firstEvaluation).isEqualTo(fakeFirstTimestamp)
        assertThat(event.lastEvaluation).isEqualTo(fakeLastTimestamp)
        assertThat(event.runtimeDefaultUsed).isFalse()
        assertThat(event.error).isNull()
        assertThat(event.targetingRule).isNull()
        assertThat(event.context).isNotNull()
        assertThat(event.context?.dd?.service).isEqualTo(fakeDatadogContext.service)
        assertThat(event.context?.dd?.rum?.application?.id).isEqualTo(fakeApplicationId)
        assertThat(event.context?.dd?.rum?.view?.url).isEqualTo(fakeViewName)
    }

    @Test
    fun `M set runtime default true W toEvaluationEvent() { null variantKey }`() {
        // Given - null variantKey indicates runtime default was used
        val keyWithoutVariant = fakeEvaluationAggregationKey.copy(variantKey = null, allocationKey = null)
        val stats = createStats(evaluationAggregationKey = keyWithoutVariant)

        // When
        val event = stats.toEvaluationEvent(fakeDatadogContext)

        // Then
        assertThat(event.runtimeDefaultUsed).isTrue()
        assertThat(event.variant).isNull()
        assertThat(event.allocation).isNull()
    }

    @Test
    fun `M set runtime default true W toEvaluationEvent() { ERROR reason }`(forge: Forge) {
        // Given - ERROR reason (reason = null, no flag data)
        val errorMessage = forge.anAlphabeticalString()
        val keyWithError = fakeEvaluationAggregationKey.copy(
            variantKey = null,
            allocationKey = null,
            errorCode = "FLAG_NOT_FOUND"
        )
        val stats = createStats(
            evaluationAggregationKey = keyWithError,
            errorMessage = errorMessage
        )

        // When
        val event = stats.toEvaluationEvent(fakeDatadogContext)

        // Then
        assertThat(event.runtimeDefaultUsed).isTrue()
        assertThat(event.error?.message).isEqualTo(errorMessage)
    }

    @Test
    fun `M set runtime default false W toEvaluationEvent() { MATCHED reason }`() {
        // Given
        val stats = createStats()

        // When
        val event = stats.toEvaluationEvent(fakeDatadogContext)

        // Then
        assertThat(event.runtimeDefaultUsed).isFalse()
    }

    @Test
    fun `M set runtime default false W toEvaluationEvent() { non-null variantKey }`() {
        // Given - non-null variantKey indicates a variant was assigned
        val stats = createStats() // fakeAggregationKey has non-null variantKey

        // When
        val event = stats.toEvaluationEvent(fakeDatadogContext)

        // Then
        assertThat(event.runtimeDefaultUsed).isFalse()
    }

    @Test
    fun `M include error message W toEvaluationEvent() { error provided }`(forge: Forge) {
        // Given
        val errorMessage = forge.anAlphabeticalString()
        val stats = createStats(errorMessage = errorMessage)

        // When
        val event = stats.toEvaluationEvent(fakeDatadogContext)

        // Then
        assertThat(event.error?.message).isEqualTo(errorMessage)
    }

    @Test
    fun `M omit error W toEvaluationEvent() { no error }`() {
        // Given
        val stats = createStats(errorMessage = null)

        // When
        val event = stats.toEvaluationEvent(fakeDatadogContext)

        // Then
        assertThat(event.error).isNull()
    }

    @Test
    fun `M use aggregation key fields W toEvaluationEvent() { key fields provided }`() {
        // Given
        val stats = createStats()

        // When
        val event = stats.toEvaluationEvent(fakeDatadogContext)

        // Then
        assertThat(event.variant?.key).isEqualTo(fakeEvaluationAggregationKey.variantKey)
        assertThat(event.allocation?.key).isEqualTo(fakeEvaluationAggregationKey.allocationKey)
        assertThat(event.targetingKey).isEqualTo(fakeEvaluationAggregationKey.targetingKey)
    }

    @Test
    fun `M handle null variant W toEvaluationEvent() { key has null variant }`() {
        // Given
        val keyWithNullVariant = fakeEvaluationAggregationKey.copy(variantKey = null)
        val stats = createStats(evaluationAggregationKey = keyWithNullVariant)

        // When
        val event = stats.toEvaluationEvent(fakeDatadogContext)

        // Then
        assertThat(event.variant).isNull()
    }

    @Test
    fun `M handle null allocation W toEvaluationEvent() { key has null allocation }`() {
        // Given
        val keyWithNullAllocation = fakeEvaluationAggregationKey.copy(allocationKey = null)
        val stats = createStats(evaluationAggregationKey = keyWithNullAllocation)

        // When
        val event = stats.toEvaluationEvent(fakeDatadogContext)

        // Then
        assertThat(event.allocation).isNull()
    }

    @Test
    fun `M use firstEvaluation as timestamp W toEvaluationEvent()`() {
        // Given
        val stats = createStats()

        // When
        val event = stats.toEvaluationEvent(fakeDatadogContext)

        // Then
        assertThat(event.timestamp).isEqualTo(fakeFirstTimestamp)
        assertThat(event.timestamp).isEqualTo(event.firstEvaluation)
    }

    @Test
    fun `M include correct count W toEvaluationEvent()`() {
        // Given
        val stats = createStats(count = 42)

        // When
        val event = stats.toEvaluationEvent(fakeDatadogContext)

        // Then
        assertThat(event.evaluationCount).isEqualTo(42L)
    }

    @Test
    fun `M handle null RUM application ID W toEvaluationEvent()`() {
        // Given
        val stats = createStats(rumApplicationId = null)

        // When
        val event = stats.toEvaluationEvent(fakeDatadogContext)

        // Then
        assertThat(event.context?.dd?.rum).isNull()
    }

    @Test
    fun `M handle null view name W toEvaluationEvent() { RUM app ID present }`() {
        // Given
        val keyWithoutView = fakeEvaluationAggregationKey.copy(viewName = null)
        val stats = createStats(evaluationAggregationKey = keyWithoutView)

        // When
        val event = stats.toEvaluationEvent(fakeDatadogContext)

        // Then
        assertThat(event.context?.dd?.rum?.application?.id).isEqualTo(fakeApplicationId)
        assertThat(event.context?.dd?.rum?.view).isNull()
    }

    // endregion

    // region copy (immutability)

    @Test
    fun `M create new instance W copy() { count updated }`() {
        // Given
        val original = createStats(count = 1)

        // When
        val updated = original.copy(count = 5)

        // Then - original unchanged
        assertThat(original.count).isEqualTo(1)
        assertThat(updated.count).isEqualTo(5)
    }

    @Test
    fun `M create new instance W copy() { lastEvaluation updated }`() {
        // Given
        val original = createStats(lastEvaluation = 1000L)

        // When
        val updated = original.copy(lastEvaluation = 5000L)

        // Then - original unchanged
        assertThat(original.lastEvaluation).isEqualTo(1000L)
        assertThat(updated.lastEvaluation).isEqualTo(5000L)
    }

    @Test
    fun `M create new instance W copy() { errorMessage updated }`() {
        // Given
        val original = createStats(errorMessage = "first error")

        // When
        val updated = original.copy(errorMessage = "second error")

        // Then - original unchanged
        assertThat(original.errorMessage).isEqualTo("first error")
        assertThat(updated.errorMessage).isEqualTo("second error")
    }

    // endregion

    // region Helpers

    private fun createStats(
        evaluationAggregationKey: EvaluationAggregationKey = fakeEvaluationAggregationKey,
        count: Int = fakeCount,
        firstEvaluation: Long = fakeFirstTimestamp,
        lastEvaluation: Long = fakeLastTimestamp,
        context: EvaluationContext = fakeContext,
        rumApplicationId: String? = fakeApplicationId,
        errorMessage: String? = null
    ): EvaluationAggregationStats = EvaluationAggregationStats(
        aggregationKey = evaluationAggregationKey,
        count = count,
        firstEvaluation = firstEvaluation,
        lastEvaluation = lastEvaluation,
        context = context,
        rumApplicationId = rumApplicationId,
        errorMessage = errorMessage
    )

    // endregion
}
