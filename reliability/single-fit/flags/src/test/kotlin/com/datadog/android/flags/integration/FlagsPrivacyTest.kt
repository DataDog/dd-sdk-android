/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestExecutionContext
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags._FlagsInternalProxy
import com.datadog.android.flags.integration.FlagsClientTest.Companion.createStubSdkCore
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.FakeAssignment
import com.datadog.android.flags.integration.tests.utils.bodyAsJson
import com.datadog.android.flags.integration.tests.utils.httpResponse
import com.datadog.android.flags.integration.tests.utils.precomputedAssignmentsBody
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.UnparsedFlag
import com.datadog.android.flags.model.UnparsedFlagWithConsent
import com.datadog.android.flags.openfeature.asOpenFeatureProvider
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.Value
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class FlagsPrivacyTest {
    private lateinit var stubSdkCore: StubSDKCore

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = createStubSdkCore(forge)
        stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
    }

    @AfterEach
    fun `tear down`() = runTest {
        OpenFeatureAPI.shutdown()
        stubSdkCore.stopFeatures()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M enforce five state consent on the wire W resolve + stop`(fakeOpenFeature: Boolean) = runTest {
        // Given
        val client = buildClient()
        val provider = client.asOpenFeatureProvider()
        val fakeConsentValues = listOf(null, false, JSONObject.NULL, "true", true)

        // When
        fakeConsentValues.forEachIndexed { index, consent ->
            stubAssignments(consent)
            val key = "flag-$index"
            if (fakeOpenFeature) {
                if (index == 0) {
                    OpenFeatureAPI.setProviderAndWait(
                        provider,
                        ImmutableContext(TARGETING_KEY, mapOf("email" to Value.String(CONTEXT_CANARY))),
                        UnconfinedTestDispatcher(testScheduler)
                    )
                } else {
                    provider.onContextSet(null, ImmutableContext(TARGETING_KEY))
                }
                repeat(2) { assertThat(OpenFeatureAPI.getClient().getBooleanValue(key, false)).isTrue() }
            } else {
                client.setEvaluationContext(EvaluationContext(TARGETING_KEY, mapOf("email" to CONTEXT_CANARY)))
                repeat(2) { assertThat(client.resolveBooleanValue(key, false)).isTrue() }
            }
        }
        stubSdkCore.stopFeatures()
        val wire = wireBody()
        val events = wire.lineSequence().filter { it.isNotBlank() }.map(::JSONObject).toList()

        // Then
        assertThat(events).hasSize(5)
        events.forEachIndexed { index, event ->
            assertThat(event.getString("targeting_key")).isEqualTo(if (index == 4) TARGETING_KEY else HASHED_KEY)
            assertThat(event.getInt("evaluation_count")).isEqualTo(2)
            assertThat(event.getJSONObject("context").has("evaluation")).isFalse()
            assertThat(event.has("error")).isFalse()
        }
        assertThat(wire).doesNotContain(CONTEXT_CANARY)
        assertThat(wireBody(attempt = 2)).isEqualTo(wire)
        assertThat(
            stubSdkCore.requestsSent().first().bodyAsJson()
                .getJSONObject("data").getJSONObject("attributes")
                .getJSONObject("subject").getString("targeting_key")
        ).isEqualTo(TARGETING_KEY)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M keep evaluation time consent and errors W resolve + refresh + stop`(fakeOpenFeature: Boolean) = runTest {
        // Given
        val client = buildClient()
        val provider = client.asOpenFeatureProvider()
        stubAssignments(false)
        OpenFeatureAPI.setProviderAndWait(
            provider,
            ImmutableContext(TARGETING_KEY),
            UnconfinedTestDispatcher(testScheduler)
        )

        // When
        listOf(false, true, false).forEach { consent ->
            stubAssignments(consent)
            client.setEvaluationContext(EvaluationContext(TARGETING_KEY))
            if (fakeOpenFeature) {
                val ofClient = OpenFeatureAPI.getClient()
                ofClient.getBooleanValue("flag-0", false)
                ofClient.getBooleanValue("missing", false)
                ofClient.getBooleanValue("hostile", false)
                ofClient.getIntegerValue("broken", 0)
            } else {
                client.resolveBooleanValue("flag-0", false)
                client.resolveBooleanValue("missing", false)
                client.resolveBooleanValue("hostile", false)
                client.resolveIntValue("broken", 0)
            }
        }
        stubSdkCore.stopFeatures()
        val events = wireBody().lineSequence().filter { it.isNotBlank() }.map(::JSONObject).toList()

        // Then
        assertThat(events).hasSize(8)
        assertThat(events.sumOf { it.getInt("evaluation_count") }).isEqualTo(12)
        listOf(HASHED_KEY to 2, TARGETING_KEY to 1).forEach { (targetingKey, count) ->
            val group = events.filter { it.getString("targeting_key") == targetingKey }
            assertThat(group).hasSize(4)
            assertThat(group.map { it.getInt("evaluation_count") }).containsOnly(count)
            assertThat(group.filter { it.has("error") }.map { it.getJSONObject("error").getString("message") })
                .containsExactlyInAnyOrder("FLAG_NOT_FOUND", "TYPE_MISMATCH", "PARSE_ERROR")
        }
        assertThat(wireBody()).doesNotContain(ERROR_CANARY)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M preserve consent offline W build + resolve + stop`(fakeConsent: Boolean, forge: Forge) {
        // Given
        val dataStore = stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        stubAssignments(fakeConsent)
        buildClient().setEvaluationContext(EvaluationContext(TARGETING_KEY))
        stubSdkCore.stopFeatures()
        stubSdkCore = createStubSdkCore(forge)
        stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME, dataStore)
        stubSdkCore.stubNetworkResponses { null }
        val client = buildClient()

        // When
        val details = client.resolve("flag-0", false)
        val missing = client.resolve("missing", false)
        client.setEvaluationContext(EvaluationContext(TARGETING_KEY))
        client.resolveBooleanValue("flag-0", false)
        stubSdkCore.stopFeatures()

        // Then
        assertThat(details.value).isTrue()
        assertThat(details.flagMetadata).containsEntry("__dd_observe_full_evaluation_data", fakeConsent)
        assertThat(missing.flagMetadata).containsEntry("__dd_observe_full_evaluation_data", fakeConsent)
        val events = wireBody().lineSequence().filter { it.isNotBlank() }.map(::JSONObject).toList()
        assertThat(events.map { it.getString("targeting_key") })
            .containsOnly(if (fakeConsent) TARGETING_KEY else HASHED_KEY)
        assertThat(events.sumOf { it.getInt("evaluation_count") }).isEqualTo(3)
    }

    @ParameterizedTest
    @ValueSource(strings = ["\uD800", "\uDC00", "a\uD800b"])
    fun `M omit malformed cached keys without losing counts W build + resolve + stop`(
        fakeKey: String,
        forge: Forge
    ) {
        // Given
        val dataStore = stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        stubAssignments(false)
        buildClient().setEvaluationContext(EvaluationContext(fakeKey))
        stubSdkCore.stopFeatures()
        stubSdkCore = createStubSdkCore(forge)
        stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME, dataStore)
        stubSdkCore.stubNetworkResponses { null }
        val client = buildClient()

        // When
        val success = client.resolve("flag-0", false)
        val missing = client.resolve("missing", false)
        client.setEvaluationContext(EvaluationContext(fakeKey))
        val afterFailedRefresh = client.resolveBooleanValue("flag-0", false)
        stubSdkCore.stopFeatures()
        val events = wireBody().lineSequence().filter { it.isNotBlank() }.map(::JSONObject).toList()

        // Then
        assertThat(success.value).isTrue()
        assertThat(missing.value).isFalse()
        assertThat(afterFailedRefresh).isTrue()
        assertThat(success.flagMetadata).containsEntry("__dd_observe_full_evaluation_data", false)
        assertThat(missing.flagMetadata).containsEntry("__dd_observe_full_evaluation_data", false)
        assertThat(events).hasSize(2)
        assertThat(events.map { it.has("targeting_key") }).containsOnly(false)
        assertThat(events.sumOf { it.getInt("evaluation_count") }).isEqualTo(3)
        val error = events.single { it.has("error") }
        assertThat(error.getJSONObject("error").getString("message")).isEqualTo("FLAG_NOT_FOUND")
        assertThat(error.getInt("evaluation_count")).isEqualTo(1)
    }

    @Test
    fun `M retain empty and omit malformed or missing keys W resolve + stop`() {
        // Given
        val client = buildClient()
        stubAssignments(false)

        // When
        client.resolveBooleanValue("not-ready", false)
        listOf("", "\uD800", "\uDC00").forEachIndexed { index, key ->
            client.setEvaluationContext(EvaluationContext(key))
            client.resolveBooleanValue("flag-$index", false)
        }
        stubSdkCore.stopFeatures()
        val events = wireBody().lineSequence().filter { it.isNotBlank() }.map(::JSONObject).toList()

        // Then
        assertThat(events).hasSize(4)
        assertThat(events[0].has("targeting_key")).isFalse()
        assertThat(events[0].getJSONObject("error").getString("message")).isEqualTo("PROVIDER_NOT_READY")
        assertThat(events[1].getString("targeting_key")).isEmpty()
        assertThat(events.drop(2).map { it.has("targeting_key") }).containsOnly(false)
    }

    @Test
    fun `M protect retained native snapshot W trackFlagSnapshotEvaluation + stop`() {
        // Given
        stubAssignments(false)
        val client = buildClient()
        val context = EvaluationContext(TARGETING_KEY)
        client.setEvaluationContext(context)
        val proxy = _FlagsInternalProxy(client)
        val oldFlag = checkNotNull(proxy.getFlagAssignmentsSnapshot()["flag-0"])
        stubAssignments(true)
        client.setEvaluationContext(context)

        // When
        proxy.trackFlagSnapshotEvaluation("flag-0", oldFlag, context)
        stubSdkCore.stopFeatures()

        // Then
        assertThat((oldFlag as UnparsedFlagWithConsent).observeFullEvaluationData).isFalse()
        assertThat(JSONObject(wireBody()).getString("targeting_key")).isEqualTo(HASHED_KEY)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M honor tracking disable W resolve + stop`(fakeConsent: Boolean) {
        // Given
        stubAssignments(fakeConsent)
        val client = buildClient(trackEvaluations = false)
        client.setEvaluationContext(EvaluationContext(TARGETING_KEY))

        // When
        client.resolveBooleanValue("flag-0", false)
        client.resolveBooleanValue("missing", false)
        stubSdkCore.stopFeatures()

        // Then
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M preserve object metadata W getObjectEvaluation`(fakeConsent: Boolean) = runTest {
        // Given
        stubAssignments(fakeConsent)
        val client = buildClient()
        client.setEvaluationContext(EvaluationContext(TARGETING_KEY))
        val provider = client.asOpenFeatureProvider()

        // When
        val success = provider.getObjectEvaluation("object", Value.Structure(emptyMap()), null)
        val missing = provider.getObjectEvaluation("missing", Value.Structure(emptyMap()), null)

        // Then
        assertThat(success.metadata.getBoolean("__dd_observe_full_evaluation_data")).isEqualTo(fakeConsent)
        assertThat(missing.metadata.getBoolean("__dd_observe_full_evaluation_data")).isEqualTo(fakeConsent)
        assertThat(success.errorCode).isNull()
        assertThat(missing.errorCode?.name).isEqualTo("FLAG_NOT_FOUND")
    }

    @Test
    fun `M default old bridge implementations to protected W trackFlagSnapshotEvaluation + stop`() {
        // Given
        stubAssignments(true)
        val client = buildClient()
        client.setEvaluationContext(EvaluationContext(TARGETING_KEY))
        val proxy = _FlagsInternalProxy(client)
        val assignment = checkNotNull(proxy.getFlagAssignmentsSnapshot()["flag-0"])
        val oldBridgeFlag = object : UnparsedFlag by assignment {}

        // When
        proxy.trackFlagSnapshotEvaluation("flag-0", oldBridgeFlag, EvaluationContext(TARGETING_KEY))
        stubSdkCore.stopFeatures()

        // Then
        assertThat(JSONObject(wireBody()).getString("targeting_key")).isEqualTo(HASHED_KEY)
    }

    @Test
    fun `M preserve consent without assignments W setEvaluationContext + resolve + stop`() {
        // Given
        val client = buildClient()
        val response = JSONObject(precomputedAssignmentsBody(emptyMap()))
        response.getJSONObject("data").getJSONObject("attributes").put("observeFullEvaluationData", true)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response.toString()) }

        // When
        client.setEvaluationContext(EvaluationContext(TARGETING_KEY))
        val result = client.resolve("missing", false)
        stubSdkCore.stopFeatures()

        // Then
        assertThat(result.flagMetadata).containsEntry("__dd_observe_full_evaluation_data", true)
        val event = JSONObject(wireBody())
        assertThat(event.getString("targeting_key")).isEqualTo(TARGETING_KEY)
        assertThat(event.getJSONObject("error").getString("message")).isEqualTo("FLAG_NOT_FOUND")
    }

    private fun buildClient(trackEvaluations: Boolean = true): FlagsClient {
        Flags.enable(
            FlagsConfiguration.Builder()
                .useCustomFlagEndpoint("https://flags.example.test/precompute-assignments")
                .trackEvaluations(trackEvaluations)
                .trackExposures(false)
                .build(),
            stubSdkCore
        )
        return FlagsClient.Builder(sdkCore = stubSdkCore).build()
    }

    private fun stubAssignments(consent: Any?) {
        val flag = FakeAssignment("boolean", true, "variant", "allocation", doLog = false)
        val assignments = (0..4).associate { "flag-$it" to flag } + mapOf(
            "object" to flag.copy(variationType = "object", variationValue = JSONObject().put("enabled", true)),
            "hostile" to flag.copy(variationType = "string", variationValue = ERROR_CANARY),
            "broken" to flag.copy(variationType = "integer", variationValue = ERROR_CANARY)
        )
        val response = JSONObject(precomputedAssignmentsBody(assignments))
        response.getJSONObject("data").getJSONObject("attributes").put("observeFullEvaluationData", consent)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response.toString()) }
    }

    private fun wireBody(attempt: Int = 1): String {
        val feature = checkNotNull(stubSdkCore.getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME))
            .unwrap<StorageBackedFeature>()
        val batch = stubSdkCore.eventsWritten(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)
            .map { RawBatchEvent(it.eventData.toByteArray(Charsets.UTF_8)) }
        val request = checkNotNull(
            feature.requestFactory.create(
                stubSdkCore.getDatadogContext(emptySet()),
                RequestExecutionContext(attempt, if (attempt == 1) null else 503),
                batch,
                null
            )
        )
        return request.body.toString(Charsets.UTF_8)
    }

    private companion object {
        const val TARGETING_KEY = "jane.doe@datadoghq.com"
        const val HASHED_KEY = "sha256_b4698f9b6d186781fa8dc59e533578fa2d8379a46b1cf6db85cda6aa9c99e51b"
        const val CONTEXT_CANARY = "private-context-canary@example.com"
        const val ERROR_CANARY = "private-error-canary@example.com"
    }
}
