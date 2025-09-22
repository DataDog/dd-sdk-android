/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.model

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class EvaluationContextTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`(forge: Forge) {
        ForgeConfigurator.configure(forge)
    }

    // region Data Class Properties

    @Test
    fun `M create context W constructor() { targeting key and attributes }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeAttributes = mapOf(
            "plan" to forge.anAlphabeticalString(),
            "user_id" to forge.anInt()
        )

        // When
        val context = EvaluationContext(fakeTargetingKey, fakeAttributes)

        // Then
        assertThat(context.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(context.attributes).isEqualTo(fakeAttributes)
    }

    @Test
    fun `M create context with empty attributes W constructor() { targeting key only }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()

        // When
        val context = EvaluationContext(fakeTargetingKey)

        // Then
        assertThat(context.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(context.attributes).isEmpty()
    }

    // endregion

    // region Builder - addAttribute()

    @Test
    fun `M add attribute W addAttribute() { string value }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeKey = forge.anAlphabeticalString()
        val fakeValue = forge.anAlphabeticalString()

        // When
        val context = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)
            .addAttribute(fakeKey, fakeValue)
            .build()

        // Then
        assertThat(context.attributes[fakeKey]).isEqualTo(fakeValue)
        verify(mockInternalLogger, times(0)).log(
            any<InternalLogger.Level>(),
            any<InternalLogger.Target>(),
            any<() -> String>(),
            any(),
            any(),
            any()
        )
    }

    @Test
    fun `M add attribute W addAttribute() { number value }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeKey = forge.anAlphabeticalString()
        val fakeValue = forge.anInt()

        // When
        val context = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)
            .addAttribute(fakeKey, fakeValue)
            .build()

        // Then
        assertThat(context.attributes[fakeKey]).isEqualTo(fakeValue)
        verify(mockInternalLogger, times(0)).log(
            any<InternalLogger.Level>(),
            any<InternalLogger.Target>(),
            any<() -> String>(),
            any(),
            any(),
            any()
        )
    }

    @Test
    fun `M add attribute W addAttribute() { boolean value }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeKey = forge.anAlphabeticalString()
        val fakeValue = forge.aBool()

        // When
        val context = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)
            .addAttribute(fakeKey, fakeValue)
            .build()

        // Then
        assertThat(context.attributes[fakeKey]).isEqualTo(fakeValue)
        verify(mockInternalLogger, times(0)).log(
            any<InternalLogger.Level>(),
            any<InternalLogger.Target>(),
            any<() -> String>(),
            any(),
            any(),
            any()
        )
    }

    @Test
    fun `M log warning and skip W addAttribute() { unsupported list type }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeKey = forge.anAlphabeticalString()
        val unsupportedValue = listOf("not", "supported")

        // When
        val context = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)
            .addAttribute(fakeKey, unsupportedValue)
            .build()

        // Then
        assertThat(context.attributes).doesNotContainKey(fakeKey)

        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                eq(null),
                eq(false),
                eq(null)
            )
            val message = lastValue()
            assertThat(message).contains(fakeKey)
            assertThat(message).contains("ArrayList")
            assertThat(message).contains("Only String, Number, and Boolean are supported")
        }
    }

    @Test
    fun `M log warning and skip W addAttribute() { unsupported map type }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeKey = forge.anAlphabeticalString()
        val unsupportedValue = mapOf("not" to "supported")

        // When
        val context = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)
            .addAttribute(fakeKey, unsupportedValue)
            .build()

        // Then
        assertThat(context.attributes).doesNotContainKey(fakeKey)
        // Note: Detailed log verification can be complex due to method overloads
        // Just verify that a warning was logged
        verify(mockInternalLogger, times(1)).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any<() -> String>(),
            eq<Throwable?>(null),
            eq(false),
            eq<Map<String, Any?>?>(null)
        )
    }

    @Test
    fun `M log warning and skip W addAttribute() { custom object type }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeKey = forge.anAlphabeticalString()
        val customObject = Any()

        // When
        val context = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)
            .addAttribute(fakeKey, customObject)
            .build()

        // Then
        assertThat(context.attributes).doesNotContainKey(fakeKey)
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any<() -> String>(),
            eq<Throwable?>(null),
            eq(false),
            eq<Map<String, Any?>?>(null)
        )
    }

    // endregion

    // region Builder - addAll()

    @Test
    fun `M add all valid attributes W addAll() { supported types only }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val validAttributes = mapOf(
            "string_attr" to forge.anAlphabeticalString(),
            "int_attr" to forge.anInt(),
            "double_attr" to forge.aDouble(),
            "boolean_attr" to forge.aBool()
        )

        // When
        val context = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)
            .addAll(validAttributes)
            .build()

        // Then
        assertThat(context.attributes).containsExactlyInAnyOrderEntriesOf(validAttributes)
        verify(mockInternalLogger, times(0)).log(
            any<InternalLogger.Level>(),
            any<InternalLogger.Target>(),
            any<() -> String>(),
            any(),
            any(),
            any()
        )
    }

    @Test
    fun `M filter invalid attributes W addAll() { mixed valid and invalid types }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val validAttributes = mapOf(
            "string_attr" to forge.anAlphabeticalString(),
            "number_attr" to forge.anInt(),
            "boolean_attr" to forge.aBool()
        )
        val invalidAttributes = mapOf(
            "invalid_list" to listOf(1, 2, 3),
            "invalid_map" to mapOf("not" to "supported"),
            "invalid_object" to Any()
        )
        val allAttributes = validAttributes + invalidAttributes

        // When
        val context = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)
            .addAll(allAttributes)
            .build()

        // Then
        assertThat(context.attributes).containsExactlyInAnyOrderEntriesOf(validAttributes)
        assertThat(context.attributes).doesNotContainKeys("invalid_list", "invalid_map", "invalid_object")

        // Verify warnings logged for each invalid attribute
        verify(mockInternalLogger, times(3)).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M handle empty map W addAll() { empty attributes }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val emptyAttributes = emptyMap<String, Any>()

        // When
        val context = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)
            .addAll(emptyAttributes)
            .build()

        // Then
        assertThat(context.attributes).isEmpty()
        verify(mockInternalLogger, times(0)).log(
            any<InternalLogger.Level>(),
            any<InternalLogger.Target>(),
            any<() -> String>(),
            any(),
            any(),
            any()
        )
    }

    // endregion

    // region Builder - build() validation

    @Test
    fun `M build successfully W build() { valid targeting key }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()

        // When
        val context = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)
            .build()

        // Then
        assertThat(context.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(context.attributes).isEmpty()
    }

    @Test
    fun `M throw IllegalArgumentException W build() { blank targeting key }`() {
        // Given
        val blankTargetingKey = ""

        // When & Then
        assertThatThrownBy {
            EvaluationContext.builder(blankTargetingKey, mockInternalLogger)
                .build()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Targeting key cannot be blank")
    }

    @Test
    fun `M throw IllegalArgumentException W build() { whitespace-only targeting key }`() {
        // Given
        val whitespaceTargetingKey = "   "

        // When & Then
        assertThatThrownBy {
            EvaluationContext.builder(whitespaceTargetingKey, mockInternalLogger)
                .build()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Targeting key cannot be blank")
    }

    @Test
    fun `M throw IllegalArgumentException W build() { tab-only targeting key }`() {
        // Given
        val tabTargetingKey = "\t\t"

        // When & Then
        assertThatThrownBy {
            EvaluationContext.builder(tabTargetingKey, mockInternalLogger)
                .build()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Targeting key cannot be blank")
    }

    // endregion

    // region Builder Chaining

    @Test
    fun `M support method chaining W builder() { fluent interface }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeAttributes = mapOf(
            "attr1" to forge.anAlphabeticalString(),
            "attr2" to forge.anInt()
        )

        // When
        val context = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)
            .addAttribute("single", "value")
            .addAll(fakeAttributes)
            .addAttribute("another", 42)
            .build()

        // Then
        assertThat(context.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(context.attributes["single"]).isEqualTo("value")
        assertThat(context.attributes["another"]).isEqualTo(42)
        assertThat(context.attributes).containsAllEntriesOf(fakeAttributes)
    }

    // endregion

    // region Companion Object - isSupportedAttributeType()

    @Test
    fun `M return true W isSupportedAttributeType() { string value }`(forge: Forge) {
        // Given
        val stringValue = forge.anAlphabeticalString()

        // When
        val result = EvaluationContext.isSupportedAttributeType(stringValue)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return true W isSupportedAttributeType() { number values }`(forge: Forge) {
        // Given & When & Then
        assertThat(EvaluationContext.isSupportedAttributeType(forge.anInt())).isTrue()
        assertThat(EvaluationContext.isSupportedAttributeType(forge.aLong())).isTrue()
        assertThat(EvaluationContext.isSupportedAttributeType(forge.aDouble())).isTrue()
        assertThat(EvaluationContext.isSupportedAttributeType(forge.aFloat())).isTrue()
    }

    @Test
    fun `M return true W isSupportedAttributeType() { boolean value }`(forge: Forge) {
        // Given
        val booleanValue = forge.aBool()

        // When
        val result = EvaluationContext.isSupportedAttributeType(booleanValue)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W isSupportedAttributeType() { list value }`(forge: Forge) {
        // Given
        val listValue = listOf(forge.anAlphabeticalString(), forge.anInt())

        // When
        val result = EvaluationContext.isSupportedAttributeType(listValue)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W isSupportedAttributeType() { map value }`(forge: Forge) {
        // Given
        val mapValue = mapOf("key" to forge.anAlphabeticalString())

        // When
        val result = EvaluationContext.isSupportedAttributeType(mapValue)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W isSupportedAttributeType() { array value }`(forge: Forge) {
        // Given
        val arrayValue = arrayOf(forge.anAlphabeticalString(), forge.anInt())

        // When
        val result = EvaluationContext.isSupportedAttributeType(arrayValue)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W isSupportedAttributeType() { custom object }`() {
        // Given
        val customObject = Any()

        // When
        val result = EvaluationContext.isSupportedAttributeType(customObject)

        // Then
        assertThat(result).isFalse()
    }

    // endregion

    // region Number Type Variations

    @Test
    fun `M accept various number types W addAttribute() { int, long, double, float }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()

        // When
        val context = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)
            .addAttribute("int_val", forge.anInt())
            .addAttribute("long_val", forge.aLong())
            .addAttribute("double_val", forge.aDouble())
            .addAttribute("float_val", forge.aFloat())
            .build()

        // Then
        assertThat(context.attributes).hasSize(4)
        assertThat(context.attributes.values).allMatch { it is Number }
        verify(mockInternalLogger, times(0)).log(
            any<InternalLogger.Level>(),
            any<InternalLogger.Target>(),
            any<() -> String>(),
            any(),
            any(),
            any()
        )
    }

    // endregion

    // region Edge Cases

    @Test
    fun `M handle large attribute count W addAttribute() { many attributes }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val builder = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)

        // When
        repeat(100) { index ->
            builder.addAttribute("attr_$index", "value_$index")
        }
        val context = builder.build()

        // Then
        assertThat(context.attributes).hasSize(100)
        assertThat(context.attributes["attr_50"]).isEqualTo("value_50")
        verify(mockInternalLogger, times(0)).log(
            any<InternalLogger.Level>(),
            any<InternalLogger.Target>(),
            any<() -> String>(),
            any(),
            any(),
            any()
        )
    }

    @Test
    fun `M overwrite previous value W addAttribute() { duplicate key }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeKey = forge.anAlphabeticalString()
        val firstValue = forge.anAlphabeticalString()
        val secondValue = forge.anAlphabeticalString()

        // When
        val context = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)
            .addAttribute(fakeKey, firstValue)
            .addAttribute(fakeKey, secondValue)
            .build()

        // Then
        assertThat(context.attributes[fakeKey]).isEqualTo(secondValue)
        assertThat(context.attributes).hasSize(1)
    }

    // endregion
}
