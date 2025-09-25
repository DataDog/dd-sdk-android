/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlagConstants
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
                    variationType = PrecomputedFlagConstants.VariationType.STRING,
                    variationValue = fakeStringValue
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        assertThat(flag).isNotNull
        assertThat(flag!!.variationType).isEqualTo(PrecomputedFlagConstants.VariationType.STRING)
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
                    variationType = PrecomputedFlagConstants.VariationType.BOOLEAN,
                    variationValue = true
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        assertThat(flag).isNotNull
        assertThat(flag!!.variationType).isEqualTo(PrecomputedFlagConstants.VariationType.BOOLEAN)
        assertThat(flag.variationValue).isEqualTo("true")
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M parse single flag with boolean variation W map() { valid JSON with boolean false }`() {
        // Given
        val json = buildValidJson(
            mapOf(
                fakeFlagName to buildFlagJson(
                    variationType = PrecomputedFlagConstants.VariationType.BOOLEAN,
                    variationValue = false
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        assertThat(flag).isNotNull
        assertThat(flag!!.variationType).isEqualTo(PrecomputedFlagConstants.VariationType.BOOLEAN)
        assertThat(flag.variationValue).isEqualTo("false")
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M parse single flag with integer variation W map() { valid JSON }`() {
        // Given
        val json = buildValidJson(
            mapOf(
                fakeFlagName to buildFlagJson(
                    variationType = PrecomputedFlagConstants.VariationType.INTEGER,
                    variationValue = fakeIntValue
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        assertThat(flag).isNotNull
        assertThat(flag!!.variationType).isEqualTo(PrecomputedFlagConstants.VariationType.INTEGER)
        assertThat(flag.variationValue).isEqualTo(fakeIntValue.toString())
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M parse single flag with double variation W map() { valid JSON }`() {
        // Given
        val json = buildValidJson(
            mapOf(
                fakeFlagName to buildFlagJson(
                    variationType = PrecomputedFlagConstants.VariationType.DOUBLE,
                    variationValue = fakeDoubleValue
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        assertThat(flag).isNotNull
        assertThat(flag!!.variationType).isEqualTo(PrecomputedFlagConstants.VariationType.DOUBLE)
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
                    variationType = PrecomputedFlagConstants.VariationType.JSON,
                    variationValue = jsonValue
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        assertThat(flag).isNotNull
        assertThat(flag!!.variationType).isEqualTo(PrecomputedFlagConstants.VariationType.JSON)
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
                    variationType = PrecomputedFlagConstants.VariationType.STRING,
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
        assertThat(flag).isNotNull
        assertThat(flag!!.extraLogging.toString()).isEqualTo(extraLogging.toString())
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
                    variationType = PrecomputedFlagConstants.VariationType.STRING,
                    variationValue = "value1"
                ),
                flagName2 to buildFlagJson(
                    variationType = PrecomputedFlagConstants.VariationType.BOOLEAN,
                    variationValue = true
                ),
                flagName3 to buildFlagJson(
                    variationType = PrecomputedFlagConstants.VariationType.INTEGER,
                    variationValue = 42
                )
            )
        )

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(3)
        assertThat(result).containsKeys(flagName1, flagName2, flagName3)

        assertThat(result[flagName1]!!.variationType).isEqualTo(PrecomputedFlagConstants.VariationType.STRING)
        assertThat(result[flagName1]!!.variationValue).isEqualTo("value1")

        assertThat(result[flagName2]!!.variationType).isEqualTo(PrecomputedFlagConstants.VariationType.BOOLEAN)
        assertThat(result[flagName2]!!.variationValue).isEqualTo("true")

        assertThat(result[flagName3]!!.variationType).isEqualTo(PrecomputedFlagConstants.VariationType.INTEGER)
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
            org.mockito.kotlin.any(),
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
            org.mockito.kotlin.any(),
            eq(false),
            eq(null)
        )
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("Failed to parse precomputed response")
    }

    @Test
    fun `M return empty map and log error W map() { missing attributes field }`() {
        // Given
        val jsonWithoutAttributes = JSONObject().apply {
            put(
                "data",
                JSONObject().apply {
                    put("notAttributes", JSONObject())
                }
            )
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
            org.mockito.kotlin.any(),
            eq(false),
            eq(null)
        )
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("Failed to parse precomputed response")
    }

    @Test
    fun `M return empty map and log error W map() { missing flags field }`() {
        // Given
        val jsonWithoutFlags = JSONObject().apply {
            put(
                "data",
                JSONObject().apply {
                    put(
                        "attributes",
                        JSONObject().apply {
                            put("notFlags", JSONObject())
                        }
                    )
                }
            )
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
            org.mockito.kotlin.any(),
            eq(false),
            eq(null)
        )
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("Failed to parse precomputed response")
    }

    @Test
    fun `M return empty map and log error W map() { missing required flag fields }`() {
        // Given
        val jsonWithIncompleteFlag = JSONObject().apply {
            put(
                "data",
                JSONObject().apply {
                    put(
                        "attributes",
                        JSONObject().apply {
                            put(
                                "flags",
                                JSONObject().apply {
                                    put(
                                        fakeFlagName,
                                        JSONObject().apply {
                                            put("variationType", PrecomputedFlagConstants.VariationType.STRING)
                                            // Missing other required fields
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
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
            org.mockito.kotlin.any(),
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
            org.mockito.kotlin.any(),
            eq(false),
            eq(null)
        )
        assertThat(messageCaptor.firstValue.invoke()).isEqualTo("Failed to parse precomputed response")
    }

    @Test
    fun `M handle null variation value W map() { null variationValue }`() {
        // Given
        val json = JSONObject().apply {
            put(
                "data",
                JSONObject().apply {
                    put(
                        "attributes",
                        JSONObject().apply {
                            put(
                                "flags",
                                JSONObject().apply {
                                    put(
                                        fakeFlagName,
                                        JSONObject().apply {
                                            put("variationType", PrecomputedFlagConstants.VariationType.STRING)
                                            put("variationValue", JSONObject.NULL)
                                            put("doLog", fakeDoLog)
                                            put("allocationKey", fakeAllocationKey)
                                            put("variationKey", fakeVariationKey)
                                            put("reason", fakeReason)
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }.toString()

        // When
        val result = testedMapper.map(json)

        // Then
        assertThat(result).hasSize(1)
        val flag = result[fakeFlagName]
        assertThat(flag).isNotNull
        assertThat(flag!!.variationValue).isEqualTo("null")
        verifyNoInteractions(mockInternalLogger)
    }

    // endregion

    // region Helper Methods

    private fun buildValidJson(flags: Map<String, JSONObject>): String {
        return JSONObject().apply {
            put(
                "data",
                JSONObject().apply {
                    put(
                        "attributes",
                        JSONObject().apply {
                            put(
                                "flags",
                                JSONObject().apply {
                                    flags.forEach { (name, flagData) ->
                                        put(name, flagData)
                                    }
                                }
                            )
                        }
                    )
                }
            )
        }.toString()
    }

    private fun buildValidJsonWithExtraLogging(flags: Map<String, JSONObject>): String {
        return buildValidJson(flags)
    }

    private fun buildFlagJson(
        variationType: String,
        variationValue: Any
    ): JSONObject {
        return JSONObject().apply {
            put("variationType", variationType)
            put("variationValue", variationValue)
            put("doLog", fakeDoLog)
            put("allocationKey", fakeAllocationKey)
            put("variationKey", fakeVariationKey)
            put("reason", fakeReason)
        }
    }

    private fun buildFlagJsonWithExtraLogging(
        variationType: String,
        variationValue: Any,
        extraLogging: JSONObject
    ): JSONObject {
        return JSONObject().apply {
            put("variationType", variationType)
            put("variationValue", variationValue)
            put("doLog", fakeDoLog)
            put("allocationKey", fakeAllocationKey)
            put("variationKey", fakeVariationKey)
            put("reason", fakeReason)
            put("extraLogging", extraLogging)
        }
    }

    // endregion
}
