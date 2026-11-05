/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags._FlagsInternalProxy
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.FakeAssignment
import com.datadog.android.flags.integration.tests.utils.FlagsTelemetryTestFixture
import com.datadog.android.flags.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.flags.integration.tests.utils.httpResponse
import com.datadog.android.flags.integration.tests.utils.precomputedAssignmentsBody
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ForgeExtension::class, TestConfigurationExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
internal class FlagsBridgePrivacyTest {
    private lateinit var fixture: FlagsTelemetryTestFixture

    @BeforeEach
    fun `set up`(forge: Forge) {
        fixture = FlagsTelemetryTestFixture(forge)
    }

    @AfterEach
    fun `tear down`() {
        fixture.close()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain flag consent and protect missing flags W trackFlagEvaluationEvent {legacy hook}`(
        fakeConsent: Boolean
    ) {
        // Given
        assignments(fakeConsent)
        val client = FlagsClient.Builder(sdkCore = fixture.core).build()
        val context = EvaluationContext(TARGETING_KEY, mapOf("email" to CONTEXT_CANARY))
        client.setEvaluationContext(context)
        val proxy = _FlagsInternalProxy(client)
        val flag = checkNotNull(proxy.getFlagAssignmentsSnapshot()["enabled"])
        assignments(!fakeConsent)
        client.setEvaluationContext(context)

        // When
        proxy.trackFlagEvaluationEvent("enabled", flag, context, null)
        proxy.trackFlagEvaluationEvent("enabled", flag, context, "TYPE_MISMATCH")
        proxy.trackFlagEvaluationEvent("missing", null, context, "FLAG_NOT_FOUND")
        fixture.flush()
        val events = fixture.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)

        // Then
        assertThat(events).hasSize(3)
        assertThat(events.map { it["evaluation_count"].asInt }).containsOnly(1)
        val assigned = events.filter { it["flag"].asJsonObject["key"].asString == "enabled" }
        assertThat(assigned.map { it["targeting_key"].asString })
            .containsOnly(if (fakeConsent) TARGETING_KEY else HASHED_KEY)
        assertThat(assigned.single { it.has("error") }["error"].asJsonObject["message"].asString)
            .isEqualTo("TYPE_MISMATCH")
        val missing = events.single { it["flag"].asJsonObject["key"].asString == "missing" }
        assertThat(missing["targeting_key"].asString).isEqualTo(HASHED_KEY)
        assertThat(missing["error"].asJsonObject["message"].asString).isEqualTo("FLAG_NOT_FOUND")
        assertThat(events.map { it["context"].asJsonObject.has("evaluation") }).containsOnly(false)
        assertThat(events.toString()).doesNotContain(CONTEXT_CANARY)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).isEmpty()
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain error consent and nullable identity W trackFlagEvaluationEvent {captured hook}`(
        fakeConsent: Boolean
    ) {
        // Given
        assignments(fakeConsent)
        val client = FlagsClient.Builder(sdkCore = fixture.core).build()
        val context = EvaluationContext(TARGETING_KEY, mapOf("email" to CONTEXT_CANARY))
        client.setEvaluationContext(context)
        val proxy = _FlagsInternalProxy(client)
        val flag = checkNotNull(proxy.getFlagAssignmentsSnapshot()["enabled"])
        assignments(!fakeConsent)
        client.setEvaluationContext(context)

        // When
        listOf(null, "", TELEMETRY_KEY).forEach { telemetryTargetingKey ->
            repeat(2) {
                proxy.trackFlagEvaluationEvent("enabled", flag, context, null, fakeConsent, telemetryTargetingKey)
                proxy.trackFlagEvaluationEvent(
                    "enabled",
                    flag,
                    context,
                    "TYPE_MISMATCH",
                    fakeConsent,
                    telemetryTargetingKey
                )
                proxy.trackFlagEvaluationEvent(
                    "missing",
                    null,
                    context,
                    "FLAG_NOT_FOUND",
                    fakeConsent,
                    telemetryTargetingKey
                )
                proxy.trackFlagEvaluationEvent(
                    "unknown",
                    null,
                    context,
                    ERROR_CANARY,
                    fakeConsent,
                    telemetryTargetingKey
                )
            }
        }
        fixture.flush()
        val events = fixture.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)

        // Then
        assertThat(events).hasSize(12)
        assertThat(events.map { it["evaluation_count"].asInt }).containsOnly(2)
        assertThat(events.count { !it.has("targeting_key") }).isEqualTo(4)
        assertThat(events.filter { it.has("targeting_key") }.map { it["targeting_key"].asString })
            .containsOnly("", if (fakeConsent) TELEMETRY_KEY else HASHED_TELEMETRY_KEY)
        assertThat(events.count { it.has("targeting_key") && it["targeting_key"].asString.isEmpty() }).isEqualTo(4)
        assertThat(events.filter { it.has("error") }.map { it["error"].asJsonObject["message"].asString })
            .containsOnly("TYPE_MISMATCH", "FLAG_NOT_FOUND", "GENERAL")
        mapOf("enabled" to "TYPE_MISMATCH", "missing" to "FLAG_NOT_FOUND", "unknown" to "GENERAL")
            .forEach { (key, code) ->
                val errors = events.filter { it.has("error") && it["flag"].asJsonObject["key"].asString == key }
                assertThat(errors).hasSize(3)
                assertThat(errors.map { it["error"].asJsonObject["message"].asString }).containsOnly(code)
            }
        assertThat(events.count { !it.has("error") }).isEqualTo(3)
        assertThat(events.map { it["context"].asJsonObject.has("evaluation") }).containsOnly(false)
        assertThat(events.toString()).doesNotContain(TARGETING_KEY, CONTEXT_CANARY, ERROR_CANARY)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).isEmpty()
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M omit malformed identity without losing error counts W trackFlagEvaluationEvent`(fakeConsent: Boolean) {
        // Given
        assignments(fakeConsent)
        val client = FlagsClient.Builder(sdkCore = fixture.core).build()
        val context = EvaluationContext(TARGETING_KEY)
        client.setEvaluationContext(context)
        val proxy = _FlagsInternalProxy(client)
        val flag = checkNotNull(proxy.getFlagAssignmentsSnapshot()["enabled"])

        // When
        listOf("\uD800", "\uDC00", "a\uD800b").forEach { telemetryTargetingKey ->
            proxy.trackFlagEvaluationEvent("enabled", flag, context, null, fakeConsent, telemetryTargetingKey)
            proxy.trackFlagEvaluationEvent(
                "missing",
                null,
                context,
                "FLAG_NOT_FOUND",
                fakeConsent,
                telemetryTargetingKey
            )
        }
        fixture.flush()
        val events = fixture.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)

        // Then
        assertThat(events).hasSize(2)
        assertThat(events.map { it.has("targeting_key") }).containsOnly(false)
        assertThat(events.map { it["evaluation_count"].asInt }).containsOnly(3)
        assertThat(events.single { it.has("error") }["error"].asJsonObject["message"].asString)
            .isEqualTo("FLAG_NOT_FOUND")
        assertThat(events.map { it["context"].asJsonObject.has("evaluation") }).containsOnly(false)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).isEmpty()
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).isEmpty()
    }

    private fun assignments(consent: Boolean) {
        val response = JSONObject(
            precomputedAssignmentsBody(
                mapOf("enabled" to FakeAssignment("boolean", true, "variant", "allocation", doLog = true))
            )
        )
        response.getJSONObject("data").getJSONObject("attributes").put("observeFullEvaluationData", consent)
        fixture.stubCore.stubNetworkResponses { httpResponse(it, body = response.toString()) }
    }

    companion object {
        private const val TARGETING_KEY = "jane.doe@datadoghq.com"
        private const val HASHED_KEY = "sha256_b4698f9b6d186781fa8dc59e533578fa2d8379a46b1cf6db85cda6aa9c99e51b"
        private const val TELEMETRY_KEY = "abc"
        private const val HASHED_TELEMETRY_KEY =
            "sha256_ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        private const val CONTEXT_CANARY = "private-context-canary@example.com"
        private const val ERROR_CANARY = "private-error-canary@example.com"
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> = listOf(mainLooper)
    }
}
