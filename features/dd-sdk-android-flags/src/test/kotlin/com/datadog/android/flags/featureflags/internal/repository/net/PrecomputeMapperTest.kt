/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.internal.model.VariationType
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PrecomputeMapperTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    lateinit var fakeFlagName: String

    @StringForgery
    lateinit var fakeAllocationKey: String

    @StringForgery
    lateinit var fakeVariationKey: String

    @StringForgery
    lateinit var fakeReason: String

    @BoolForgery
    var fakeDoLog: Boolean = false

    @IntForgery
    var fakeIntValue: Int = 0

    @DoubleForgery
    var fakeDoubleValue: Double = 0.0

    private lateinit var testedMapper: PrecomputeMapper

    @BeforeEach
    fun `set up`() {
        testedMapper = PrecomputeMapper(mockInternalLogger)
    }

    // region Valid JSON Parsing

    @Test
    fun `M parse single flag with string variation W map() { valid JSON }`() {
        // Given
        val fakeStringValue = "test-value"
        val json = buildValidJson(
            mapOf(
                fakeFlagName to buildFlagJson(
                    variationType = VariationType.STRING.value,
                    variationValue = fakeStringValue
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        checkNotNull(flag)
        assertThat(flag.variationType).isEqualTo(VariationType.STRING.value)
        assertThat(flag.variationValue).isEqualTo(fakeStringValue)
        assertThat(flag.doLog).isEqualTo(fakeDoLog)
        assertThat(flag.allocationKey).isEqualTo(fakeAllocationKey)
        assertThat(flag.variationKey).isEqualTo(fakeVariationKey)
        assertThat(flag.reason).isEqualTo(fakeReason)
        assertThat(flag.extraLogging.length()).isEqualTo(0)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M parse single flag with boolean variation W map() { valid JSON with boolean true }`() {
        // Given
        val json = buildValidJson(
            mapOf(
                fakeFlagName to buildFlagJson(
                    variationType = VariationType.BOOLEAN.value,
                    variationValue = true
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        checkNotNull(flag)
        assertThat(flag.variationType).isEqualTo(VariationType.BOOLEAN.value)
        assertThat(flag.variationValue).isEqualTo("true")
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M parse single flag with boolean variation W map() { valid JSON with boolean false }`() {
        // Given
        val json = buildValidJson(
            mapOf(
                fakeFlagName to buildFlagJson(
                    variationType = VariationType.BOOLEAN.value,
                    variationValue = false
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        checkNotNull(flag)
        assertThat(flag.variationType).isEqualTo(VariationType.BOOLEAN.value)
        assertThat(flag.variationValue).isEqualTo("false")
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M parse single flag with integer variation W map() { valid JSON }`() {
        // Given
        val json = buildValidJson(
            mapOf(
                fakeFlagName to buildFlagJson(
                    variationType = VariationType.INTEGER.value,
                    variationValue = fakeIntValue
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        checkNotNull(flag)
        assertThat(flag.variationType).isEqualTo(VariationType.INTEGER.value)
        assertThat(flag.variationValue).isEqualTo(fakeIntValue.toString())
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M parse single flag with double variation W map() { valid JSON }`() {
        // Given
        val json = buildValidJson(
            mapOf(
                fakeFlagName to buildFlagJson(
                    variationType = VariationType.NUMBER.value,
                    variationValue = fakeDoubleValue
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        checkNotNull(flag)
        assertThat(flag.variationType).isEqualTo(VariationType.NUMBER.value)
        assertThat(flag.variationValue).isEqualTo(fakeDoubleValue.toString())
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M parse single flag with JSON variation W map() { valid JSON }`(forge: Forge) {
        // Given
        val jsonValue = JSONObject().apply {
            put("nested_key", forge.anAlphabeticalString())
            put("nested_number", forge.anInt())
        }
        val json = buildValidJson(
            mapOf(
                fakeFlagName to buildFlagJson(
                    variationType = VariationType.OBJECT.value,
                    variationValue = jsonValue
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        checkNotNull(flag)
        assertThat(flag.variationType).isEqualTo(VariationType.OBJECT.value)
        assertThat(flag.variationValue).isEqualTo(jsonValue.toString())
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M parse flag with extra logging W map() { valid JSON with extraLogging }`(forge: Forge) {
        // Given
        val extraLogging = JSONObject().apply {
            put("custom_field", forge.anAlphabeticalString())
            put("numeric_field", forge.anInt())
        }
        val json = buildValidJsonWithExtraLogging(
            mapOf(
                fakeFlagName to buildFlagJsonWithExtraLogging(
                    variationType = VariationType.STRING.value,
                    variationValue = "test-value",
                    extraLogging = extraLogging
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        checkNotNull(flag)
        assertThat(flag.extraLogging.toString()).isEqualTo(extraLogging.toString())
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M parse multiple flags W map() { valid JSON with multiple flags }`(forge: Forge) {
        // Given
        val flagName1 = forge.anAlphabeticalString()
        val flagName2 = forge.anAlphabeticalString()
        val flagName3 = forge.anAlphabeticalString()

        val json = buildValidJson(
            mapOf(
                flagName1 to buildFlagJson(
                    variationType = VariationType.STRING.value,
                    variationValue = "value1"
                ),
                flagName2 to buildFlagJson(
                    variationType = VariationType.BOOLEAN.value,
                    variationValue = true
                ),
                flagName3 to buildFlagJson(
                    variationType = VariationType.INTEGER.value,
                    variationValue = 42
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(3)
        assertThat(result).containsKeys(flagName1, flagName2, flagName3)

        assertThat(result[flagName1]!!.variationType).isEqualTo(VariationType.STRING.value)
        assertThat(result[flagName1]!!.variationValue).isEqualTo("value1")

        assertThat(result[flagName2]!!.variationType).isEqualTo(VariationType.BOOLEAN.value)
        assertThat(result[flagName2]!!.variationValue).isEqualTo("true")

        assertThat(result[flagName3]!!.variationType).isEqualTo(VariationType.INTEGER.value)
        assertThat(result[flagName3]!!.variationValue).isEqualTo("42")

        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M parse empty flags object W map() { valid JSON with empty flags }`() {
        // Given
        val json = buildValidJson(emptyMap())

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).isEmpty()
        verifyNoInteractions(mockInternalLogger)
    }

    // endregion

    // region Error Handling

    @Test
    fun `M return empty map and log error W map() { invalid JSON }`() {
        // Given
        val invalidJson = "{ invalid json"

        // When
        val result = testedMapper.map(invalidJson)

        // Then
        assertThat(result).isEmpty()

        val messageCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            messageCaptor.capture(),
            any(),
            eq(false),
            eq(null)
        )
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("Failed to parse precomputed response")
    }

    @Test
    fun `M return empty map and log error W map() { missing data field }`() {
        // Given
        val jsonWithoutData = JSONObject().apply {
            put("notData", JSONObject())
        }.toString()

        // When
        val result = testedMapper.map(jsonWithoutData)

        // Then
        assertThat(result).isEmpty()

        val messageCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            messageCaptor.capture(),
            any(),
            eq(false),
            eq(null)
        )
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("Failed to parse precomputed response")
    }

    @Test
    fun `M return empty map and log error W map() { missing attributes field }`() {
        // Given
        val jsonWithoutAttributes = JSONObject().data {
            put("notAttributes", JSONObject())
        }.toString()

        // When
        val result = testedMapper.map(jsonWithoutAttributes)

        // Then
        assertThat(result).isEmpty()

        val messageCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            messageCaptor.capture(),
            any(),
            eq(false),
            eq(null)
        )
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("Failed to parse precomputed response")
    }

    @Test
    fun `M return empty map and log error W map() { missing flags field }`() {
        // Given
        val jsonWithoutFlags = JSONObject().data {
            attributes {
                put("notFlags", JSONObject())
            }
        }.toString()

        // When
        val result = testedMapper.map(jsonWithoutFlags)

        // Then
        assertThat(result).isEmpty()

        val messageCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            messageCaptor.capture(),
            any(),
            eq(false),
            eq(null)
        )
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("Failed to parse precomputed response")
    }

    @Test
    fun `M return empty map and log error W map() { missing required flag fields }`() {
        // Given
        val jsonWithIncompleteFlag = JSONObject().data {
            attributes {
                flags {
                    flag(fakeFlagName) {
                        put("variationType", VariationType.STRING)
                        // Missing other required fields
                    }
                }
            }
        }.toString()

        // When
        val result = testedMapper.map(jsonWithIncompleteFlag)

        // Then
        assertThat(result).isEmpty()

        val messageCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            messageCaptor.capture(),
            any(),
            eq(false),
            eq(null)
        )
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("Failed to parse precomputed response")
    }

    @Test
    fun `M return empty map and log error W map() { empty JSON string }`() {
        // Given
        val emptyJson = ""

        // When
        val result = testedMapper.map(emptyJson)

        // Then
        assertThat(result).isEmpty()

        val messageCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            messageCaptor.capture(),
            any(),
            eq(false),
            eq(null)
        )
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("Failed to parse precomputed response")
    }

    @Test
    fun `M handle null variation value W map() { null variationValue }`() {
        // Given
        val json = JSONObject().data {
            attributes {
                flags {
                    flag(fakeFlagName) {
                        put("variationType", VariationType.STRING.value)
                        put("variationValue", JSONObject.NULL)
                        put("doLog", fakeDoLog)
                        put("allocationKey", fakeAllocationKey)
                        put("variationKey", fakeVariationKey)
                        put("extraLogging", JSONObject())
                        put("reason", fakeReason)
                    }
                }
            }
        }.toString()

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        checkNotNull(flag)
        assertThat(flag.variationValue).isEqualTo("null")
        verifyNoInteractions(mockInternalLogger)
    }

    // endregion

    // region Helper Methods

    private fun buildValidJson(flags: Map<String, JSONObject>): String = JSONObject().data {
        attributes {
            flags {
                flags.forEach { (name, flagData) ->
                    put(name, flagData)
                }
            }
        }
    }.toString()

    private fun buildValidJsonWithExtraLogging(flags: Map<String, JSONObject>): String = buildValidJson(flags)

    private fun buildFlagJson(variationType: String, variationValue: Any): JSONObject = JSONObject().apply {
        put("variationType", variationType)
        put("variationValue", variationValue)
        put("doLog", fakeDoLog)
        put("allocationKey", fakeAllocationKey)
        put("variationKey", fakeVariationKey)
        put("extraLogging", JSONObject())
        put("reason", fakeReason)
    }

    private fun buildFlagJsonWithExtraLogging(
        variationType: String,
        variationValue: Any,
        extraLogging: JSONObject
    ): JSONObject = JSONObject().apply {
        put("variationType", variationType)
        put("variationValue", variationValue)
        put("doLog", fakeDoLog)
        put("allocationKey", fakeAllocationKey)
        put("variationKey", fakeVariationKey)
        put("reason", fakeReason)
        put("extraLogging", extraLogging)
    }

    // endregion

    // region JSON Builder Extensions

    private fun JSONObject.data(block: JSONObject.() -> Unit): JSONObject {
        val dataObject = JSONObject().apply(block)
        put("data", dataObject)
        return this
    }

    private fun JSONObject.attributes(block: JSONObject.() -> Unit): JSONObject {
        val attributesObject = JSONObject().apply(block)
        put("attributes", attributesObject)
        return this
    }

    private fun JSONObject.flags(block: JSONObject.() -> Unit): JSONObject {
        val flagsObject = JSONObject().apply(block)
        put("flags", flagsObject)
        return this
    }

    private fun JSONObject.flag(name: String, block: JSONObject.() -> Unit): JSONObject {
        val flagObject = JSONObject().apply(block)
        put(name, flagObject)
        return this
    }

    // endregion
}
