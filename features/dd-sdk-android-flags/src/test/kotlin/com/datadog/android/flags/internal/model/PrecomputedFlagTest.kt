/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PrecomputedFlagTest {

    @StringForgery
    lateinit var fakeVariationType: String

    @StringForgery
    lateinit var fakeVariationValue: String

    @BoolForgery
    var fakeDoLog: Boolean = false

    @StringForgery
    lateinit var fakeAllocationKey: String

    @StringForgery
    lateinit var fakeVariationKey: String

    @StringForgery
    lateinit var fakeReason: String

    // region Constructor and Data Class Behavior

    @Test
    fun `M create PrecomputedFlag W constructor() { all parameters }`(forge: Forge) {
        // Given
        val fakeExtraLogging = JSONObject().apply {
            put("custom_field", forge.anAlphabeticalString())
            put("numeric_field", forge.anInt())
        }

        // When
        val precomputedFlag = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = fakeExtraLogging,
            reason = fakeReason
        )

        // Then
        assertThat(precomputedFlag.variationType).isEqualTo(fakeVariationType)
        assertThat(precomputedFlag.variationValue).isEqualTo(fakeVariationValue)
        assertThat(precomputedFlag.doLog).isEqualTo(fakeDoLog)
        assertThat(precomputedFlag.allocationKey).isEqualTo(fakeAllocationKey)
        assertThat(precomputedFlag.variationKey).isEqualTo(fakeVariationKey)
        assertThat(precomputedFlag.extraLogging).isEqualTo(fakeExtraLogging)
        assertThat(precomputedFlag.reason).isEqualTo(fakeReason)
    }

    @Test
    fun `M create PrecomputedFlag W constructor() { empty extraLogging }`() {
        // Given
        val emptyExtraLogging = JSONObject()

        // When
        val precomputedFlag = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = emptyExtraLogging,
            reason = fakeReason
        )

        // Then
        assertThat(precomputedFlag.extraLogging).isEqualTo(emptyExtraLogging)
        assertThat(precomputedFlag.extraLogging.length()).isEqualTo(0)
    }

    @Test
    fun `M create PrecomputedFlag W constructor() { complex extraLogging }`(forge: Forge) {
        // Given
        val complexExtraLogging = JSONObject().apply {
            put("string_field", forge.anAlphabeticalString())
            put("int_field", forge.anInt())
            put("double_field", forge.aDouble())
            put("boolean_field", forge.aBool())
            put(
                "nested_object",
                JSONObject().apply {
                    put("nested_string", forge.anAlphabeticalString())
                }
            )
            put(
                "array_field",
                listOf(
                    forge.anAlphabeticalString(),
                    forge.anInt(),
                    forge.aBool()
                )
            )
        }

        // When
        val precomputedFlag = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = complexExtraLogging,
            reason = fakeReason
        )

        // Then
        assertThat(precomputedFlag.extraLogging).isEqualTo(complexExtraLogging)
        assertThat(precomputedFlag.extraLogging.length()).isGreaterThan(0)
    }

    // endregion

    // region Equality and Hash Code

    @Test
    fun `M be equal W equals() { same values }`(forge: Forge) {
        // Given
        val extraLogging = JSONObject().apply {
            put("field", forge.anAlphabeticalString())
        }

        val flag1 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging,
            reason = fakeReason
        )

        val flag2 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging,
            reason = fakeReason
        )

        // Then
        assertThat(flag1).isEqualTo(flag2)
        assertThat(flag1.hashCode()).isEqualTo(flag2.hashCode())
    }

    @Test
    fun `M not be equal W equals() { different variationType }`(forge: Forge) {
        // Given
        val extraLogging = JSONObject()

        val flag1 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging,
            reason = fakeReason
        )

        val flag2 = PrecomputedFlag(
            variationType = forge.anAlphabeticalString(),
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging,
            reason = fakeReason
        )

        // Then
        assertThat(flag1).isNotEqualTo(flag2)
    }

    @Test
    fun `M not be equal W equals() { different variationValue }`(forge: Forge) {
        // Given
        val extraLogging = JSONObject()

        val flag1 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging,
            reason = fakeReason
        )

        val flag2 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = forge.anAlphabeticalString(),
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging,
            reason = fakeReason
        )

        // Then
        assertThat(flag1).isNotEqualTo(flag2)
    }

    @Test
    fun `M not be equal W equals() { different doLog }`() {
        // Given
        val extraLogging = JSONObject()

        val flag1 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = true,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging,
            reason = fakeReason
        )

        val flag2 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = false,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging,
            reason = fakeReason
        )

        // Then
        assertThat(flag1).isNotEqualTo(flag2)
    }

    @Test
    fun `M not be equal W equals() { different allocationKey }`(forge: Forge) {
        // Given
        val extraLogging = JSONObject()

        val flag1 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging,
            reason = fakeReason
        )

        val flag2 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = forge.anAlphabeticalString(),
            variationKey = fakeVariationKey,
            extraLogging = extraLogging,
            reason = fakeReason
        )

        // Then
        assertThat(flag1).isNotEqualTo(flag2)
    }

    @Test
    fun `M not be equal W equals() { different variationKey }`(forge: Forge) {
        // Given
        val extraLogging = JSONObject()

        val flag1 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging,
            reason = fakeReason
        )

        val flag2 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = forge.anAlphabeticalString(),
            extraLogging = extraLogging,
            reason = fakeReason
        )

        // Then
        assertThat(flag1).isNotEqualTo(flag2)
    }

    @Test
    fun `M not be equal W equals() { different extraLogging }`(forge: Forge) {
        // Given
        val extraLogging1 = JSONObject().apply {
            put("field1", forge.anAlphabeticalString())
        }
        val extraLogging2 = JSONObject().apply {
            put("field2", forge.anAlphabeticalString())
        }

        val flag1 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging1,
            reason = fakeReason
        )

        val flag2 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging2,
            reason = fakeReason
        )

        // Then
        assertThat(flag1).isNotEqualTo(flag2)
    }

    @Test
    fun `M not be equal W equals() { different reason }`(forge: Forge) {
        // Given
        val extraLogging = JSONObject()

        val flag1 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging,
            reason = fakeReason
        )

        val flag2 = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging,
            reason = forge.anAlphabeticalString()
        )

        // Then
        assertThat(flag1).isNotEqualTo(flag2)
    }

    // endregion

    // region toString

    @Test
    fun `M provide string representation W toString() { contains all fields }`(forge: Forge) {
        // Given
        val extraLogging = JSONObject().apply {
            put("custom", forge.anAlphabeticalString())
        }

        val precomputedFlag = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = extraLogging,
            reason = fakeReason
        )

        // When
        val stringRepresentation = precomputedFlag.toString()

        // Then
        assertThat(stringRepresentation).contains("PrecomputedFlag")
        assertThat(stringRepresentation).contains("variationType=$fakeVariationType")
        assertThat(stringRepresentation).contains("variationValue=$fakeVariationValue")
        assertThat(stringRepresentation).contains("doLog=$fakeDoLog")
        assertThat(stringRepresentation).contains("allocationKey=$fakeAllocationKey")
        assertThat(stringRepresentation).contains("variationKey=$fakeVariationKey")
        assertThat(stringRepresentation).contains("reason=$fakeReason")
        // extraLogging toString() representation may vary, so we just check it's included
        assertThat(stringRepresentation).contains("extraLogging=")
    }

    // endregion

    // region Copy

    @Test
    fun `M create copy with modified fields W copy() { change single field }`(forge: Forge) {
        // Given
        val originalFlag = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = JSONObject(),
            reason = fakeReason
        )
        val newVariationType = forge.anAlphabeticalString()

        // When
        val copiedFlag = originalFlag.copy(variationType = newVariationType)

        // Then
        assertThat(copiedFlag.variationType).isEqualTo(newVariationType)
        assertThat(copiedFlag.variationValue).isEqualTo(fakeVariationValue)
        assertThat(copiedFlag.doLog).isEqualTo(fakeDoLog)
        assertThat(copiedFlag.allocationKey).isEqualTo(fakeAllocationKey)
        assertThat(copiedFlag.variationKey).isEqualTo(fakeVariationKey)
        assertThat(copiedFlag.reason).isEqualTo(fakeReason)
        assertThat(copiedFlag).isNotEqualTo(originalFlag)
    }

    @Test
    fun `M create copy with multiple modified fields W copy() { change multiple fields }`(forge: Forge) {
        // Given
        val originalFlag = PrecomputedFlag(
            variationType = fakeVariationType,
            variationValue = fakeVariationValue,
            doLog = fakeDoLog,
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = JSONObject(),
            reason = fakeReason
        )
        val newVariationType = forge.anAlphabeticalString()
        val newVariationValue = forge.anAlphabeticalString()
        val newDoLog = !fakeDoLog

        // When
        val copiedFlag = originalFlag.copy(
            variationType = newVariationType,
            variationValue = newVariationValue,
            doLog = newDoLog
        )

        // Then
        assertThat(copiedFlag.variationType).isEqualTo(newVariationType)
        assertThat(copiedFlag.variationValue).isEqualTo(newVariationValue)
        assertThat(copiedFlag.doLog).isEqualTo(newDoLog)
        assertThat(copiedFlag.allocationKey).isEqualTo(fakeAllocationKey)
        assertThat(copiedFlag.variationKey).isEqualTo(fakeVariationKey)
        assertThat(copiedFlag.reason).isEqualTo(fakeReason)
        assertThat(copiedFlag).isNotEqualTo(originalFlag)
    }

    // endregion
}
