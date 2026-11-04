/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.integration.FlagsClientTest.Companion.createStubSdkCore
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.httpResponse
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.model.ResolutionReason
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CanonicalPrecomputedTest {
    @ParameterizedTest(name = "{0}, restored={1}")
    @MethodSource("fixtures")
    fun `M satisfy canonical contract W setEvaluationContext() and resolve()`(
        path: String,
        restored: Boolean,
        forge: Forge
    ) {
        // Given
        val fixture = read(path).asJsonObject
        assumeTrue(applies(fixture), "Canonical platform exclusion: $path")
        val context = fixture.getAsJsonObject("context")
        val targetingKey = context["targetingKey"]
        require(targetingKey != null && targetingKey.isJsonPrimitive && targetingKey.asJsonPrimitive.isString) {
            "$path: Android requires a string targetingKey; null-context fixtures must exclude android upstream"
        }
        var core = createStubSdkCore(forge)
        val store = core.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        core.stubNetworkResponses { httpResponse(it, body = fixture["response"].toString()) }
        Flags.enable(
            FlagsConfiguration.Builder().useCustomFlagEndpoint("https://fixture.test/precompute-assignments").build(),
            core
        )
        var client = FlagsClient.Builder(sdkCore = core).build()
        val attributes = context.getAsJsonObject("attributes").entrySet().associate { it.key to it.value.asString }

        // When
        try {
            client.setEvaluationContext(EvaluationContext(targetingKey.asString, attributes))

            // Then
            assertThat(core.requestsSent()).hasSize(1)
            if (fixture.has("expectedResponseError")) {
                assertThat(fixture["expectedResponseError"].asString).isEqualTo("INVALID_RESPONSE")
                assertThat(client.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
            } else {
                assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
                if (restored) {
                    core.stopFeatures()
                    core = createStubSdkCore(forge)
                    core.stubDataStore(Feature.FLAGS_FEATURE_NAME, store)
                    core.stubNetworkResponses { null }
                    Flags.enable(FlagsConfiguration.Builder().build(), core)
                    client = FlagsClient.Builder(sdkCore = core).build()
                    assertThat(core.requestsSent()).isEmpty()
                }
                fixture.getAsJsonArray("evaluations").map { it.asJsonObject }.filter(::applies).forEach { evaluation ->
                    val key = evaluation["flag"].asString
                    val fallback = evaluation["defaultValue"]
                    val result = when (evaluation["variationType"].asString) {
                        "boolean" -> client.resolve(key, fallback.asBoolean)
                        "string" -> client.resolve(key, fallback.asString)
                        "integer" -> client.resolve(key, fallback.asInt)
                        "float" -> client.resolve(key, fallback.asDouble)
                        "object" -> client.resolve(key, Gson().fromJson(fallback, Map::class.java))
                        else -> error("Unsupported canonical getter: $evaluation")
                    }
                    if (restored && result.errorCode == null) {
                        assertThat(result.reason).isEqualTo(ResolutionReason.CACHED)
                    }
                    val actual = JsonObject().apply {
                        add("value", Gson().toJsonTree(result.value))
                        addProperty("reason", if (result.errorCode != null) "ERROR" else result.reason.toString())
                        addProperty("errorCode", result.errorCode?.toString())
                        addProperty("variantKey", result.variant)
                    }
                    evaluation.getAsJsonObject("expectedResult").entrySet().forEach { (field, expected) ->
                        if (restored && field == "reason" && result.errorCode == null) return@forEach
                        assertThat(actual[field]).describedAs("$path: $key: $field").isEqualTo(expected)
                    }
                }
            }
            core.stopFeatures()
            val expectations = fixture.getAsJsonObject("expectations")
            assertEvents(
                core.eventsWritten(Feature.FLAGS_FEATURE_NAME).map {
                    JsonParser.parseString(it.eventData)
                },
                expectations,
                "exposures"
            )
            assertEvents(
                core.eventsWritten(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).map {
                    JsonParser.parseString(it.eventData)
                },
                expectations,
                "evaluationEvents"
            )
        } finally {
            core.stopFeatures()
        }
    }

    private fun assertEvents(events: List<JsonElement>, expectations: JsonObject, field: String) {
        val remaining = events.toMutableList()
        expectations.getAsJsonArray(field)?.map { it.asJsonObject }?.filter(::applies)?.forEach { matcher ->
            val fields = JsonObject().apply {
                matcher.entrySet().filterNot { it.key.startsWith("_") }.forEach { add(it.key, it.value) }
            }
            repeat(matcher["_count"].asInt) {
                val index = remaining.indexOfFirst { matches(it, fields) }
                assertThat(
                    index
                ).describedAs("Missing event for $matcher; remaining $remaining").isGreaterThanOrEqualTo(0)
                remaining.removeAt(index)
            }
        }
        if (expectations["noUnmatchedEvents"]?.asBoolean == true) {
            assertThat(remaining).describedAs("Unmatched $field").isEmpty()
        }
    }

    private fun matches(actual: JsonElement?, expected: JsonElement): Boolean = when {
        expected.isJsonNull -> actual == null || actual.isJsonNull
        expected.isJsonObject -> actual?.isJsonObject == true && expected.asJsonObject.entrySet().all {
            matches(actual.asJsonObject[it.key], it.value)
        }
        else -> actual == expected
    }

    companion object {
        private fun read(path: String): JsonElement = JsonParser.parseString(
            checkNotNull(CanonicalPrecomputedTest::class.java.getResource("/precomputed/$path")) {
                "Missing fixture: $path"
            }.readText()
        )

        private fun applies(value: JsonObject): Boolean {
            fun contains(field: String) =
                value.getAsJsonArray(field)?.any { it.asJsonObject["platform"].asString == "android" } == true
            return !contains("_skip") && (!value.has("_include") || contains("_include"))
        }

        @JvmStatic
        fun fixtures(): List<Arguments> {
            val paths = read("index.json").asJsonArray.map { it.asString }
            check(paths.isNotEmpty()) { "Empty canonical fixture corpus" }
            check(paths.distinct().size == paths.size) { "Duplicate fixture index entries" }
            check(paths.any { applies(read(it).asJsonObject) }) { "All canonical fixtures excluded" }
            return paths.flatMap { path ->
                val fixture = read(path).asJsonObject
                val modes = if (applies(fixture) &&
                    !fixture.has("expectedResponseError")
                ) {
                    listOf(false, true)
                } else {
                    listOf(false)
                }
                modes.map { Arguments.of(path, it) }
            }
        }
    }
}
