/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags._FlagsInternalProxy
import com.datadog.android.flags.integration.FlagsObfuscationTest.Companion.readFixture
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.FlagsTelemetryTestFixture
import com.datadog.android.flags.integration.tests.utils.FlagsTelemetryTestFixture.Companion.CONTROL
import com.datadog.android.flags.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.flags.integration.tests.utils.bodyAsJson
import com.datadog.android.flags.integration.tests.utils.httpResponse
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.android.flags.openfeature.asOpenFeatureProvider
import com.datadog.android.flags.setEvaluationTelemetryOptOut
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.Value
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import dev.openfeature.kotlin.sdk.EvaluationContext as OpenFeatureEvaluationContext

@ExtendWith(ForgeExtension::class, TestConfigurationExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
internal class EvaluationTelemetryTest {
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
    @ValueSource(booleans = [true, false])
    fun `M preserve telemetry policy W resolve() {encoded assignments and missing flag}`(
        optedOut: Boolean,
        @StringForgery fakeTargetingKey: String,
        @StringForgery fakeFlagKey: String,
        @StringForgery(regex = "[0-9a-f]{32}") fakeSalt: String,
        @BoolForgery fakeValue: Boolean
    ) {
        // Given
        val response = encodedResponse(fakeFlagKey, fakeValue, fakeSalt)
        fixture.stubCore.stubNetworkResponses { httpResponse(it, body = response) }
        val client = newClient()
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))
        client.setEvaluationTelemetryOptOut(optedOut)

        // When
        val assigned = client.resolve(fakeFlagKey, !fakeValue)
        val missing = client.resolve("$fakeFlagKey-missing", !fakeValue)
        fixture.flush()

        // Then
        assertThat(assigned.value).isEqualTo(fakeValue)
        assertThat(missing.errorCode).isEqualTo(ErrorCode.FLAG_NOT_FOUND)
        assertThat(fixture.evaluationCount()).isEqualTo(if (optedOut) 0 else 2)
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).hasSize(if (optedOut) 0 else 1)
        val exposure = fixture.events(Feature.FLAGS_FEATURE_NAME).single()
        assertThat(exposure["flag"].asJsonObject["key"].asString).isEqualTo(fakeFlagKey)
        assertThat(exposure["subject"].asJsonObject["id"].asString).isEqualTo(fakeTargetingKey)
    }

    @ParameterizedTest
    @MethodSource("interleavedContextUpdates")
    fun `M bind policy to submitted context W context update() {concurrent conversion}`(
        initializing: Boolean,
        optedOut: Boolean
    ) = runBlocking<Unit> {
        // Given
        val provider = newClient().asOpenFeatureProvider()
        val original = context(Value.Boolean(false))
        provider.initialize(original)
        val requested = context(Value.Boolean(optedOut))
        val competing = context(Value.Boolean(!optedOut))
        val executor = Executors.newSingleThreadExecutor()
        var interleavedUpdates = 0
        val interleaved = object : OpenFeatureEvaluationContext by requested {
            override fun getTargetingKey(): String {
                // Complete another update while this caller is converting its context.
                // The old provider publishes its policy before its final conversion.
                executor.submit { runBlocking { provider.onContextSet(original, competing) } }
                    .get(5, TimeUnit.SECONDS)
                interleavedUpdates++
                return requested.getTargetingKey()
            }
        }

        try {
            // When
            if (initializing) provider.initialize(interleaved) else provider.onContextSet(original, interleaved)
            val result = provider.getBooleanEvaluation("enabled", false, null)
            fixture.flush()

            // Then
            assertThat(interleavedUpdates).isPositive()
            assertThat(result.value).isTrue()
            val attributes = fixture.stubCore.requestsSent().last().bodyAsJson()
                .getJSONObject("data").getJSONObject("attributes")
                .getJSONObject("subject").getJSONObject("targeting_attributes")
            assertThat(attributes.getString(CONTROL)).isEqualTo(optedOut.toString())
            assertThat(fixture.evaluationCount()).isEqualTo(if (optedOut) 0 else 1)
            assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).hasSize(if (optedOut) 0 else 1)
            assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `M retain subject opt-out W getBooleanEvaluation() {different subject refresh pending}`() = runBlocking<Unit> {
        // Given
        val provider = newClient().asOpenFeatureProvider()
        val original = context(Value.Boolean(true), "A")
        val requested = context(Value.Boolean(false), "B")
        provider.initialize(original)
        fixture.pauseAssignments = true
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            provider.onContextSet(original, requested)
        }

        // When
        val previous = provider.getBooleanEvaluation("enabled", false, null)
        val rumWhilePending = fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME).size
        fixture.completeNextAssignment()
        pending.join()
        val updated = provider.getBooleanEvaluation("enabled", false, null)
        fixture.flush()

        // Then
        assertThat(previous.value).isTrue()
        assertThat(updated.value).isTrue()
        assertThat(rumWhilePending).isZero()
        assertThat(fixture.evaluationCount()).isEqualTo(1)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(2)
    }

    @Test
    fun `M retain subject opt-out W getBooleanEvaluation() {different subject refresh fails}`() = runBlocking<Unit> {
        // Given
        val provider = newClient().asOpenFeatureProvider()
        val original = context(Value.Boolean(true), "A")
        provider.initialize(original)
        fixture.failAssignments()
        val refresh = runCatching {
            provider.onContextSet(original, context(Value.Boolean(false), "B"))
        }

        // When
        val result = provider.getBooleanEvaluation("enabled", false, null)
        fixture.flush()

        // Then
        assertThat(refresh.isFailure).isTrue()
        assertThat(result.value).isTrue()
        assertThat(fixture.evaluationCount()).isZero()
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).isEmpty()
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
    }

    @Test
    fun `M keep subject opt-in W getBooleanEvaluation() {B refresh pending}`() = runBlocking<Unit> {
        // Given
        val provider = newClient().asOpenFeatureProvider()
        val optedOut = context(Value.Boolean(true), "A")
        val optedIn = context(Value.Boolean(false), "A")
        provider.initialize(optedOut)
        fixture.pauseAssignments = true
        val first = launch(start = CoroutineStart.UNDISPATCHED) { provider.onContextSet(optedOut, optedIn) }
        val second = launch(start = CoroutineStart.UNDISPATCHED) {
            provider.onContextSet(optedIn, context(Value.Boolean(false), "B"))
        }

        // When
        val result = provider.getBooleanEvaluation("enabled", false, null)
        fixture.completeNextAssignment()
        fixture.completeNextAssignment()
        first.join()
        second.join()
        fixture.flush()

        // Then
        assertThat(result.value).isTrue()
        assertThat(fixture.evaluationCount()).isEqualTo(1)
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).hasSize(1)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain targeting and exposure attributes W getBooleanEvaluation() {activation varies}`(
        activated: Boolean
    ) = runBlocking<Unit> {
        // Given
        val provider = newClient(activated).asOpenFeatureProvider()
        provider.initialize(context(Value.Boolean(true)))

        // When
        val result = provider.getBooleanEvaluation("enabled", false, null)
        fixture.flush()

        // Then
        assertThat(result.value).isTrue()
        assertThat(requestAttributes().getString(CONTROL)).isEqualTo("true")
        val exposures = fixture.events(Feature.FLAGS_FEATURE_NAME)
        assertThat(exposures).hasSize(1)
        assertThat(exposures.single().getAsJsonObject("subject").getAsJsonObject("attributes")[CONTROL].asString)
            .isEqualTo("true")
        assertThat(fixture.evaluationCount()).isEqualTo(if (activated) 0 else 1)
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).hasSize(if (activated) 0 else 1)
    }

    @ParameterizedTest
    @MethodSource("controls")
    fun `M suppress only Boolean true W getBooleanEvaluation() {activated provider}`(
        fakeControl: Value?
    ) = runBlocking<Unit> {
        // Given
        val provider = newClient().asOpenFeatureProvider()
        provider.initialize(context(fakeControl))

        // When
        val result = provider.getBooleanEvaluation("enabled", false, null)
        fixture.flush()

        // Then
        val suppressed = fakeControl == Value.Boolean(true)
        assertThat(result.value).isTrue()
        assertThat(fixture.evaluationCount()).isEqualTo(if (suppressed) 0 else 1)
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).hasSize(if (suppressed) 0 else 1)
        val exposures = fixture.events(Feature.FLAGS_FEATURE_NAME)
        assertThat(exposures).hasSize(1)
        val attributes = requestAttributes()
        assertThat(attributes.has(CONTROL)).isEqualTo(fakeControl != null)
        val exposureAttributes = exposures.single().getAsJsonObject("subject").getAsJsonObject("attributes")
        assertThat(exposureAttributes.toString()).isEqualTo(attributes.toString())
    }

    @Test
    fun `M preserve values and exposures W resolveXxxValue() {opted out}`() {
        // Given
        val client = initializedClient(optedOut = true)

        // When
        val boolean = client.resolve("enabled", false)
        val string = client.resolveStringValue("text", "fallback")
        val integer = client.resolveIntValue("integer", 0)
        val number = client.resolveDoubleValue("number", 0.0)
        val structure = client.resolveStructureValue("object", emptyMap())
        val json = client.resolveStructureValue("object", JSONObject())
        fixture.flush()

        // Then
        assertThat(boolean.value).isTrue()
        assertThat(string).isEqualTo("assigned")
        assertThat(integer).isEqualTo(7)
        assertThat(number).isEqualTo(2.5)
        assertThat(structure).containsEntry("assigned", true)
        assertThat(json.getBoolean("assigned")).isTrue()
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(5)
        assertThat(fixture.evaluationCount()).isZero()
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("errorCases")
    fun `M preserve defaults and errors W resolve() {opt-out varies}`(
        flagKey: String,
        defaultValue: Any,
        expectedError: ErrorCode,
        optedOut: Boolean
    ) {
        // Given
        val client = initializedClient(optedOut)

        // When
        val result = client.resolve(flagKey, defaultValue)
        fixture.flush()

        // Then
        assertThat(result.value).isEqualTo(defaultValue)
        assertThat(result.errorCode).isEqualTo(expectedError)
        assertThat(fixture.evaluationCount()).isEqualTo(if (optedOut) 0 else 1)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M suppress telemetry W resolve() {not initialized and opted out}`() {
        // Given
        val client = newClient()
        client.setEvaluationTelemetryOptOut(true)

        // When
        val result = client.resolve("enabled", false)
        fixture.flush()

        // Then
        assertThat(result.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(result.value).isFalse()
        assertThat(fixture.evaluationCount()).isZero()
    }

    @Test
    fun `M preserve diagnostics W resolve() {unknown reason and opted out}`() {
        // Given
        val client = initializedClient(optedOut = true)
        val diagnosticsBefore = fixture.stubCore.telemetryEventsWritten().size

        // When
        val result = client.resolve("future-reason", false)
        fixture.flush()

        // Then
        assertThat(result.value).isTrue()
        assertThat(fixture.stubCore.telemetryEventsWritten()).hasSize(diagnosticsBefore + 1)
        assertThat(fixture.evaluationCount()).isZero()
    }

    @Test
    fun `M preserve exposure policy W resolve() {doLog false and opted out}`() {
        // Given
        val client = initializedClient(optedOut = true)

        // When
        val result = client.resolve("untracked", false)
        fixture.flush()

        // Then
        assertThat(result.value).isTrue()
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).isEmpty()
        assertThat(fixture.evaluationCount()).isZero()
    }

    @Test
    fun `M preserve snapshot exposures W trackFlagSnapshotEvaluation() {opted out}`() {
        // Given
        val client = initializedClient(optedOut = true)
        val proxy = _FlagsInternalProxy(client)
        val snapshot = proxy.getFlagAssignmentsSnapshot()

        // When
        proxy.trackFlagSnapshotEvaluation("enabled", checkNotNull(snapshot["enabled"]), EvaluationContext("subject"))
        fixture.flush()

        // Then
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
        assertThat(fixture.evaluationCount()).isZero()
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M preserve manual RUM annotations W addFeatureFlagEvaluation() {opted out}`() {
        // Given
        initializedClient(optedOut = true)

        // When
        GlobalRumMonitor.get(fixture.core).addFeatureFlagEvaluation("manual", "preserved")
        fixture.flush()

        // Then
        assertThat(
            fixture.events(Feature.RUM_FEATURE_NAME).any {
                it.getAsJsonObject("feature_flags")?.get("manual")?.asString == "preserved"
            }
        ).isTrue()
    }

    @Test
    fun `M resume telemetry without changing assignments W resolve() {opt-out cleared}`() {
        // Given
        val client = initializedClient(optedOut = true)
        val first = client.resolve("enabled", false)
        val proxy = _FlagsInternalProxy(client)
        val snapshot = proxy.getFlagAssignmentsSnapshot()
        client.setEvaluationTelemetryOptOut(false)

        // When
        val result = client.resolve("enabled", false)
        val updatedSnapshot = proxy.getFlagAssignmentsSnapshot()
        fixture.flush()

        // Then
        assertThat(result).isEqualTo(first)
        assertThat(updatedSnapshot).isEqualTo(snapshot)
        assertThat(fixture.evaluationCount()).isEqualTo(1)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
        assertThat(fixture.stubCore.requestsSent()).hasSize(1)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M use current telemetry policy W getBooleanEvaluation() {refresh failed}`(
        optedOut: Boolean
    ) = runBlocking<Unit> {
        // Given
        val provider = newClient().asOpenFeatureProvider()
        val original = context(Value.Boolean(!optedOut))
        provider.initialize(original)
        fixture.failAssignments()
        val refresh = runCatching { provider.onContextSet(original, context(Value.Boolean(optedOut))) }

        // When
        val result = provider.getBooleanEvaluation("enabled", false, null)
        fixture.flush()

        // Then
        assertThat(refresh.isFailure).isTrue()
        assertThat(result.value).isTrue()
        assertThat(fixture.evaluationCount()).isEqualTo(if (optedOut) 0 else 1)
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).hasSize(if (optedOut) 0 else 1)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M use current telemetry policy W getBooleanEvaluation() {refresh pending}`(
        optedOut: Boolean
    ) = runBlocking<Unit> {
        // Given
        val provider = newClient().asOpenFeatureProvider()
        val original = context(Value.Boolean(!optedOut))
        provider.initialize(original)
        fixture.pauseAssignments = true
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            provider.onContextSet(original, context(Value.Boolean(optedOut)))
        }

        // When
        val wasPending = !pending.isCompleted
        val result = provider.getBooleanEvaluation("enabled", false, null)
        fixture.completeNextAssignment()
        pending.join()
        fixture.flush()

        // Then
        assertThat(wasPending).isTrue()
        assertThat(result.value).isTrue()
        assertThat(fixture.evaluationCount()).isEqualTo(if (optedOut) 0 else 1)
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).hasSize(if (optedOut) 0 else 1)
    }

    @Test
    fun `M preserve current policy W getBooleanEvaluation() {older refresh completes}`() = runBlocking {
        // Given
        val provider = newClient().asOpenFeatureProvider()
        val optedIn = context(Value.Boolean(false))
        val optedOut = context(Value.Boolean(true))
        provider.initialize(optedOut)
        fixture.pauseAssignments = true
        val older = launch(start = CoroutineStart.UNDISPATCHED) { provider.onContextSet(optedOut, optedIn) }
        val newer = launch(start = CoroutineStart.UNDISPATCHED) { provider.onContextSet(optedIn, optedOut) }
        fixture.completeNextAssignment()
        older.join()

        // When
        val latestWasPending = !newer.isCompleted
        val result = provider.getBooleanEvaluation("enabled", false, null)
        fixture.completeNextAssignment()
        newer.join()
        fixture.flush()

        // Then
        assertThat(latestWasPending).isTrue()
        assertThat(result.value).isTrue()
        assertThat(fixture.evaluationCount()).isZero()
        assertThat(fixture.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M preserve queued telemetry W resolve() {opt-out enabled after evaluation}`() {
        // Given
        val client = initializedClient(optedOut = false)
        client.resolveBooleanValue("enabled", false)
        client.setEvaluationTelemetryOptOut(true)

        // When
        val result = client.resolveBooleanValue("untracked", false)
        fixture.flush()

        // Then
        assertThat(result).isTrue()
        assertThat(fixture.evaluationCount()).isEqualTo(1)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
    }

    @Test
    fun `M isolate telemetry decisions W resolve() {two named clients}`() {
        // Given
        val optedOut = initializedClient(optedOut = true)
        val unaffected = newClient(activated = false, name = "other")
        unaffected.setEvaluationTelemetryOptOut(true)
        unaffected.setEvaluationContext(EvaluationContext("other-subject"))

        // When
        val suppressed = optedOut.resolve("enabled", false)
        val allowed = unaffected.resolve("enabled", false)
        fixture.flush()

        // Then
        assertThat(suppressed.value).isTrue()
        assertThat(allowed.value).isTrue()
        assertThat(fixture.evaluationCount()).isEqualTo(1)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(2)
    }

    @Test
    fun `M apply current policy W resolve() {cached assignments}`() {
        // Given
        val initial = newClient(activated = false)
        initial.setEvaluationContext(EvaluationContext("subject", mapOf(CONTROL to "true")))
        fixture.restartFlags()
        val restored = newClient()
        restored.setEvaluationTelemetryOptOut(true)

        // When
        val result = restored.resolve("enabled", false)
        fixture.flush()

        // Then
        assertThat(result.value).isTrue()
        assertThat(result.reason).isEqualTo(ResolutionReason.CACHED)
        assertThat(fixture.evaluationCount()).isZero()
        val exposures = fixture.events(Feature.FLAGS_FEATURE_NAME)
        assertThat(exposures).hasSize(1)
        assertThat(exposures.single().getAsJsonObject("subject").getAsJsonObject("attributes")[CONTROL].asString)
            .isEqualTo("true")
        assertThat(fixture.stubCore.requestsSent()).hasSize(1)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M ignore native string controls W resolve() {activation varies}`(activated: Boolean) {
        // Given
        val client = newClient(activated)
        client.setEvaluationContext(EvaluationContext("subject", mapOf(CONTROL to "true")))

        // When
        val result = client.resolveBooleanValue("enabled", false)
        fixture.flush()

        // Then
        assertThat(result).isTrue()
        assertThat(fixture.evaluationCount()).isEqualTo(1)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
        assertThat(requestAttributes().getString(CONTROL)).isEqualTo("true")
    }

    @Test
    fun `M refresh without duplicate exposures W onContextSet() {only attribute changes}`() = runBlocking<Unit> {
        // Given
        val provider = newClient().asOpenFeatureProvider()
        val original = context(Value.Boolean(false))
        provider.initialize(original)
        provider.getBooleanEvaluation("enabled", false, null)

        // When
        provider.onContextSet(original, context(Value.Boolean(true)))
        val result = provider.getBooleanEvaluation("enabled", false, null)
        fixture.flush()

        // Then
        assertThat(result.value).isTrue()
        assertThat(fixture.stubCore.requestsSent()).hasSize(2)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
        assertThat(fixture.evaluationCount()).isEqualTo(1)
    }

    @Test
    fun `M use updated assignments W onContextSet() {attribute changes targeting}`() = runBlocking<Unit> {
        // Given
        val provider = newClient().asOpenFeatureProvider()
        val original = context(Value.Boolean(false))
        fixture.assignments(enabled = false)
        provider.initialize(original)
        val previous = provider.getBooleanEvaluation("enabled", true, null)
        fixture.assignments(enabled = true)

        // When
        provider.onContextSet(original, context(Value.Boolean(true)))
        val result = provider.getBooleanEvaluation("enabled", false, null)
        fixture.flush()

        // Then
        assertThat(previous.value).isFalse()
        assertThat(result.value).isTrue()
        assertThat(result.variant).isEqualTo("on")
        val exposures = fixture.events(Feature.FLAGS_FEATURE_NAME)
        assertThat(exposures.map { it.getAsJsonObject("variant")["key"].asString }).containsExactly("off", "on")
        assertThat(exposures.map { it.getAsJsonObject("subject").getAsJsonObject("attributes")[CONTROL].asString })
            .containsExactly("false", "true")
        assertThat(
            fixture.stubCore.requestsSent().map {
                it.bodyAsJson().getJSONObject("data").getJSONObject("attributes")
                    .getJSONObject("subject").getJSONObject("targeting_attributes").getString(CONTROL)
            }
        ).containsExactly("false", "true")
        assertThat(fixture.evaluationCount()).isEqualTo(1)
    }

    private fun encodedResponse(key: String, value: Boolean, salt: String): String {
        val response = JSONObject(readFixture("plaintext"))
        val attributes = response.getJSONObject("data").getJSONObject("attributes")
        val flag = attributes.getJSONObject("flags").getJSONObject("flag").put("variationValue", value)
        val saltBytes = salt.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val input = "datadog.feature-flags.flag-key.v1\u0000".toByteArray() + saltBytes + key.toByteArray()
        val encodedKey = MessageDigest.getInstance("SHA-256").digest(input)
            .joinToString("") { "%02x".format(Locale.US, it) }
        attributes.put("flags", JSONObject().put(encodedKey, flag))
        attributes.put("obfuscated", true)
        attributes.put("obfuscation", JSONObject().put("scheme", "flag-key-sha256-v1").put("salt", salt))
        return response.toString()
    }

    private fun newClient(activated: Boolean = true, name: String = "client"): FlagsClient =
        FlagsClient.Builder(name, fixture.core).enableEvaluationTelemetryOptOutParameter(activated).build()

    private fun initializedClient(optedOut: Boolean): FlagsClient = newClient().apply {
        setEvaluationTelemetryOptOut(optedOut)
        setEvaluationContext(EvaluationContext("subject"))
    }

    private fun context(control: Value?, subject: String = "subject") = ImmutableContext(
        subject,
        control?.let { mapOf(CONTROL to it) } ?: emptyMap()
    )

    private fun requestAttributes() = fixture.stubCore.requestsSent().single().bodyAsJson()
        .getJSONObject(
            "data"
        ).getJSONObject("attributes").getJSONObject("subject").getJSONObject("targeting_attributes")

    companion object {
        @JvmStatic
        fun interleavedContextUpdates(): List<Arguments> = listOf(false, true).flatMap { initializing ->
            listOf(false, true).map { optedOut -> Arguments.of(initializing, optedOut) }
        }

        @JvmStatic
        fun controls(): List<Arguments> = listOf(
            null, Value.Null, Value.Boolean(false), Value.Boolean(true), Value.String("true"),
            Value.String("false"), Value.Integer(1), Value.Double(1.0), Value.List(listOf(Value.Boolean(true))),
            Value.Structure(mapOf("value" to Value.Boolean(true)))
        ).map { Arguments.of(it) }

        @JvmStatic
        fun errorCases(): List<Arguments> = listOf(false, true).flatMap { optedOut ->
            listOf(
                Arguments.of("missing", false, ErrorCode.FLAG_NOT_FOUND, optedOut),
                Arguments.of("enabled", "fallback", ErrorCode.TYPE_MISMATCH, optedOut),
                Arguments.of("malformed", false, ErrorCode.PARSE_ERROR, optedOut)
            )
        }

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> = listOf(MainLooperTestConfiguration())
    }
}
