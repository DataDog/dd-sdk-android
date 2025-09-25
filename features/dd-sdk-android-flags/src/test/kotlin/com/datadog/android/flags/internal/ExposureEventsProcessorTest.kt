/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.internal.model.ExposureEvent
import com.datadog.android.flags.internal.storage.RecordWriter
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ExposureEventsProcessorTest {

    @Mock
    lateinit var mockRecordWriter: RecordWriter

    @StringForgery
    lateinit var fakeFlagName: String

    @StringForgery
    lateinit var fakeTargetingKey: String

    @StringForgery
    lateinit var fakeAllocationKey: String

    @StringForgery
    lateinit var fakeVariationKey: String

    private lateinit var testedProcessor: ExposureEventsProcessor
    private lateinit var fakeFlag: PrecomputedFlag

    @BeforeEach
    fun `set up`(forge: Forge) {
        testedProcessor = ExposureEventsProcessor(mockRecordWriter)
        fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey
        )
    }

    // region processEvent

    @Test
    fun `M process first exposure W processEvent() { new key }`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf(
                "user_id" to forge.anAlphabeticalString(),
                "plan" to "premium",
                "age" to forge.anInt(min = 18, max = 99)
            )
        )

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag)

        // Then
        val eventCaptor = argumentCaptor<ExposureEvent>()
        verify(mockRecordWriter).write(eventCaptor.capture())

        val capturedEvent = eventCaptor.firstValue
        assertThat(capturedEvent.flag.key).isEqualTo(fakeFlagName)
        assertThat(capturedEvent.allocation.key).isEqualTo(fakeAllocationKey)
        assertThat(capturedEvent.variant.key).isEqualTo(fakeVariationKey)
        assertThat(capturedEvent.subject.id).isEqualTo(fakeTargetingKey)
        assertThat(capturedEvent.subject.attributes).containsKeys("user_id", "plan", "age")
        assertThat(capturedEvent.subject.attributes).hasSize(3)
        assertThat(capturedEvent.timeStamp).isGreaterThan(0)
    }

    @Test
    fun `M not process duplicate exposure W processEvent() { same flag and targeting key }`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag)
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag) // Duplicate

        // Then
        // Should only write once due to deduplication
        verify(mockRecordWriter, times(1)).write(any())
    }

    @Test
    fun `M process different exposures W processEvent() { different flag names }`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )
        val anotherFlagName = forge.anAlphabeticalString()

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag)
        testedProcessor.processEvent(anotherFlagName, fakeContext, fakeFlag)

        // Then
        // Should write both since they have different flag names
        verify(mockRecordWriter, times(2)).write(any())
    }

    @Test
    fun `M handle empty attributes W processEvent() { context with no attributes }`() {
        // Given
        val fakeContext = EvaluationContext(targetingKey = fakeTargetingKey)

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag)

        // Then
        val eventCaptor = argumentCaptor<ExposureEvent>()
        verify(mockRecordWriter).write(eventCaptor.capture())

        val capturedEvent = eventCaptor.firstValue
        assertThat(capturedEvent.subject.id).isEqualTo(fakeTargetingKey)
        assertThat(capturedEvent.subject.attributes).isEmpty() // No attributes
    }

    @Test
    fun `M process different exposures W processEvent() { different targeting keys }`(forge: Forge) {
        // Given
        val fakeContext1 = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )
        val fakeContext2 = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext1, fakeFlag)
        testedProcessor.processEvent(fakeFlagName, fakeContext2, fakeFlag)

        // Then
        // Should write both since they have different targeting keys
        verify(mockRecordWriter, times(2)).write(any())
    }

    @Test
    fun `M process different exposures W processEvent() { different allocation keys }`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )
        val fakeFlag2 = fakeFlag.copy(allocationKey = forge.anAlphabeticalString())

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag)
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag2)

        // Then
        // Should write both since they have different allocation keys
        verify(mockRecordWriter, times(2)).write(any())
    }

    @Test
    fun `M process different exposures W processEvent() { different variation keys }`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )
        val fakeFlag2 = fakeFlag.copy(variationKey = forge.anAlphabeticalString())

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag)
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag2)

        // Then
        // Should write both since they have different variation keys
        verify(mockRecordWriter, times(2)).write(any())
    }

    @Test
    fun `M convert attribute values to strings W processEvent() { various attribute types }`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf(
                "string_attr" to forge.anAlphabeticalString(),
                "int_attr" to forge.anInt(),
                "bool_attr" to forge.aBool(),
                "double_attr" to forge.aDouble()
            )
        )

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag)

        // Then
        val eventCaptor = argumentCaptor<ExposureEvent>()
        verify(mockRecordWriter).write(eventCaptor.capture())

        val capturedEvent = eventCaptor.firstValue
        // Verify all attribute values are converted to strings
        capturedEvent.subject.attributes.values.forEach { value ->
            assertThat(value).isInstanceOf(String::class.java)
        }
        assertThat(capturedEvent.subject.attributes).hasSize(4)
    }

    @Test
    fun `M handle special characters in cache key W processEvent() { keys with separator character }`(forge: Forge) {
        // Given
        val flagNameWithSeparator = "flag|name"
        val targetingKeyWithSeparator = "user|123"
        val allocationKeyWithSeparator = "allocation|key"
        val variationKeyWithSeparator = "variation|key"

        val fakeContext = EvaluationContext(
            targetingKey = targetingKeyWithSeparator,
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )
        val flagWithSeparators = fakeFlag.copy(
            allocationKey = allocationKeyWithSeparator,
            variationKey = variationKeyWithSeparator
        )

        // When
        testedProcessor.processEvent(flagNameWithSeparator, fakeContext, flagWithSeparators)
        testedProcessor.processEvent(flagNameWithSeparator, fakeContext, flagWithSeparators) // Duplicate

        // Then
        // Should only write once - deduplication should still work with separator characters
        verify(mockRecordWriter, times(1)).write(any())
    }

    @Test
    fun `M handle empty strings in parameters W processEvent() { empty flag name and keys }`() {
        // Given
        val emptyFlagName = ""
        val contextWithEmptyTargeting = EvaluationContext(
            targetingKey = "",
            attributes = mapOf("attr" to "value")
        )
        val flagWithEmptyKeys = fakeFlag.copy(
            allocationKey = "",
            variationKey = ""
        )

        // When
        testedProcessor.processEvent(emptyFlagName, contextWithEmptyTargeting, flagWithEmptyKeys)

        // Then
        val eventCaptor = argumentCaptor<ExposureEvent>()
        verify(mockRecordWriter).write(eventCaptor.capture())

        val capturedEvent = eventCaptor.firstValue
        assertThat(capturedEvent.flag.key).isEmpty()
        assertThat(capturedEvent.allocation.key).isEmpty()
        assertThat(capturedEvent.variant.key).isEmpty()
        assertThat(capturedEvent.subject.id).isEmpty()
    }

    @Test
    fun `M maintain separate cache entries W processEvent() { same parameters but different order }`() {
        // Given
        val context1 = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("attr1" to "value1", "attr2" to "value2")
        )
        val context2 = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("attr2" to "value2", "attr1" to "value1") // Same attributes, different order
        )

        // When
        testedProcessor.processEvent(fakeFlagName, context1, fakeFlag)
        testedProcessor.processEvent(fakeFlagName, context2, fakeFlag)

        // Then
        // Should only write once since cache key doesn't depend on attribute order
        // (cache key only uses targetingKey, flagName, allocationKey, variationKey)
        verify(mockRecordWriter, times(1)).write(any())
    }

    @Test
    fun `M generate consistent timestamps W processEvent() { multiple calls }`(forge: Forge) {
        // Given
        val fakeContext1 = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )
        val fakeContext2 = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )

        val beforeTime = System.currentTimeMillis()

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext1, fakeFlag)
        testedProcessor.processEvent(fakeFlagName, fakeContext2, fakeFlag)

        val afterTime = System.currentTimeMillis()

        // Then
        val eventCaptor = argumentCaptor<ExposureEvent>()
        verify(mockRecordWriter, times(2)).write(eventCaptor.capture())

        val capturedEvents = eventCaptor.allValues
        capturedEvents.forEach { event ->
            assertThat(event.timeStamp).isBetween(beforeTime, afterTime)
        }
    }

    // endregion
}
