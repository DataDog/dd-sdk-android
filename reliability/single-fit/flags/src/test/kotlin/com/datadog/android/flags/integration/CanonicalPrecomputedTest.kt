/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.core.stub.StubDataStoreHandler
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.integration.FlagsClientTest.Companion.createStubSdkCore
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.CanonicalFixtureMatcher
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CanonicalPrecomputedTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidResponseFixtures")
    fun `M reject the response W setEvaluationContext() { invalid canonical response }`(path: String, forge: Forge) {
        // Given
        val fixture = applicableFixture(path)
        val core = fixtureCore(fixture, forge, StubDataStoreHandler())
        val client = FlagsClient.Builder(sdkCore = core).build()

        try {
            // When
            client.setEvaluationContext(evaluationContext(path, fixture))

            // Then
            assertThat(core.requestsSent()).hasSize(1)
            assertThat(fixture["expectedResponseError"].asString).isEqualTo("INVALID_RESPONSE")
            assertThat(client.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
            assertFixtureEvents(path, fixture, core)
        } finally {
            core.stopFeatures()
        }
    }

    @ParameterizedTest(name = "{0}, restored={1}")
    @MethodSource("evaluationFixtures")
    fun `M satisfy canonical contract W setEvaluationContext() and resolve()`(
        path: String,
        restored: Boolean,
        forge: Forge
    ) {
        // Given
        val fixture = applicableFixture(path)
        val store = StubDataStoreHandler()
        var core = fixtureCore(fixture, forge, store)
        var client = FlagsClient.Builder(sdkCore = core).build()

        try {
            // When
            client.setEvaluationContext(evaluationContext(path, fixture))
            assertThat(core.requestsSent()).hasSize(1)
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

            // Then
            assertEvaluations(path, fixture, client, restored)
            assertFixtureEvents(path, fixture, core)
        } finally {
            core.stopFeatures()
        }
    }

    private fun applicableFixture(path: String): JsonObject {
        val fixture = read(path).asJsonObject
        if (!applies(fixture)) excluded.add("$path (fixture)")
        assumeTrue(applies(fixture), "Canonical platform exclusion: $path")
        return fixture
    }

    private fun fixtureCore(fixture: JsonObject, forge: Forge, store: DataStoreHandler? = null): StubSDKCore {
        val core = createStubSdkCore(forge)
        store?.let { core.stubDataStore(Feature.FLAGS_FEATURE_NAME, it) }
        core.stubNetworkResponses { httpResponse(it, body = fixture["response"].toString()) }
        Flags.enable(
            FlagsConfiguration.Builder().useCustomFlagEndpoint("https://fixture.test/precompute-assignments").build(),
            core
        )
        return core
    }

    private fun evaluationContext(path: String, fixture: JsonObject): EvaluationContext {
        val context = fixture.getAsJsonObject("context")
        val targetingKey = context["targetingKey"]
        require(targetingKey != null && targetingKey.isJsonPrimitive && targetingKey.asJsonPrimitive.isString) {
            "$path: Android requires a string targetingKey; null-context fixtures must exclude android upstream"
        }
        val attributes = context.getAsJsonObject("attributes").entrySet().associate { it.key to it.value.asString }
        return EvaluationContext(targetingKey.asString, attributes)
    }

    private fun assertEvaluations(path: String, fixture: JsonObject, client: FlagsClient, restored: Boolean) {
        val evaluations = fixture.getAsJsonArray("evaluations").map { it.asJsonObject }
        evaluations.filterNot { applies(it) }.forEach {
            excluded.add("$path: evaluation ${it["flag"]}")
        }
        evaluations.filter { applies(it) }.forEach { evaluation ->
            val key = evaluation["flag"].asString
            executed.add("$path, restored=$restored: ${evaluation["flag"]}")
            val fallback = evaluation["defaultValue"]
            val result = when (evaluation["variationType"].asString) {
                "boolean" -> client.resolve(key, fallback.asBoolean)
                "string" -> client.resolve(key, fallback.asString)
                "integer" -> client.resolve(key, fallback.asInt)
                "float" -> client.resolve(key, fallback.asDouble)
                "object" -> client.resolve(key, Gson().fromJson(fallback, Map::class.java))
                else -> error("Unsupported canonical getter: $evaluation")
            }
            // A restored snapshot reports a successful result with restoredReason, or CACHED when the field is absent.
            val restoredReason = evaluation["restoredReason"]?.asString ?: ResolutionReason.CACHED.name
            if (restored && result.errorCode == null) {
                assertThat(result.reason?.name)
                    .describedAs("$path: $key: restoredReason")
                    .isEqualTo(restoredReason)
            }
            val actual = JsonObject().apply {
                add("value", Gson().toJsonTree(result.value))
                addProperty("reason", result.reason?.name)
                addProperty("errorCode", result.errorCode?.toString())
                addProperty("variantKey", result.variant)
            }
            evaluation.getAsJsonObject("expectedResult").entrySet().forEach { (field, expected) ->
                if (restored && field == "reason" && result.errorCode == null) return@forEach
                assertThat(CanonicalFixtureMatcher.jsonEquals(actual[field], expected))
                    .describedAs("$path: $key: $field: expected $expected, actual ${actual[field]}")
                    .isTrue()
            }
        }
    }

    private fun assertFixtureEvents(path: String, fixture: JsonObject, core: StubSDKCore) {
        core.stopFeatures()
        val expectations = fixture.getAsJsonObject("expectations")
        assertEvents(
            path,
            core.eventsWritten(Feature.FLAGS_FEATURE_NAME).map { JsonParser.parseString(it.eventData) },
            expectations,
            "exposures"
        )
        assertEvents(
            path,
            core.eventsWritten(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).map { JsonParser.parseString(it.eventData) },
            expectations,
            "evaluationEvents"
        )
    }

    private fun assertEvents(path: String, events: List<JsonElement>, expectations: JsonObject, field: String) {
        expectations.getAsJsonArray(field)?.map { it.asJsonObject }?.filterNot { applies(it) }?.forEach {
            excluded.add("$path: $field matcher $it")
        }
        val remaining = CanonicalFixtureMatcher.consume(events, expectations, field) { applies(it) }
        if (expectations["noUnmatchedEvents"]?.asBoolean == true) {
            assertThat(remaining).describedAs("Unmatched $field").isEmpty()
        }
    }

    companion object {
        private val executed = ConcurrentLinkedQueue<String>()
        private val excluded = ConcurrentHashMap.newKeySet<String>()

        @JvmStatic
        @AfterAll
        fun report() {
            val revision = read("SOURCE.json").asJsonObject["commit"].asString
            println(
                buildString {
                    appendLine("Canonical precomputed fixtures at ffe-system-test-data@$revision")
                    appendLine("Executed evaluations: ${executed.size}")
                    executed.sorted().forEach { appendLine("  $it") }
                    appendLine("Exclusions: ${excluded.size}")
                    excluded.sorted().forEach { appendLine("  $it") }
                }
            )
        }

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

        private fun paths(): List<String> {
            val paths = read("index.json").asJsonArray.map { it.asString }
            check(paths.isNotEmpty()) { "Empty canonical fixture corpus" }
            check(paths.distinct().size == paths.size) { "Duplicate fixture index entries" }
            check(paths.any { applies(read(it).asJsonObject) }) { "All canonical fixtures excluded" }
            return paths
        }

        @JvmStatic
        fun invalidResponseFixtures(): List<Arguments> =
            paths().filter { read(it).asJsonObject.has("expectedResponseError") }.map { Arguments.of(it) }

        // An excluded fixture runs once so that the report lists it; applicable ones also run from the cache.
        @JvmStatic
        fun evaluationFixtures(): List<Arguments> = paths()
            .filterNot { read(it).asJsonObject.has("expectedResponseError") }
            .flatMap { path ->
                val modes = if (applies(read(path).asJsonObject)) listOf(false, true) else listOf(false)
                modes.map { Arguments.of(path, it) }
            }
    }
}
