/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.model.PrecomputedFlag
import com.datadog.android.flags.model.EvaluationContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
internal class FlagsStateSerializerTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedSerializer: FlagsStateSerializer

    @BeforeEach
    fun `set up`() {
        testedSerializer = FlagsStateSerializer(mockInternalLogger)
    }

    @Test
    fun `M serialize complete flags state W serialize()`(forge: Forge) {
        // Given
        val targetingKey = forge.anAlphabeticalString()
        val attributes = mapOf(
            "string_attr" to forge.anAlphabeticalString(),
            "number_attr" to forge.anInt().toString(),
            "boolean_attr" to forge.aBool().toString()
        )
        val evaluationContext = EvaluationContext(targetingKey, attributes)

        val flags = mapOf(
            "flag1" to PrecomputedFlag(
                variationType = "boolean",
                variationValue = "true",
                doLog = true,
                allocationKey = forge.anAlphabeticalString(),
                variationKey = "variation1",
                extraLogging = JSONObject().apply { put("extra", "data") },
                reason = "TARGETING_MATCH"
            ),
            "flag2" to PrecomputedFlag(
                variationType = "string",
                variationValue = forge.anAlphabeticalString(),
                doLog = false,
                allocationKey = forge.anAlphabeticalString(),
                variationKey = "variation2",
                extraLogging = JSONObject(),
                reason = "DEFAULT"
            )
        )

        val timestamp = System.currentTimeMillis()
        val flagsState = FlagsStateEntry(evaluationContext, flags, timestamp)

        // When
        val serialized = testedSerializer.serialize(flagsState)

        // Then
        assertThat(serialized).isNotEmpty()

        SerializedFlagsStateAssert.assertThatSerializedFlagsState(serialized)
            .hasTargetingKey(targetingKey)
            .hasFlag("flag1")
            .hasFlag("flag2")
            .hasTimestamp(timestamp)
    }

    @Test
    fun `M serialize empty flags state W serialize()`(forge: Forge) {
        // Given
        val targetingKey = forge.anAlphabeticalString()
        val evaluationContext = EvaluationContext(targetingKey, emptyMap())
        val flagsState = FlagsStateEntry(evaluationContext, emptyMap())

        // When
        val serialized = testedSerializer.serialize(flagsState)

        // Then
        assertThat(serialized).isNotEmpty()

        SerializedFlagsStateAssert.assertThatSerializedFlagsState(serialized)
            .hasEmptyFlags()
    }

    @Test
    fun `M return valid JSON W serialize() { edge case }`() {
        // Given
        // Create a state that could potentially cause serialization issues
        val evaluationContext = EvaluationContext("key", emptyMap())
        val flagsState = FlagsStateEntry(evaluationContext, emptyMap())

        // When
        val serialized = testedSerializer.serialize(flagsState)

        // Then
        assertThat(serialized).isNotEmpty()
        // Should still produce valid JSON even in edge cases
        SerializedFlagsStateAssert.assertThatSerializedFlagsState(serialized)
            .hasEmptyFlags()
    }
}
