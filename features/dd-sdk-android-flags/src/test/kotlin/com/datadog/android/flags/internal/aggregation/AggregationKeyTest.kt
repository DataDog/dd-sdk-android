/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.android.flags.model.UnparsedFlag
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class AggregationKeyTest {

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

    // region fromEvaluation

    @Test
    fun `M create aggregation key W fromEvaluation() { successful match }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val data = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.MATCHED.name
        )

        // When
        val key = AggregationKey.fromEvaluation(fakeFlagName, context, data, null)

        // Then
        assertThat(key.flagKey).isEqualTo(fakeFlagName)
        assertThat(key.variantKey).isEqualTo(fakeVariantKey)
        assertThat(key.allocationKey).isEqualTo(fakeAllocationKey)
        assertThat(key.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(key.errorCode).isNull()
    }

    @Test
    fun `M omit variant and allocation W fromEvaluation() { DEFAULT reason }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val data = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.DEFAULT.name
        )

        // When
        val key = AggregationKey.fromEvaluation(fakeFlagName, context, data, null)

        // Then
        assertThat(key.flagKey).isEqualTo(fakeFlagName)
        assertThat(key.variantKey).isNull()
        assertThat(key.allocationKey).isNull()
        assertThat(key.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(key.errorCode).isNull()
    }

    @Test
    fun `M omit variant and allocation W fromEvaluation() { ERROR reason }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val data = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.ERROR.name
        )

        // When
        val key = AggregationKey.fromEvaluation(fakeFlagName, context, data, ErrorCode.FLAG_NOT_FOUND.name)

        // Then
        assertThat(key.flagKey).isEqualTo(fakeFlagName)
        assertThat(key.variantKey).isNull()
        assertThat(key.allocationKey).isNull()
        assertThat(key.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(key.errorCode).isEqualTo(ErrorCode.FLAG_NOT_FOUND.name)
    }

    @Test
    fun `M include variant and allocation W fromEvaluation() { MATCHED reason }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val data = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.MATCHED.name
        )

        // When
        val key = AggregationKey.fromEvaluation(fakeFlagName, context, data, null)

        // Then
        assertThat(key.variantKey).isEqualTo(fakeVariantKey)
        assertThat(key.allocationKey).isEqualTo(fakeAllocationKey)
    }

    @Test
    fun `M include variant and allocation W fromEvaluation() { TARGETING_MATCH reason }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val data = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.TARGETING_MATCH.name
        )

        // When
        val key = AggregationKey.fromEvaluation(fakeFlagName, context, data, null)

        // Then
        assertThat(key.variantKey).isEqualTo(fakeVariantKey)
        assertThat(key.allocationKey).isEqualTo(fakeAllocationKey)
    }

    @Test
    fun `M default to ERROR W fromEvaluation() { invalid reason }`(forge: Forge) {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val invalidReason = forge.anAlphabeticalString()
        val data = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = invalidReason
        )

        // When
        val key = AggregationKey.fromEvaluation(fakeFlagName, context, data, null)

        // Then
        assertThat(key.variantKey).isNull()
        assertThat(key.allocationKey).isNull()
    }

    @Test
    fun `M include error code W fromEvaluation() { error provided }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val data = UnparsedFlag(
            value = fakeValue,
            variationKey = null,
            allocationKey = null,
            reason = ResolutionReason.ERROR.name
        )
        val errorCode = ErrorCode.PROVIDER_NOT_READY.name

        // When
        val key = AggregationKey.fromEvaluation(fakeFlagName, context, data, errorCode)

        // Then
        assertThat(key.errorCode).isEqualTo(errorCode)
    }

    @Test
    fun `M set null error code W fromEvaluation() { no error }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val data = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.MATCHED.name
        )

        // When
        val key = AggregationKey.fromEvaluation(fakeFlagName, context, data, null)

        // Then
        assertThat(key.errorCode).isNull()
    }

    // endregion

    // region Equality and Aggregation

    @Test
    fun `M create same keys W fromEvaluation() { identical evaluations }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val data = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.MATCHED.name
        )

        // When
        val key1 = AggregationKey.fromEvaluation(fakeFlagName, context, data, null)
        val key2 = AggregationKey.fromEvaluation(fakeFlagName, context, data, null)

        // Then
        assertThat(key1).isEqualTo(key2)
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode())
    }

    @Test
    fun `M create different keys W fromEvaluation() { different flag names }`(forge: Forge) {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val data = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.MATCHED.name
        )
        val anotherFlagName = forge.anAlphabeticalString()

        // When
        val key1 = AggregationKey.fromEvaluation(fakeFlagName, context, data, null)
        val key2 = AggregationKey.fromEvaluation(anotherFlagName, context, data, null)

        // Then
        assertThat(key1).isNotEqualTo(key2)
    }

    @Test
    fun `M create different keys W fromEvaluation() { different targeting keys }`(forge: Forge) {
        // Given
        val context1 = EvaluationContext(targetingKey = fakeTargetingKey)
        val context2 = EvaluationContext(targetingKey = forge.anAlphabeticalString())
        val data = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.MATCHED.name
        )

        // When
        val key1 = AggregationKey.fromEvaluation(fakeFlagName, context1, data, null)
        val key2 = AggregationKey.fromEvaluation(fakeFlagName, context2, data, null)

        // Then
        assertThat(key1).isNotEqualTo(key2)
    }

    @Test
    fun `M create different keys W fromEvaluation() { different variants }`(forge: Forge) {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val data1 = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.MATCHED.name
        )
        val data2 = UnparsedFlag(
            value = fakeValue,
            variationKey = forge.anAlphabeticalString(),
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.MATCHED.name
        )

        // When
        val key1 = AggregationKey.fromEvaluation(fakeFlagName, context, data1, null)
        val key2 = AggregationKey.fromEvaluation(fakeFlagName, context, data2, null)

        // Then
        assertThat(key1).isNotEqualTo(key2)
    }

    @Test
    fun `M create different keys W fromEvaluation() { different allocations }`(forge: Forge) {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val data1 = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.MATCHED.name
        )
        val data2 = UnparsedFlag(
            value = fakeValue,
            variationKey = fakeVariantKey,
            allocationKey = forge.anAlphabeticalString(),
            reason = ResolutionReason.MATCHED.name
        )

        // When
        val key1 = AggregationKey.fromEvaluation(fakeFlagName, context, data1, null)
        val key2 = AggregationKey.fromEvaluation(fakeFlagName, context, data2, null)

        // Then
        assertThat(key1).isNotEqualTo(key2)
    }

    @Test
    fun `M create different keys W fromEvaluation() { different error codes }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val data = UnparsedFlag(
            value = fakeValue,
            variationKey = null,
            allocationKey = null,
            reason = ResolutionReason.ERROR.name
        )
        val errorCode1 = ErrorCode.FLAG_NOT_FOUND.name
        val errorCode2 = ErrorCode.PROVIDER_NOT_READY.name

        // When
        val key1 = AggregationKey.fromEvaluation(fakeFlagName, context, data, errorCode1)
        val key2 = AggregationKey.fromEvaluation(fakeFlagName, context, data, errorCode2)

        // Then
        assertThat(key1).isNotEqualTo(key2)
    }

    @Test
    fun `M create same keys W fromEvaluation() { same error code different messages }`() {
        // Given - error messages don't affect aggregation
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val data = UnparsedFlag(
            value = fakeValue,
            variationKey = null,
            allocationKey = null,
            reason = ResolutionReason.ERROR.name
        )
        val errorCode = ErrorCode.TYPE_MISMATCH.name

        // When
        val key1 = AggregationKey.fromEvaluation(fakeFlagName, context, data, errorCode)
        val key2 = AggregationKey.fromEvaluation(fakeFlagName, context, data, errorCode)

        // Then
        assertThat(key1).isEqualTo(key2)
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode())
    }

    @Test
    fun `M ignore flag value W fromEvaluation() { different values same key }`(forge: Forge) {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val data1 = UnparsedFlag(
            value = forge.anAlphabeticalString(),
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.MATCHED.name
        )
        val data2 = UnparsedFlag(
            value = forge.anAlphabeticalString(),
            variationKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            reason = ResolutionReason.MATCHED.name
        )

        // When
        val key1 = AggregationKey.fromEvaluation(fakeFlagName, context, data1, null)
        val key2 = AggregationKey.fromEvaluation(fakeFlagName, context, data2, null)

        // Then - value is not part of aggregation key
        assertThat(key1).isEqualTo(key2)
    }

    // endregion
}
