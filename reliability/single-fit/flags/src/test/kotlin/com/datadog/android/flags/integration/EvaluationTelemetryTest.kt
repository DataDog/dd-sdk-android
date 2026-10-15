/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags._FlagsInternalProxy
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.android.flags.openfeature.asOpenFeatureProvider
import com.datadog.android.flags.setEvaluationTelemetryOptOut
import com.datadog.android.rum.GlobalRumMonitor
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.Value
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
internal class EvaluationTelemetryTest {
    @Test
    fun `M emit baseline telemetry W native and provider resolution`(forge: Forge) = runBlocking<Unit> {
        // Given
        FlagsRuntime(forge).use { runtime ->
            val native = FlagsClient.Builder("native", runtime.core).build()
            native.setEvaluationTelemetryOptOut(true)
            val provider = FlagsClient.Builder("provider", runtime.core).build().asOpenFeatureProvider()
            runtime.assignments()
            native.setEvaluationContext(EvaluationContext("native-subject"))
            runtime.assignments()
            provider.initialize(
                ImmutableContext("provider-subject", mapOf(FlagsRuntime.CONTROL to Value.Boolean(true)))
            )

            // When
            assertThat(native.resolveBooleanValue("enabled", false)).isTrue()
            assertThat(provider.getBooleanEvaluation("enabled", false, null).value).isTrue()
            assertThat(provider.getBooleanEvaluation("enabled", false, null).variant).isEqualTo("on")
            assertThat(provider.getBooleanEvaluation("untracked", false, null).value).isTrue()
            assertThat(provider.getBooleanEvaluation("missing", false, null).value).isFalse()
            assertThat(provider.getStringEvaluation("enabled", "fallback", null).value).isEqualTo("fallback")
            runtime.flush()

            // Then
            val evaluations = runtime.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)
            val count = evaluations.sumOf { it["evaluation_count"].asInt }
            assertThat(count).isEqualTo(6)
            assertThat(runtime.events(Feature.FLAGS_FEATURE_NAME)).hasSize(2)
            assertThat(runtime.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).isNotEmpty()
            assertThat(
                runtime.events(Feature.RUM_FEATURE_NAME).any {
                    it.getAsJsonObject("feature_flags")?.get("enabled")?.asString == "on"
                }
            ).isTrue()
            assertThat(runtime.server.requestCount).isEqualTo(2)
            assertThat(evaluations.count { it["runtime_default_used"].asBoolean }).isEqualTo(2)
            println("FLAGS_BASELINE evaluations=$count exposures=2 assignment_requests=2 defaults=2")
        }
    }

    @Test
    fun `M suppress only strict Boolean true W provider initialize`(forge: Forge) = runBlocking<Unit> {
        // Given
        val controls = listOf(
            null, Value.Null, Value.Boolean(false), Value.Boolean(true), Value.String("true"),
            Value.String("false"), Value.Integer(1), Value.Double(1.0), Value.List(listOf(Value.Boolean(true))),
            Value.Structure(mapOf("value" to Value.Boolean(true)))
        )
        for (control in controls) {
            FlagsRuntime(forge).use { runtime ->
                val provider = FlagsClient.Builder("provider", runtime.core)
                    .enableEvaluationTelemetryOptOutParameter(true).build().asOpenFeatureProvider()
                runtime.assignments()
                val attributes = control?.let { mapOf(FlagsRuntime.CONTROL to it) } ?: emptyMap()
                provider.initialize(ImmutableContext("subject", attributes))

                // When
                assertThat(provider.getBooleanEvaluation("enabled", false, null).value).isTrue()
                assertThat(provider.getBooleanEvaluation("untracked", false, null).value).isTrue()
                assertThat(provider.getBooleanEvaluation("missing", false, null).value).isFalse()
                assertThat(provider.getStringEvaluation("enabled", "fallback", null).value).isEqualTo("fallback")
                runtime.flush()

                // Then
                val suppressed = control == Value.Boolean(true)
                assertThat(
                    runtime.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).sumOf {
                        it["evaluation_count"].asInt
                    }
                ).describedAs("control=%s", control).isEqualTo(if (suppressed) 0 else 4)
                assertThat(runtime.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
                assertThat(runtime.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).hasSize(if (suppressed) 0 else 2)
                assertThat(runtime.server.takeRequest().body.readUtf8()).doesNotContain(FlagsRuntime.CONTROL)
            }
        }
    }

    @Test
    fun `M retain values and exposures W native opt out and snapshots`(forge: Forge) {
        // Given
        FlagsRuntime(forge).use { runtime ->
            val client = FlagsClient.Builder("native", runtime.core)
                .enableEvaluationTelemetryOptOutParameter(true).build()
            client.setEvaluationTelemetryOptOut(true)
            assertThat(client.resolve("enabled", false).errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
            runtime.assignments()
            val context = EvaluationContext("subject")
            client.setEvaluationContext(context)
            val proxy = _FlagsInternalProxy(client)
            val snapshot = proxy.getFlagAssignmentsSnapshot()

            // When
            val suppressed = client.resolve("enabled", false)
            assertThat(suppressed.value).isTrue()
            assertThat(client.resolveStringValue("text", "fallback")).isEqualTo("assigned")
            assertThat(client.resolveIntValue("integer", 0)).isEqualTo(7)
            assertThat(client.resolveDoubleValue("number", 0.0)).isEqualTo(2.5)
            assertThat(client.resolveStructureValue("object", emptyMap())).containsEntry("assigned", true)
            assertThat(client.resolveStructureValue("object", JSONObject()).getBoolean("assigned")).isTrue()
            assertThat(client.resolveBooleanValue("untracked", false)).isTrue()
            assertThat(client.resolve("missing", false).errorCode).isEqualTo(ErrorCode.FLAG_NOT_FOUND)
            assertThat(client.resolve("enabled", "fallback").errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
            assertThat(client.resolve("malformed", false).errorCode).isEqualTo(ErrorCode.PARSE_ERROR)
            val diagnosticsBefore = runtime.stubCore.telemetryEventsWritten().size
            assertThat(client.resolve("future-reason", false).value).isTrue()
            assertThat(runtime.stubCore.telemetryEventsWritten()).hasSize(diagnosticsBefore + 1)
            proxy.trackFlagSnapshotEvaluation("enabled", checkNotNull(snapshot["enabled"]), context)
            GlobalRumMonitor.get(runtime.core).addFeatureFlagEvaluation("manual", "preserved")
            runtime.flush()

            // Then
            assertThat(runtime.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).isEmpty()
            assertThat(runtime.events(Feature.FLAGS_FEATURE_NAME)).hasSize(5)
            assertThat(runtime.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).isEmpty()
            assertThat(
                runtime.events(Feature.RUM_FEATURE_NAME).any {
                    it.getAsJsonObject("feature_flags")?.get("manual")?.asString == "preserved"
                }
            ).isTrue()
            client.setEvaluationTelemetryOptOut(false)
            assertThat(client.resolve("enabled", false)).isEqualTo(suppressed)
            assertThat(proxy.getFlagAssignmentsSnapshot()).isEqualTo(snapshot)
            runtime.flush()
            assertThat(
                runtime.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).sumOf {
                    it["evaluation_count"].asInt
                }
            ).isEqualTo(1)
            assertThat(runtime.events(Feature.FLAGS_FEATURE_NAME)).hasSize(5)
            assertThat(runtime.server.requestCount).isEqualTo(1)
        }
    }

    @Test
    fun `M use current policy W failed and pending context updates`(forge: Forge) = runBlocking<Unit> {
        // Given
        FlagsRuntime(forge).use { runtime ->
            val provider = FlagsClient.Builder("provider", runtime.core)
                .enableEvaluationTelemetryOptOutParameter(true).build().asOpenFeatureProvider()
            val original = ImmutableContext("original")
            val failedOptOut = ImmutableContext("failed", mapOf(FlagsRuntime.CONTROL to Value.Boolean(true)))
            val failedOptIn = ImmutableContext("failed", mapOf(FlagsRuntime.CONTROL to Value.Boolean(false)))
            val pendingOptOut = ImmutableContext("pending", mapOf(FlagsRuntime.CONTROL to Value.Boolean(true)))
            val pendingOptIn = ImmutableContext("pending", mapOf(FlagsRuntime.CONTROL to Value.Boolean(false)))
            runtime.assignments()
            provider.initialize(original)

            // When
            runtime.server.enqueue(MockResponse().setResponseCode(500))
            assertThat(runCatching { provider.onContextSet(original, failedOptOut) }.isFailure).isTrue()
            assertThat(provider.getBooleanEvaluation("enabled", false, null).value).isTrue()
            runtime.flush()
            assertThat(runtime.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).isEmpty()
            provider.onContextSet(failedOptOut, failedOptIn)
            assertThat(provider.getBooleanEvaluation("enabled", false, null).value).isTrue()
            runtime.pauseAssignments = true
            runtime.assignments()
            val pending = launch(start = CoroutineStart.UNDISPATCHED) {
                provider.onContextSet(failedOptIn, pendingOptOut)
            }
            assertThat(pending.isCompleted).isFalse()
            assertThat(provider.getBooleanEvaluation("enabled", false, null).value).isTrue()
            provider.onContextSet(pendingOptOut, pendingOptIn)
            assertThat(provider.getBooleanEvaluation("enabled", false, null).value).isTrue()
            runtime.completeAssignments()
            pending.join()
            assertThat(provider.getBooleanEvaluation("enabled", false, null).value).isTrue()
            runtime.flush()

            // Then
            assertThat(
                runtime.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).sumOf {
                    it["evaluation_count"].asInt
                }
            ).isEqualTo(3)
            assertThat(runtime.events(Feature.FLAGS_FEATURE_NAME)).hasSize(2)
            assertThat(runtime.server.requestCount).isEqualTo(3)
        }
    }

    @Test
    fun `M preserve queued telemetry W policy changes and independent clients`(forge: Forge) {
        // Given
        FlagsRuntime(forge).use { runtime ->
            val optedOut = FlagsClient.Builder("enabled", runtime.core)
                .enableEvaluationTelemetryOptOutParameter(true).build()
            val unaffected = FlagsClient.Builder("disabled", runtime.core).build()
            for ((index, client) in listOf(optedOut, unaffected).withIndex()) {
                runtime.assignments()
                client.setEvaluationContext(EvaluationContext("subject-$index"))
            }

            // When
            assertThat(optedOut.resolveBooleanValue("enabled", false)).isTrue()
            optedOut.setEvaluationTelemetryOptOut(true)
            unaffected.setEvaluationTelemetryOptOut(true)
            assertThat(optedOut.resolveBooleanValue("untracked", false)).isTrue()
            assertThat(unaffected.resolveBooleanValue("enabled", false)).isTrue()
            runtime.flush()

            // Then
            assertThat(
                runtime.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).sumOf {
                    it["evaluation_count"].asInt
                }
            ).isEqualTo(2)
            assertThat(runtime.events(Feature.FLAGS_FEATURE_NAME)).hasSize(2)
            assertThat(runtime.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).hasSize(2)
            assertThat(
                runtime.events(Feature.RUM_FEATURE_NAME).any {
                    it.getAsJsonObject("feature_flags")?.get("enabled")?.asString == "on"
                }
            ).isTrue()
            assertThat(runtime.server.requestCount).isEqualTo(2)
        }
    }

    @Test
    fun `M apply current policy W cached assignment resolution`(forge: Forge) {
        // Given
        FlagsRuntime(forge).use { runtime ->
            val initial = FlagsClient.Builder("cached", runtime.core).build()
            runtime.assignments()
            initial.setEvaluationContext(EvaluationContext("subject"))
            val original = initial.resolve("enabled", false)
            runtime.restartFlags()
            val restored = FlagsClient.Builder("cached", runtime.core)
                .enableEvaluationTelemetryOptOutParameter(true).build()

            // When
            restored.setEvaluationTelemetryOptOut(true)
            val result = restored.resolve("enabled", false)
            runtime.flush()

            // Then
            assertThat(result.value).isEqualTo(original.value)
            assertThat(result.variant).isEqualTo(original.variant)
            assertThat(result.reason).isEqualTo(ResolutionReason.CACHED)
            assertThat(runtime.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).isEmpty()
            assertThat(runtime.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
            assertThat(runtime.server.requestCount).isEqualTo(1)
        }
    }

    @Test
    fun `M update policy locally W same subject control toggles`(forge: Forge) = runBlocking<Unit> {
        // Given
        FlagsRuntime(forge).use { runtime ->
            val client = FlagsClient.Builder("provider", runtime.core)
                .enableEvaluationTelemetryOptOutParameter(true).build()
            val provider = client.asOpenFeatureProvider()
            var oldContext = ImmutableContext("subject")
            runtime.assignments()
            provider.initialize(oldContext)
            val proxy = _FlagsInternalProxy(client)
            val flag = checkNotNull(proxy.getFlagAssignmentsSnapshot()["enabled"])

            // When
            for (value in listOf(
                Value.Boolean(true),
                Value.Boolean(false),
                Value.Boolean(true),
                null,
                Value.String("true")
            )) {
                val attributes = value?.let { mapOf(FlagsRuntime.CONTROL to it) } ?: emptyMap()
                val newContext = ImmutableContext("subject", attributes)
                provider.onContextSet(oldContext, newContext)
                assertThat(provider.getBooleanEvaluation("enabled", false, null).value).isTrue()
                proxy.trackFlagSnapshotEvaluation("enabled", flag, EvaluationContext("subject"))
                oldContext = newContext
            }
            runtime.flush()

            // Then
            assertThat(
                runtime.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).sumOf {
                    it["evaluation_count"].asInt
                }
            ).isEqualTo(6)
            assertThat(runtime.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
            assertThat(runtime.stubCore.eventsReceived(Feature.RUM_FEATURE_NAME)).hasSize(6)
            assertThat(runtime.server.requestCount).isEqualTo(1)
        }
    }

    @Test
    fun `M ignore string controls W native targeting context`(forge: Forge) {
        // Given
        FlagsRuntime(forge).use { runtime ->
            val activated = FlagsClient.Builder("activated", runtime.core)
                .enableEvaluationTelemetryOptOutParameter(true).build()
            val defaultClient = FlagsClient.Builder("default", runtime.core).build()
            val attributes = mapOf(FlagsRuntime.CONTROL to "true", "plan" to "premium")

            // When
            for ((index, client) in listOf(activated, defaultClient).withIndex()) {
                runtime.assignments()
                client.setEvaluationContext(EvaluationContext("subject-$index", attributes))
                assertThat(client.resolveBooleanValue("enabled", false)).isTrue()
            }
            runtime.flush()

            // Then
            assertThat(
                runtime.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).sumOf {
                    it["evaluation_count"].asInt
                }
            ).isEqualTo(2)
            assertThat(runtime.events(Feature.FLAGS_FEATURE_NAME)).hasSize(2)
            assertThat(runtime.server.takeRequest().body.readUtf8())
                .contains("premium").doesNotContain(FlagsRuntime.CONTROL)
            assertThat(runtime.server.takeRequest().body.readUtf8())
                .contains("premium", FlagsRuntime.CONTROL)
            assertThat(runtime.server.requestCount).isEqualTo(2)
        }
    }
}
