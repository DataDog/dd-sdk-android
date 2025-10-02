/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.model

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DatadogEvaluationContextTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M create normalized context W from() { valid attributes only }`(forge: Forge) {
        // Given
        val targetingKey = forge.anAlphabeticalString()
        val stringValue = forge.anAlphabeticalString()
        val numberValue = forge.anInt()
        val booleanValue = forge.aBool()

        val publicContext = EvaluationContext(
            targetingKey = targetingKey,
            attributes = mapOf(
                "stringAttr" to stringValue,
                "numberAttr" to numberValue,
                "booleanAttr" to booleanValue
            )
        )

        // When
        val ddEvalContext = DatadogEvaluationContext.from(publicContext, mockInternalLogger)

        // Then
        checkNotNull(ddEvalContext)
        assertThat(ddEvalContext.targetingKey).isEqualTo(targetingKey)
        assertThat(ddEvalContext.attributes).isEqualTo(
            mapOf(
                "stringAttr" to stringValue,
                "numberAttr" to numberValue.toString(),
                "booleanAttr" to booleanValue.toString()
            )
        )
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M filter unsupported types and log warnings W from() { mixed valid and invalid attributes }`(forge: Forge) {
        // Given
        val targetingKey = forge.anAlphabeticalString()
        val validStringValue = forge.anAlphabeticalString()
        val validNumberValue = forge.anInt()
        val invalidListValue = listOf(1, 2, 3)
        val invalidMapValue = mapOf("nested" to "not supported")

        val publicContext = EvaluationContext(
            targetingKey = targetingKey,
            attributes = mapOf(
                "validString" to validStringValue,
                "validNumber" to validNumberValue,
                "invalidList" to invalidListValue,
                "invalidMap" to invalidMapValue
            )
        )

        // When
        val ddEvalContext = DatadogEvaluationContext.from(publicContext, mockInternalLogger)

        // Then
        // Only valid attributes should be present
        checkNotNull(ddEvalContext)
        assertThat(ddEvalContext.attributes).isEqualTo(
            mapOf(
                "validString" to validStringValue,
                "validNumber" to validNumberValue.toString()
            )
        )

        // Warnings should be logged for invalid types
        verify(mockInternalLogger, times(2)).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any<() -> String>(),
            anyOrNull<Throwable>(),
            any<Boolean>(),
            anyOrNull<Map<String, Any?>>()
        )
    }

    @Test
    fun `M filter null values and log warnings W from() { attributes with null values }`(forge: Forge) {
        // Given
        val targetingKey = forge.anAlphabeticalString()
        val validValue = forge.anAlphabeticalString()

        val publicContext = EvaluationContext(
            targetingKey = targetingKey,
            attributes = mapOf(
                "validAttr" to validValue,
                "nullAttr" to null
            )
        )

        // When
        val ddEvalContext = DatadogEvaluationContext.from(publicContext, mockInternalLogger)

        // Then
        // Only non-null attributes should be present
        checkNotNull(ddEvalContext)
        assertThat(ddEvalContext.attributes).isEqualTo(
            mapOf(
                "validAttr" to validValue
            )
        )

        // Warning should be logged for null value
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any<() -> String>(),
            anyOrNull<Throwable>(),
            any<Boolean>(),
            anyOrNull<Map<String, Any?>>()
        )
    }

    @Test
    fun `M handle empty attributes W from() { empty attributes map }`(forge: Forge) {
        // Given
        val targetingKey = forge.anAlphabeticalString()
        val publicContext = EvaluationContext(targetingKey, emptyMap())

        // When
        val ddEvalContext = DatadogEvaluationContext.from(publicContext, mockInternalLogger)

        // Then
        checkNotNull(ddEvalContext)
        assertThat(ddEvalContext.targetingKey).isEqualTo(targetingKey)
        assertThat(ddEvalContext.attributes).isEmpty()
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M stringifies numbers correctly W from() { different number types }`(forge: Forge) {
        // Given
        val targetingKey = forge.anAlphabeticalString()
        val intValue = forge.anInt()
        val doubleValue = forge.aDouble()
        val longValue = forge.aLong()
        val floatValue = forge.aFloat()

        val publicContext = EvaluationContext(
            targetingKey = targetingKey,
            attributes = mapOf(
                "intAttr" to intValue,
                "doubleAttr" to doubleValue,
                "longAttr" to longValue,
                "floatAttr" to floatValue
            )
        )

        // When
        val ddEvalContext = DatadogEvaluationContext.from(publicContext, mockInternalLogger)

        // Then
        checkNotNull(ddEvalContext)
        assertThat(ddEvalContext).isNotNull
        assertThat(ddEvalContext.attributes).isEqualTo(
            mapOf(
                "intAttr" to intValue.toString(),
                "doubleAttr" to doubleValue.toString(),
                "longAttr" to longValue.toString(),
                "floatAttr" to floatValue.toString()
            )
        )
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return null W from() { blank targeting key }`() {
        // Given
        val blankTargetingKey = ""
        val publicContext = EvaluationContext(blankTargetingKey, emptyMap())

        // When
        val ddEvalContext = DatadogEvaluationContext.from(publicContext, mockInternalLogger)

        // Then
        assertThat(ddEvalContext).isNull()
    }

    @Test
    fun `M return null W from() { whitespace-only targeting key }`() {
        // Given
        val whitespaceTargetingKey = "   "
        val publicContext = EvaluationContext(whitespaceTargetingKey, emptyMap())

        // When
        val ddEvalContext = DatadogEvaluationContext.from(publicContext, mockInternalLogger)

        // Then
        assertThat(ddEvalContext).isNull()
    }

    @Test
    fun `M return null W from() { tab-only targeting key }`() {
        // Given
        val tabTargetingKey = "\t\t"
        val publicContext = EvaluationContext(tabTargetingKey, emptyMap())

        // When
        val ddEvalContext = DatadogEvaluationContext.from(publicContext, mockInternalLogger)

        // Then
        assertThat(ddEvalContext).isNull()
    }

    @Test
    fun `M return true W isValid() { valid context }`() {
        // Given When
        val ddEvalContext = DatadogEvaluationContext("user123", mapOf("plan" to "premium"))

        // Then
        assertThat(ddEvalContext.isValid()).isTrue()
    }

    @Test
    fun `M return null W from() { blank targeting key and log warning }`() {
        // Given
        val publicContext = EvaluationContext("", mapOf("plan" to "premium"))

        // When
        val ddEvalContext = DatadogEvaluationContext.from(publicContext, mockInternalLogger)

        // Then
        assertThat(ddEvalContext).isNull()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any<() -> String>(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M return null W from() { whitespace-only targeting key and log warning }`() {
        // Given
        val publicContext = EvaluationContext("   ", mapOf("plan" to "premium"))

        // When
        val ddEvalContext = DatadogEvaluationContext.from(publicContext, mockInternalLogger)

        // Then
        assertThat(ddEvalContext).isNull()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any<() -> String>(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M return true W isValid() { valid context with multiple attributes }`() {
        // Given When
        val ddEvalContext = DatadogEvaluationContext.from(
            EvaluationContext(
                "user123",
                mapOf(
                    "plan" to "premium",
                    "region" to "us-east-1",
                    "feature" to "enabled"
                )
            ),
            mockInternalLogger
        )

        // Then
        assertThat(ddEvalContext!!.isValid()).isTrue()
    }

    @Test
    fun `M return false W isValid() { context with blank targeting key }`() {
        // Given
        val invalidContext = DatadogEvaluationContext("", mapOf("plan" to "premium"))

        // When
        val isValid = invalidContext.isValid()

        // Then
        assertThat(isValid).isFalse()
    }

    @Test
    fun `M return false W isValid() { context with whitespace targeting key }`() {
        // Given
        val invalidContext = DatadogEvaluationContext("   ", mapOf("plan" to "premium"))

        // When
        val isValid = invalidContext.isValid()

        // Then
        assertThat(isValid).isFalse()
    }
}
