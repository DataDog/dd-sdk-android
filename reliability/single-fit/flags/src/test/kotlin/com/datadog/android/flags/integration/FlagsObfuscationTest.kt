/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags._FlagsInternalProxy
import com.datadog.android.flags.integration.FlagsClientTest.Companion.createStubSdkCore
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.httpResponse
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.model.ResolutionReason
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlagsObfuscationTest {
    private lateinit var stubSdkCore: StubSDKCore

    @StringForgery
    lateinit var fakeTargetingKey: String

    @BeforeEach
    fun setUp(forge: Forge) {
        stubSdkCore = createStubSdkCore(forge)
        stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
    }

    @ParameterizedTest
    @ValueSource(strings = ["plaintext", "encoded"])
    fun `M preserve values and errors W resolve() {plaintext and obfuscated responses}`(fixture: String) {
        // Given
        stubAssignments(fixture)
        val client = createClient()

        // When
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))

        // Then
        assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        assertThat(stubSdkCore.requestsSent().single().header("X-DD-FEATURE-FLAGS-CAPABILITIES"))
            .isEqualTo("assignment-encoding-flag-key-256-v1")
        assertThat(client.resolveBooleanValue("flag", false)).isTrue()
        assertThat(client.resolveStringValue("string", "default")).isEqualTo("visible")
        assertThat(client.resolveIntValue("integer", -1)).isEqualTo(42)
        assertThat(client.resolveDoubleValue("number", -1.0)).isEqualTo(12.5)
        assertThat(client.resolveStructureValue("object", emptyMap())).containsKey("nested")
        assertThat(client.resolve("integer", "default").errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
        assertThat(client.resolve("bad", false).errorCode).isEqualTo(ErrorCode.PARSE_ERROR)
        assertThat(client.resolve("missing", false).errorCode).isEqualTo(ErrorCode.FLAG_NOT_FOUND)
        assertThat(client.resolveBooleanValue("new-flag", false)).isTrue()
        assertThat(client.resolve("future", false).value).isFalse()
        assertThat(client.resolve("flag", false).flagMetadata)
            .containsEntry("allocationKey", "allocation").containsEntry("test", "metadata")
        assertThat(client.resolve("\ud800", false).errorCode).isEqualTo(ErrorCode.FLAG_NOT_FOUND)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M keep original telemetry keys W resolveBooleanValue() {salt changes}`(doLog: Boolean) {
        // Given
        val client = createClient()
        val context = EvaluationContext(fakeTargetingKey)

        // When
        listOf("encoded", "refreshed", "encoded").forEach { fixture ->
            val response = JSONObject(readFixture(fixture))
            val flags = response.getJSONObject("data").getJSONObject("attributes").getJSONObject("flags")
            flags.keys().forEach { flags.getJSONObject(it).put("doLog", doLog) }
            stubSdkCore.stubNetworkResponses { httpResponse(it, body = response.toString()) }
            client.setEvaluationContext(context)
            client.resolveBooleanValue("flag", false)
        }
        stubSdkCore.stopFeatures()

        // Then
        val exposures = stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)
        assertThat(exposures).hasSize(if (doLog) 1 else 0)
        if (doLog) {
            val exposure = JSONObject(exposures.single().eventData)
            assertThat(exposure.getJSONObject("flag").getString("key")).isEqualTo("flag")
            assertThat(exposure.getJSONObject("subject").getString("id")).isEqualTo(fakeTargetingKey)
            assertThat(exposure.getLong("serial_id")).isEqualTo(123)
        }
        val evaluation =
            JSONObject(stubSdkCore.eventsWritten(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).single().eventData)
        assertThat(evaluation.getJSONObject("flag").getString("key")).isEqualTo("flag")
        assertThat(evaluation.getInt("evaluation_count")).isEqualTo(3)
    }

    @Test
    fun `M restore latest salt and value W build() {restart without network}`(forge: Forge) {
        // Given
        val store = stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        val client = createClient()
        stubAssignments("encoded")
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))
        assertThat(client.resolveBooleanValue("flag", false)).isTrue()
        stubAssignments("refreshed")
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))
        assertThat(client.resolveBooleanValue("flag", true)).isFalse()
        val restartedCore = createStubSdkCore(forge)
        restartedCore.stubDataStore(Feature.FLAGS_FEATURE_NAME, store)
        restartedCore.stubNetworkResponses { null }

        // When
        val restoredClient = createClient(restartedCore)
        val details = restoredClient.resolve("flag", true)

        // Then
        assertThat(details.value).isFalse()
        assertThat(details.reason).isEqualTo(ResolutionReason.CACHED)
        assertThat(restartedCore.requestsSent()).isEmpty()
        assertThat(_FlagsInternalProxy(restoredClient).getFlagAssignmentsSnapshot()).isEmpty()
        val exposure = JSONObject(restartedCore.eventsWritten(Feature.FLAGS_FEATURE_NAME).single().eventData)
        assertThat(exposure.getJSONObject("flag").getString("key")).isEqualTo("flag")
        assertThat(exposure.getJSONObject("subject").getString("id")).isEqualTo(fakeTargetingKey)
    }

    @ParameterizedTest
    @ValueSource(strings = ["unknown-scheme", "invalid-salt", "plaintext-key"])
    fun `M retain valid assignments W setEvaluationContext() {invalid encoded response}`(
        failure: String,
        forge: Forge
    ) {
        // Given
        val store = stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        stubAssignments("encoded")
        val client = createClient()
        val context = EvaluationContext(fakeTargetingKey)
        client.setEvaluationContext(context)
        val response = JSONObject(readFixture("refreshed"))
        val attributes = response.getJSONObject("data").getJSONObject("attributes")
        when (failure) {
            "unknown-scheme" -> attributes.getJSONObject("obfuscation").put("scheme", "unsupported")
            "invalid-salt" -> attributes.getJSONObject("obfuscation").put("salt", "bad-salt")
            "plaintext-key" -> {
                val flags = attributes.getJSONObject("flags")
                flags.put("plaintext", flags.get(flags.keys().next()))
            }
        }
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response.toString()) }

        // When
        client.setEvaluationContext(context)

        // Then
        assertThat(client.state.getCurrentState()).isInstanceOf(FlagsClientState.Stale::class.java)
        assertThat(client.resolveBooleanValue("flag", false)).isTrue()
        val restartedCore = createStubSdkCore(forge)
        restartedCore.stubDataStore(Feature.FLAGS_FEATURE_NAME, store)
        restartedCore.stubNetworkResponses { null }
        assertThat(createClient(restartedCore).resolveBooleanValue("flag", false)).isTrue()
    }

    @Test
    fun `M hide hashed snapshot keys W getFlagAssignmentsSnapshot() {encoded response}`() {
        // Given
        val client = createClient()
        stubAssignments("encoded")
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))

        // When
        val encodedSnapshot = _FlagsInternalProxy(client).getFlagAssignmentsSnapshot()

        // Then
        assertThat(encodedSnapshot).isEmpty()

        // When
        stubAssignments("plaintext")
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))

        // Then
        assertThat(_FlagsInternalProxy(client).getFlagAssignmentsSnapshot()).containsKey("flag")
    }

    private fun stubAssignments(fixture: String) {
        val response = readFixture(fixture)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response) }
    }

    private fun createClient(sdkCore: StubSDKCore = stubSdkCore): FlagsClient {
        Flags.enable(
            FlagsConfiguration.Builder()
                .useCustomFlagEndpoint("https://flags.example.com/precompute-assignments")
                .build(),
            sdkCore
        )
        return FlagsClient.Builder(sdkCore = sdkCore).build()
    }

    companion object {
        internal fun readFixture(name: String): String =
            checkNotNull(FlagsObfuscationTest::class.java.getResource("/obfuscation/$name.json")).readText()
    }
}
