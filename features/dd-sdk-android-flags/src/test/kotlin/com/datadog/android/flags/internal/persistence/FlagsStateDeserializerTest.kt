/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.persistence

import com.datadog.android.api.InternalLogger
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsStateDeserializerTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedDeserializer: FlagsStateDeserializer

    @BeforeEach
    fun `set up`() {
        testedDeserializer = FlagsStateDeserializer(mockInternalLogger)
    }

    @Test
    fun `M deserialize flags state W deserialize() { valid JSON }`(forge: Forge) {
        // Given
        val targetingKey = forge.anAlphabeticalString()
        val stringAttr = forge.anAlphabeticalString()
        val numberAttr = forge.anInt().toString()
        val booleanAttr = forge.aBool().toString()
        val timestamp = System.currentTimeMillis()

        val json = JSONObject().apply {
            put(
                "evaluationContext",
                JSONObject().apply {
                    put("targetingKey", targetingKey)
                    put(
                        "attributes",
                        JSONObject().apply {
                            put("string_attr", stringAttr)
                            put("number_attr", numberAttr)
                            put("boolean_attr", booleanAttr)
                        }
                    )
                }
            )
            put(
                "flags",
                JSONObject().apply {
                    put(
                        "flag1",
                        JSONObject().apply {
                            put("variationType", "boolean")
                            put("variationValue", "true")
                            put("doLog", true)
                            put("allocationKey", "allocation1")
                            put("variationKey", "variation1")
                            put("extraLogging", JSONObject())
                            put("reason", "TARGETING_MATCH")
                        }
                    )
                }
            )
            put("lastUpdateTimestamp", timestamp)
        }

        // When
        val result = testedDeserializer.deserialize(json.toString())

        // Then
        checkNotNull(result)
        assertThat(result.evaluationContext.targetingKey).isEqualTo(targetingKey)
        assertThat(result.evaluationContext.attributes).hasSize(3)
        assertThat(result.evaluationContext.attributes["string_attr"]).isEqualTo(stringAttr)
        assertThat(result.evaluationContext.attributes["number_attr"]).isEqualTo(numberAttr)
        assertThat(result.evaluationContext.attributes["boolean_attr"]).isEqualTo(booleanAttr)

        assertThat(result.flags).hasSize(1)
        val flag1 = result.flags["flag1"]
        checkNotNull(flag1)
        assertThat(flag1.variationType).isEqualTo("boolean")
        assertThat(flag1.variationValue).isEqualTo("true")
        assertThat(flag1.doLog).isTrue()

        assertThat(result.lastUpdateTimestamp).isEqualTo(timestamp)
    }

    @Test
    fun `M deserialize the serial id W deserialize() { flags carry a serial id }`(forge: Forge) {
        // Given
        val json = buildStateJson(
            targetingKey = forge.anAlphabeticalString(),
            flags = JSONObject().apply {
                put("withSerialId", buildFlagJson().apply { put("serialId", 0L) })
                put("withNullSerialId", buildFlagJson().apply { put("serialId", JSONObject.NULL) })
                put("withoutSerialId", buildFlagJson())
            }
        )

        // When
        val result = testedDeserializer.deserialize(json)

        // Then
        checkNotNull(result)
        assertThat(result.flags).hasSize(3)
        assertThat(result.flags.getValue("withSerialId").serialId).isEqualTo(0L)
        assertThat(result.flags.getValue("withNullSerialId").serialId).isNull()
        assertThat(result.flags.getValue("withoutSerialId").serialId).isNull()
    }

    @Test
    fun `M deserialize empty state W deserialize() { valid JSON with empty data }`(forge: Forge) {
        // Given
        val targetingKey = forge.anAlphabeticalString()
        val timestamp = System.currentTimeMillis()

        val json = JSONObject().apply {
            put(
                "evaluationContext",
                JSONObject().apply {
                    put("targetingKey", targetingKey)
                    put("attributes", JSONObject())
                }
            )
            put("flags", JSONObject())
            put("lastUpdateTimestamp", timestamp)
        }

        // When
        val result = testedDeserializer.deserialize(json.toString())

        // Then
        checkNotNull(result)
        assertThat(result.evaluationContext.targetingKey).isEqualTo(targetingKey)
        assertThat(result.evaluationContext.attributes).isEmpty()
        assertThat(result.flags).isEmpty()
        assertThat(result.lastUpdateTimestamp).isEqualTo(timestamp)
    }

    @Test
    fun `M return null and log errors W deserialize() { invalid JSON }`() {
        // Given
        val invalidJson = "{ invalid json"

        // When
        val result = testedDeserializer.deserialize(invalidJson)

        // Then
        assertThat(result).isNull()

        val maintainerMessageCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            maintainerMessageCaptor.capture(),
            isA<JsonParseException>(),
            eq(false),
            eq(null)
        )
        assertThat(maintainerMessageCaptor.firstValue.invoke())
            .isEqualTo("Failed to deserialize FlagsStateEntry from JSON")

        val telemetryMessageCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.TELEMETRY),
            telemetryMessageCaptor.capture(),
            isA<JsonParseException>(),
            eq(true),
            eq(null)
        )
        assertThat(telemetryMessageCaptor.firstValue.invoke())
            .isEqualTo("Failed to parse persisted flag state")
    }

    @Test
    fun `M deserialize with empty attributes W deserialize() { missing attributes field }`(forge: Forge) {
        // Given
        val targetingKey = forge.anAlphabeticalString()
        val timestamp = System.currentTimeMillis()

        val json = JSONObject().apply {
            put(
                "evaluationContext",
                JSONObject().apply {
                    put("targetingKey", targetingKey)
                }
            )
            put("flags", JSONObject())
            put("lastUpdateTimestamp", timestamp)
        }.toString()

        // When
        val result = testedDeserializer.deserialize(json)

        // Then
        checkNotNull(result)
        assertThat(result.evaluationContext.targetingKey).isEqualTo(targetingKey)
        assertThat(result.evaluationContext.attributes).isEmpty()
        assertThat(result.flags).isEmpty()
        assertThat(result.lastUpdateTimestamp).isEqualTo(timestamp)
    }

    @Test
    fun `M skip invalid flag and process others W deserialize() { one invalid flag }`(forge: Forge) {
        // Given
        val targetingKey = forge.anAlphabeticalString()
        val timestamp = System.currentTimeMillis()

        val json = JSONObject().apply {
            put(
                "evaluationContext",
                JSONObject().apply {
                    put("targetingKey", targetingKey)
                    put("attributes", JSONObject())
                }
            )
            put(
                "flags",
                JSONObject().apply {
                    put(
                        "validFlag",
                        JSONObject().apply {
                            put("variationType", "string")
                            put("variationValue", "value")
                            put("doLog", false)
                            put("allocationKey", "alloc1")
                            put("variationKey", "var1")
                            put("extraLogging", JSONObject())
                            put("reason", "DEFAULT")
                        }
                    )
                    put(
                        "invalidFlag",
                        JSONObject().apply {
                            put("variationType", "string")
                        }
                    )
                }
            )
            put("lastUpdateTimestamp", timestamp)
        }.toString()

        // When
        val result = testedDeserializer.deserialize(json)

        // Then
        checkNotNull(result)
        assertThat(result.flags).hasSize(1)
        assertThat(result.flags).containsKey("validFlag")
        assertThat(result.flags).doesNotContainKey("invalidFlag")

        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            isA<() -> String>(),
            isA<JsonParseException>(),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M filter unsupported attributes W deserialize() { mixed attribute types }`(forge: Forge) {
        // Given
        val targetingKey = forge.anAlphabeticalString()
        val validString = forge.anAlphabeticalString()
        val validNumber = forge.anInt().toString()
        val validBoolean = forge.aBool().toString()

        val json = JSONObject().apply {
            put(
                "evaluationContext",
                JSONObject().apply {
                    put("targetingKey", targetingKey)
                    put(
                        "attributes",
                        JSONObject().apply {
                            put("valid_string", validString)
                            put("valid_number", validNumber)
                            put("valid_boolean", validBoolean)
                            // Note: When JSONObject.put() is called with complex objects like lists/maps,
                            // they get converted to JSONArray/JSONObject, which are not supported types
                            // in our EvaluationContext validation, so they should be filtered out
                        }
                    )
                }
            )
            put("flags", JSONObject())
            put("lastUpdateTimestamp", System.currentTimeMillis())
        }

        // When
        val result = testedDeserializer.deserialize(json.toString())

        // Then
        checkNotNull(result)
        assertThat(result.evaluationContext.attributes).hasSize(3)
        assertThat(result.evaluationContext.attributes["valid_string"]).isEqualTo(validString)
        assertThat(result.evaluationContext.attributes["valid_number"]).isEqualTo(validNumber)
        assertThat(result.evaluationContext.attributes["valid_boolean"]).isEqualTo(validBoolean)
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "[]", "true", "0", "\"text\""])
    fun `M reject nonobject cache W deserialize() { generated model }`(json: String) {
        // Given
        // The cache is supplied by the test case.
        // When
        val result = testedDeserializer.deserialize(json)

        // Then
        assertThat(result).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "[]", "true", "\"invalid\""])
    fun `M preserve legacy attributes fallback W deserialize() { invalid attributes }`(
        attributes: String,
        forge: Forge
    ) {
        // Given
        val targetingKey = forge.anAlphabeticalString()
        val json = JSONObject(buildStateJson(targetingKey, JSONObject().put("flag", buildFlagJson())))
        json.getJSONObject("evaluationContext").put("attributes", JSONObject("{\"value\":$attributes}").get("value"))
        // Legacy readers ignored the inactive map.
        json.put("encodedFlags", JSONObject.NULL)

        // When
        val result = checkNotNull(testedDeserializer.deserialize(json.toString()))

        // Then
        assertThat(result.evaluationContext.targetingKey).isEqualTo(targetingKey)
        assertThat(result.evaluationContext.attributes).isEmpty()
        assertThat(result.flags.getValue("flag").variationValue).isEqualTo("true")
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "[]", "{}", "true", "\"invalid\""])
    fun `M reject invalid cache timestamp W deserialize() { generated model }`(timestamp: String, forge: Forge) {
        // Given
        val json = JSONObject(buildStateJson(forge.anAlphabeticalString(), JSONObject()))
        json.put("lastUpdateTimestamp", JSONObject("{\"value\":$timestamp}").get("value"))

        // When
        val result = testedDeserializer.deserialize(json.toString())

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M preserve legacy values W serialize() and deserialize() { generated models }`(forge: Forge) {
        // Given
        val targetingKey = forge.anAlphabeticalString()
        val value = forge.anAlphabeticalString()
        val flags = JSONObject()
        val variants = listOf("true", forge.anInt().toString(), value, "{\"nested\":[true,null,1]}", "null")
        variants.forEachIndexed { index, variant ->
            flags.put(
                "flag-$index",
                buildFlagJson().apply {
                    put("variationValue", variant)
                    put("extraLogging", JSONObject("{\"nested\":{\"array\":[true,null,1]},\"text\":\"<>&\"}"))
                }
            )
        }
        val legacy = buildStateJson(targetingKey, flags)

        // When
        val loaded = checkNotNull(testedDeserializer.deserialize(legacy))
        val rewritten = FlagsStateSerializer(mockInternalLogger).serialize(loaded)
        val restored = checkNotNull(testedDeserializer.deserialize(rewritten))

        // Then
        assertThat(JsonParser.parseString(rewritten)).isEqualTo(JsonParser.parseString(legacy))
        assertThat(restored.evaluationContext).isEqualTo(loaded.evaluationContext)
        variants.forEachIndexed { index, variant ->
            assertThat(restored.flags.getValue("flag-$index").variationValue).isEqualTo(variant)
        }
    }

    private fun buildFlagJson(): JSONObject = JSONObject().apply {
        put("variationType", "boolean")
        put("variationValue", "true")
        put("doLog", true)
        put("allocationKey", "allocation1")
        put("variationKey", "variation1")
        put("extraLogging", JSONObject())
        put("reason", "TARGETING_MATCH")
    }

    private fun buildStateJson(targetingKey: String, flags: JSONObject): String = JSONObject().apply {
        put(
            "evaluationContext",
            JSONObject().apply {
                put("targetingKey", targetingKey)
                put("attributes", JSONObject())
            }
        )
        put("flags", flags)
        put("lastUpdateTimestamp", System.currentTimeMillis())
    }.toString()
}
