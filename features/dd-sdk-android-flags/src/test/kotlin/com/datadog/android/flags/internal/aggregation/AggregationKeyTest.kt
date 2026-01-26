/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
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

    // region constructor

    @Test
    fun `M include variant and allocation W constructor() { all parameters provided }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)

        // When
        val key = AggregationKey(
            flagKey = fakeFlagName,
            variantKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            targetingKey = context.targetingKey
        )

        // Then
        assertThat(key.flagKey).isEqualTo(fakeFlagName)
        assertThat(key.variantKey).isEqualTo(fakeVariantKey)
        assertThat(key.allocationKey).isEqualTo(fakeAllocationKey)
        assertThat(key.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(key.errorCode).isNull()
    }

    @Test
    fun `M omit variant and allocation W constructor() { optional params not provided }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)

        // When
        val key = AggregationKey(
            flagKey = fakeFlagName,
            targetingKey = context.targetingKey
        )

        // Then
        assertThat(key.flagKey).isEqualTo(fakeFlagName)
        assertThat(key.variantKey).isNull()
        assertThat(key.allocationKey).isNull()
        assertThat(key.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(key.errorCode).isNull()
    }

    @Test
    fun `M include error code W constructor() { error code provided }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val errorCode = ErrorCode.FLAG_NOT_FOUND.name

        // When
        val key = AggregationKey(
            flagKey = fakeFlagName,
            targetingKey = context.targetingKey,
            errorCode = errorCode
        )

        // Then
        assertThat(key.flagKey).isEqualTo(fakeFlagName)
        assertThat(key.variantKey).isNull()
        assertThat(key.allocationKey).isNull()
        assertThat(key.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(key.errorCode).isEqualTo(errorCode)
    }

    @Test
    fun `M create different keys W constructor() { different error codes }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val errorCode1 = ErrorCode.PROVIDER_NOT_READY.name
        val errorCode2 = ErrorCode.TYPE_MISMATCH.name

        // When
        val key1 = AggregationKey(
            flagKey = fakeFlagName,
            targetingKey = context.targetingKey,
            errorCode = errorCode1
        )
        val key2 = AggregationKey(
            flagKey = fakeFlagName,
            targetingKey = context.targetingKey,
            errorCode = errorCode2
        )

        // Then - different error codes create different keys
        assertThat(key1).isNotEqualTo(key2)
    }

    @Test
    fun `M create different keys W constructor() { with variant vs without variant }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)

        // When
        val withVariantKey = AggregationKey(
            flagKey = fakeFlagName,
            variantKey = fakeVariantKey,
            allocationKey = fakeAllocationKey,
            targetingKey = context.targetingKey
        )
        val withoutVariantKey = AggregationKey(
            flagKey = fakeFlagName,
            targetingKey = context.targetingKey,
            errorCode = ErrorCode.FLAG_NOT_FOUND.name
        )

        // Then - with variant and without variant should create different keys
        assertThat(withVariantKey).isNotEqualTo(withoutVariantKey)
        assertThat(withVariantKey.variantKey).isNotNull()
        assertThat(withoutVariantKey.variantKey).isNull()
        assertThat(withVariantKey.errorCode).isNull()
        assertThat(withoutVariantKey.errorCode).isNotNull()
    }

    // endregion
}
