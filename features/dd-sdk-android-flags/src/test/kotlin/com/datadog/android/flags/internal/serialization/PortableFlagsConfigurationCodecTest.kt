/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.serialization

import com.datadog.android.flags.internal.FlagValueConverter
import com.datadog.android.flags.internal.TypeMismatchException
import com.datadog.android.flags.internal.serialization.PortableFlagsConfiguration.Failure
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ForgeExtension::class)
internal class PortableFlagsConfigurationCodecTest {
    private val testedCodec = PortableFlagsConfigurationCodec()

    @ParameterizedTest
    @CsvSource(
        "boolean,false", "string,\"\"", "integer,0", "float,0.5", "object,{\"nested\":[null]}",
        "BOOLEAN,true", "STRING,\"text\"", "NUMBER,1.5", "NUMERIC,2", "INTEGER,1.5", "JSON,null", "OBJECT,[]"
    )
    fun `M preserve values and metadata W decode() and encode()`(
        type: String,
        value: String,
        @StringForgery fakeEtag: String
    ) {
        // Given
        val fakeWire = wire(type = type, value = value)
        val expectedValue = when (type) {
            "string", "STRING" -> jsonValue(value).asString
            else -> value
        }
        fakeWire.getAsJsonObject("precomputed").apply {
            add("context", json("""{"targetingKey":"subject","nested":{"items":[1,2]}}"""))
            addProperty("fetchedAt", 1_700_000_000_123.5)
            addProperty("etag", fakeEtag)
        }

        // When
        val decoded = testedCodec.decode(fakeWire.toString())
        val encoded = testedCodec.encode(decoded)
        val restored = testedCodec.decode(encoded)

        // Then
        assertThat(decoded.failure).isNull()
        val precomputed = checkNotNull(decoded.precomputed)
        val flag = checkNotNull(precomputed.assignments.flags["flag"])
        assertThat(flag.variationValue).isEqualTo(expectedValue)
        assertThat(flag.serialId).isEqualTo(42)
        assertThat(flag.reason).isEqualTo("TARGETING_MATCH")
        assertThat(flag.doLog).isTrue()
        assertThat(flag.observeFullEvaluationData).isTrue()
        assertThat(precomputed.flagErrors).isEmpty()
        assertThat(precomputed.fetchedAt).isEqualTo(1_700_000_000_123.5)
        assertThat(precomputed.etag).isEqualTo(fakeEtag)
        val encodedEntry = json(encoded).getAsJsonObject("precomputed")
        assertThat(json(encoded).get("version").asInt).isEqualTo(1)
        assertThat(encodedEntry.keySet()).containsExactlyInAnyOrder("response", "context", "fetchedAt", "etag")
        assertThat(encodedEntry.get("response")).isEqualTo(fakeWire.getAsJsonObject("precomputed").get("response"))
        assertThat(restored.precomputed?.response).isEqualTo(precomputed.response)
        assertThat(restored.precomputed?.context).isEqualTo(precomputed.context)
        assertThat(restored.precomputed?.fetchedAt).isEqualTo(precomputed.fetchedAt)
        assertThat(restored.precomputed?.etag).isEqualTo(precomputed.etag)
        assertThat(
            json(precomputed.response).getAsJsonObject("data").getAsJsonObject("attributes")
                .getAsJsonObject("evaluation_options").get("experimentation_opt_out").asBoolean
        ).isTrue()
    }

    @Test
    fun `M preserve absent and empty contexts W decode() and encode()`() {
        // Given
        val fakeAbsent = wire()
        val fakeEmpty = wire().apply { getAsJsonObject("precomputed").add("context", JsonObject()) }

        // When
        val absent = testedCodec.decode(testedCodec.encode(testedCodec.decode(fakeAbsent.toString())))
        val empty = testedCodec.decode(testedCodec.encode(testedCodec.decode(fakeEmpty.toString())))

        // Then
        assertThat(absent.precomputed?.context).isNull()
        assertThat(empty.precomputed?.context).isEqualTo(JsonObject())
    }

    @Test
    fun `M detach context from callers W decode() and encode()`() {
        // Given
        val fakeWire = wire().apply {
            getAsJsonObject("precomputed").add("context", json("""{"targetingKey":"subject"}"""))
        }

        // When
        val decoded = testedCodec.decode(fakeWire.toString())
        decoded.precomputed?.context?.addProperty("targetingKey", "changed")
        val restored = testedCodec.decode(testedCodec.encode(decoded))

        // Then
        assertThat(restored.precomputed?.context?.get("targetingKey")?.asString).isEqualTo("subject")
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "{}", "{\"variationType\":\"future\"}", "{\"doLog\":\"true\"}"])
    fun `M retain valid siblings and flag errors W decode() and encode()`(invalid: String) {
        // Given
        val fakeWire = wire().apply {
            val entry = getAsJsonObject("precomputed")
            val response = json(entry.get("response").asString)
            response.getAsJsonObject("data").getAsJsonObject("attributes").getAsJsonObject("flags")
                .add("bad", jsonValue(invalid))
            entry.addProperty("response", response.toString())
        }

        // When
        val decoded = testedCodec.decode(fakeWire.toString())
        val restored = testedCodec.decode(testedCodec.encode(decoded))

        // Then
        val precomputed = checkNotNull(decoded.precomputed)
        assertThat(precomputed.assignments.flags.keys).containsExactly("flag")
        assertThat(precomputed.flagErrors)
            .containsExactlyEntriesOf(mapOf("bad" to "Invalid precomputed flag configuration"))
        assertThat(restored.precomputed?.flagErrors).isEqualTo(precomputed.flagErrors)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "not JSON", "null", "[]", "{}", "{\"version\":999}", "{\"version\":\"1\"}",
            "{version:1}", "{'version':1}", "{\"version\":1} trailing"
        ]
    )
    fun `M reject invalid envelope without throwing W decode()`(wire: String) {
        // When
        val decoded = assertDoesNotThrow<PortableFlagsConfiguration> { testedCodec.decode(wire) }

        // Then
        assertThat(decoded.failure).isEqualTo(Failure.INVALID_ENVELOPE)
        assertThat(decoded.precomputed).isNull()
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun `M accept supported empty envelopes W decode()`(version: Int) {
        // When
        val decoded = testedCodec.decode("""{"version":$version}""")

        // Then
        assertThat(decoded.failure).isNull()
        assertThat(decoded.precomputed).isNull()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "{\"context\":null}", "{\"context\":[]}", "{\"context\":{\"targetingKey\":1}}",
            "{\"fetchedAt\":\"123\"}", "{\"fetchedAt\":null}", "{\"etag\":1}", "{\"etag\":null}",
            "{\"response\":\"not JSON\"}", "{\"response\":{}}"
        ]
    )
    fun `M distinguish invalid precomputed entry W decode()`(entry: String) {
        // Given
        val fakeWire = wire().apply {
            json(entry).entrySet().forEach { (key, value) -> getAsJsonObject("precomputed").add(key, value) }
        }

        // When
        val decoded = testedCodec.decode(fakeWire.toString())

        // Then
        assertThat(decoded.failure).isEqualTo(Failure.INVALID_PRECOMPUTED)
        assertThat(decoded.precomputed).isNull()
        assertThat(json(testedCodec.encode(decoded))).isEqualTo(json("""{"version":1}"""))
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "[]", "{}", "{\"data\":{\"attributes\":{\"flags\":{}}}}"])
    fun `M reject malformed response W decode()`(response: String) {
        // Given
        val fakeWire = wire().apply { getAsJsonObject("precomputed").addProperty("response", response) }

        // When
        val decoded = testedCodec.decode(fakeWire.toString())

        // Then
        assertThat(decoded.failure).isEqualTo(Failure.INVALID_PRECOMPUTED)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "{\"doLog\":\"true\"}", "{\"allocationKey\":12}", "{\"variationValue\":\"true\"}",
            "{\"variationType\":\"unknown\"}", "{\"variationValue\":null}"
        ]
    )
    fun `M report malformed flag values W decode()`(changes: String) {
        // Given
        val fakeWire = wire()
        val entry = fakeWire.getAsJsonObject("precomputed")
        val response = json(entry.get("response").asString)
        val flag = response.getAsJsonObject("data").getAsJsonObject("attributes")
            .getAsJsonObject("flags").getAsJsonObject("flag")
        json(changes).entrySet().forEach { (key, value) -> flag.add(key, value) }
        entry.addProperty("response", response.toString())

        // When
        val decoded = testedCodec.decode(fakeWire.toString())

        // Then
        val precomputed = checkNotNull(decoded.precomputed)
        assertThat(precomputed.assignments.flags).isEmpty()
        assertThat(precomputed.flagErrors).containsKey("flag")
    }

    @Test
    fun `M ignore rules sibling W decode() and encode()`() {
        // Given
        val fakeWire = wire().apply { add("rules", json("""{"response":"invalid protobuf"}""")) }

        // When
        val decoded = testedCodec.decode(fakeWire.toString())
        val encoded = json(testedCodec.encode(decoded))

        // Then
        assertThat(decoded.failure).isNull()
        assertThat(decoded.precomputed).isNotNull()
        assertThat(encoded.has("rules")).isFalse()
    }

    @Test
    fun `M preserve hashed keys with version two W decode() and encode()`() {
        // Given
        val fakeWire = wire(key = DIGEST, metadata = ENCODING)

        // When
        val decoded = testedCodec.decode(fakeWire.toString())
        val encoded = testedCodec.encode(decoded)
        val restored = testedCodec.decode(encoded)

        // Then
        val precomputed = checkNotNull(decoded.precomputed)
        assertThat(precomputed.assignments.obfuscation?.encode("flag")).isEqualTo(DIGEST)
        assertThat(json(encoded).get("version").asInt).isEqualTo(2)
        assertThat(restored.precomputed?.response).isEqualTo(precomputed.response)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "{\"obfuscated\":true}", "{\"obfuscated\":\"true\"}", "{\"obfuscation\":{}}",
            "{\"obfuscated\":true,\"obfuscation\":{\"scheme\":\"unknown\"," +
                "\"salt\":\"000102030405060708090a0b0c0d0e0f\"}}",
            "{\"obfuscated\":true,\"obfuscation\":{\"scheme\":\"flag-key-sha256-v1\",\"salt\":\"bad\"}}"
        ]
    )
    fun `M reject invalid encoding metadata W decode()`(metadata: String) {
        // When
        val decoded = testedCodec.decode(wire(key = DIGEST, metadata = metadata).toString())

        // Then
        assertThat(decoded.failure).isEqualTo(Failure.INVALID_PRECOMPUTED)
        assertThat(decoded.precomputed).isNull()
    }

    @Test
    fun `M reject plaintext keys in encoded payload W decode() {malformed flag}`() {
        // Given
        val fakeWire = wire(metadata = ENCODING, type = "future")

        // When
        val decoded = testedCodec.decode(fakeWire.toString())

        // Then
        assertThat(decoded.failure).isEqualTo(Failure.INVALID_PRECOMPUTED)
    }

    @ParameterizedTest
    @CsvSource(
        "boolean,true,boolean", "BOOLEAN,true,boolean", "string,\"s\",string", "STRING,\"s\",string",
        "integer,2,integer", "float,2,float", "number,2,number", "NUMBER,2,number",
        "NUMERIC,2,number", "INTEGER,1.5,number", "object,{},object", "OBJECT,{},object", "JSON,{},object"
    )
    fun `M preserve native types and normalize browser aliases W decode()`(
        type: String,
        value: String,
        expectedType: String
    ) {
        // When
        val decoded = testedCodec.decode(wire(type = type, value = value).toString())

        // Then
        assertThat(decoded.precomputed?.assignments?.flags?.get("flag")?.variationType).isEqualTo(expectedType)
    }

    @ParameterizedTest
    @CsvSource("integer,2", "float,2", "float,2.0", "float,1.5", "number,2", "number,1.5")
    fun `M preserve native numeric reads W decode() and encode()`(type: String, value: String) {
        // Given
        val expectedInt = FlagValueConverter.convert(value, type, Int::class)
        val expectedDouble = FlagValueConverter.convert(value, type, Double::class)

        // When
        val decoded = testedCodec.decode(wire(type = type, value = value).toString())
        val restored = testedCodec.decode(testedCodec.encode(decoded))

        // Then
        for (configuration in listOf(decoded, restored)) {
            val flag = checkNotNull(configuration.precomputed?.assignments?.flags?.get("flag"))
            val actualInt = FlagValueConverter.convert(flag.variationValue, flag.variationType, Int::class)
            val actualDouble = FlagValueConverter.convert(flag.variationValue, flag.variationType, Double::class)
            assertThat(actualInt.getOrNull()).isEqualTo(expectedInt.getOrNull())
            assertThat(actualInt.exceptionOrNull()?.javaClass).isEqualTo(expectedInt.exceptionOrNull()?.javaClass)
            assertThat(actualDouble.getOrNull()).isEqualTo(expectedDouble.getOrNull())
            assertThat(actualDouble.exceptionOrNull()?.javaClass).isEqualTo(expectedDouble.exceptionOrNull()?.javaClass)
            if (type == "float") assertThat(actualInt.exceptionOrNull()).isInstanceOf(TypeMismatchException::class.java)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["{\"x\":1e400}", "{\"nested\":[{\"x\":-1e400}]}", "null", "[]"])
    fun `M discard invalid extra logging without losing flags W decode() and encode()`(extraLogging: String) {
        // Given
        val fakeWire = wireWithFlagField("extraLogging", extraLogging)

        // When
        val decoded = assertDoesNotThrow<PortableFlagsConfiguration> { testedCodec.decode(fakeWire.toString()) }
        val restored = testedCodec.decode(testedCodec.encode(decoded))

        // Then
        for (configuration in listOf(decoded, restored)) {
            val precomputed = checkNotNull(configuration.precomputed)
            assertThat(precomputed.assignments.flags.keys).containsExactlyInAnyOrder("flag", "modified")
            assertThat(precomputed.flagErrors).isEmpty()
            assertThat(precomputed.assignments.flags["modified"]?.extraLogging?.length()).isZero()
            assertThat(precomputed.response).isEqualTo(fakeWire.getAsJsonObject("precomputed").get("response").asString)
        }
    }

    @Test
    fun `M preserve valid extra logging W decode()`() {
        // Given
        val fakeWire = wireWithFlagField("extraLogging", """{"nested":{"value":2},"items":[true,null]}""")

        // When
        val decoded = testedCodec.decode(fakeWire.toString())

        // Then
        val flag = checkNotNull(decoded.precomputed?.assignments?.flags?.get("modified"))
        assertThat(flag.extraLogging.getJSONObject("nested").getInt("value")).isEqualTo(2)
        assertThat(flag.extraLogging.getJSONArray("items").length()).isEqualTo(2)
    }

    @ParameterizedTest
    @CsvSource("254,true", "255,false", "50000,false")
    fun `M bound context depth without throwing W decode()`(depth: Int, accepted: Boolean) {
        // Given
        val response = wire().getAsJsonObject("precomputed").get("response")
        val fakeWire = """{"version":1,"precomputed":{"response":$response,"context":{"a":${nestedArray(depth)}}}}"""

        // When
        val decoded = assertDoesNotThrow<PortableFlagsConfiguration> { testedCodec.decode(fakeWire) }

        // Then
        assertThat(decoded.failure).isEqualTo(if (accepted) null else Failure.INVALID_PRECOMPUTED)
        assertThat(decoded.precomputed != null).isEqualTo(accepted)
        if (accepted) assertDoesNotThrow { testedCodec.encode(decoded) }
    }

    @ParameterizedTest
    @CsvSource("255,true", "256,false", "50000,false")
    fun `M isolate excessive value depth without throwing W decode() and encode()`(depth: Int, accepted: Boolean) {
        // Given
        val fakeWire = wireWithFlagField("variationValue", nestedArray(depth), "object")

        // When
        val decoded = assertDoesNotThrow<PortableFlagsConfiguration> { testedCodec.decode(fakeWire.toString()) }
        val encoded = assertDoesNotThrow<String> { testedCodec.encode(decoded) }
        val restored = testedCodec.decode(encoded)

        // Then
        for (configuration in listOf(decoded, restored)) {
            val precomputed = checkNotNull(configuration.precomputed)
            assertThat(precomputed.assignments.flags).containsKey("flag")
            assertThat(precomputed.assignments.flags.containsKey("modified")).isEqualTo(accepted)
            assertThat(precomputed.flagErrors.containsKey("modified")).isEqualTo(!accepted)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["extraLogging", "unknown"])
    fun `M ignore excessive optional nesting without throwing W decode() and encode()`(field: String) {
        // Given
        val fakeWire = wireWithFlagField(field, """{"deep":${nestedArray(50_000)}}""")

        // When
        val decoded = assertDoesNotThrow<PortableFlagsConfiguration> { testedCodec.decode(fakeWire.toString()) }
        val restored = testedCodec.decode(testedCodec.encode(decoded))

        // Then
        for (configuration in listOf(decoded, restored)) {
            val precomputed = checkNotNull(configuration.precomputed)
            assertThat(precomputed.assignments.flags.keys).containsExactlyInAnyOrder("flag", "modified")
            assertThat(precomputed.flagErrors).isEmpty()
            assertThat(precomputed.assignments.flags["modified"]?.extraLogging?.length()).isZero()
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = [Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY])
    fun `M omit non-finite fetchedAt W encode()`(fetchedAt: Double) {
        // Given
        val source = checkNotNull(testedCodec.decode(wire().toString()).precomputed)
        val configuration = PortableFlagsConfiguration(
            PortableFlagsConfiguration.Precomputed(
                source.response,
                null,
                fetchedAt,
                null,
                source.assignments,
                emptyMap()
            ),
            null
        )

        // When
        val encoded = testedCodec.encode(configuration)
        val restored = testedCodec.decode(encoded)

        // Then
        assertThat(json(encoded).getAsJsonObject("precomputed").has("fetchedAt")).isFalse()
        assertThat(restored.failure).isNull()
        assertThat(restored.precomputed?.fetchedAt).isNull()
    }

    @ParameterizedTest
    @CsvSource("true,true", "false,false", "'\"true\"',false", "1,false", "null,false", "missing,false")
    fun `M propagate strict consent W decode()`(consent: String, expected: Boolean) {
        // Given
        val fakeWire = wire()
        val entry = fakeWire.getAsJsonObject("precomputed")
        val response = json(entry.get("response").asString)
        val attributes = response.getAsJsonObject("data").getAsJsonObject("attributes")
        if (consent == "missing") {
            attributes.remove("observeFullEvaluationData")
        } else {
            attributes.add("observeFullEvaluationData", jsonValue(consent))
        }
        entry.addProperty("response", response.toString())

        // When
        val decoded = testedCodec.decode(fakeWire.toString())

        // Then
        val precomputed = checkNotNull(decoded.precomputed)
        assertThat(precomputed.assignments.observeFullEvaluationData).isEqualTo(expected)
        assertThat(precomputed.assignments.flags.values.map { it.observeFullEvaluationData }).containsOnly(expected)
    }

    @ParameterizedTest
    @ValueSource(strings = ["{}", "{\"obfuscated\":false}"])
    fun `M encode plaintext as version one W encode() {absent optional fields}`(metadata: String) {
        // Given
        val decoded = testedCodec.decode(wire(metadata = metadata).toString())

        // When
        val encoded = json(testedCodec.encode(decoded))

        // Then
        assertThat(encoded.get("version").asInt).isEqualTo(1)
        assertThat(encoded.getAsJsonObject("precomputed").keySet()).containsExactly("response")
    }

    @ParameterizedTest
    @ValueSource(strings = ["all-types-success", "type-mismatch-all-types"])
    fun `M preserve shared corpus assignments W decode() and encode()`(fixture: String) {
        // Given
        val fixtureJson = requireNotNull(javaClass.getResourceAsStream("/precomputed/$fixture.json"))
            .bufferedReader().use { json(it.readText()) }
        val response = fixtureJson.getAsJsonObject("response")
        val expectedFlags = response.getAsJsonObject("data").getAsJsonObject("attributes").getAsJsonObject("flags")
        val fakeWire = wire().apply { getAsJsonObject("precomputed").addProperty("response", response.toString()) }

        // When
        val decoded = testedCodec.decode(fakeWire.toString())
        val restored = testedCodec.decode(testedCodec.encode(decoded))

        // Then
        for (configuration in listOf(decoded, restored)) {
            assertThat(configuration.failure).isNull()
            val precomputed = checkNotNull(configuration.precomputed)
            assertThat(precomputed.flagErrors).isEmpty()
            assertThat(precomputed.assignments.flags.keys).containsExactlyInAnyOrderElementsOf(expectedFlags.keySet())
            expectedFlags.entrySet().forEach { (key, value) ->
                val expected = value.asJsonObject
                val flag = checkNotNull(precomputed.assignments.flags[key])
                assertThat(flag.variationType).isEqualTo(expected.get("variationType").asString)
                assertThat(flag.variationValue).isEqualTo(FlagsJson.stringValue(expected.get("variationValue")))
                assertThat(flag.allocationKey).isEqualTo(expected.get("allocationKey").asString)
                assertThat(flag.variationKey).isEqualTo(expected.get("variationKey").asString)
                assertThat(flag.reason).isEqualTo(expected.get("reason").asString)
                assertThat(flag.doLog).isEqualTo(expected.get("doLog").asBoolean)
            }
        }
    }

    private fun nestedArray(depth: Int): String = "[".repeat(depth) + "null" + "]".repeat(depth)

    // Avoid recursive JSON serialization while constructing intentionally excessive nesting.
    private fun wireWithFlagField(field: String, value: String, type: String = "boolean"): JsonObject {
        val fakeWire = wire(type = type, value = if (type == "object") "{}" else "true")
        val entry = fakeWire.getAsJsonObject("precomputed")
        val flag = json(entry.get("response").asString).getAsJsonObject("data")
            .getAsJsonObject("attributes").getAsJsonObject("flags").getAsJsonObject("flag")
        val original = flag.toString()
        flag.remove(field)
        val modified = flag.toString().dropLast(1) + """, "$field":$value}"""
        entry.addProperty(
            "response",
            """{"data":{"attributes":{"createdAt":0,"flags":{"flag":$original,"modified":$modified}}}}"""
        )
        return fakeWire
    }

    private fun wire(
        key: String = "flag",
        type: String = "boolean",
        value: String = "true",
        metadata: String = "{}"
    ): JsonObject {
        val attributes = json(
            """{"createdAt":"2026-01-01T00:00:00Z","observeFullEvaluationData":true,
              "evaluation_options":{"experimentation_opt_out":true},"flags":{
              "$key":{"allocationKey":"allocation","variationKey":"variant","variationType":"$type",
              "variationValue":$value,"reason":"TARGETING_MATCH","doLog":true,"serialId":42}}}"""
        )
        json(metadata).entrySet().forEach { (name, field) -> attributes.add(name, field) }
        val response = JsonObject().apply { add("data", JsonObject().apply { add("attributes", attributes) }) }
        return JsonObject().apply {
            addProperty("version", 1)
            add("precomputed", JsonObject().apply { addProperty("response", response.toString()) })
        }
    }

    private fun json(value: String) = jsonValue(value).asJsonObject

    private fun jsonValue(value: String) = JsonParser.parseString(value)

    private companion object {
        const val DIGEST = "9817872c144b018abd77e3915bd77e2c27f4f534dccff8ffaca27361e3a5e1ee"
        const val ENCODING = """{"obfuscated":true,"obfuscation":{
            "scheme":"flag-key-sha256-v1","salt":"000102030405060708090a0b0c0d0e0f"}}"""
    }
}
