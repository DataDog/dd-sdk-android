/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags._FlagsInternalProxy
import com.datadog.android.flags._FlagsInternalProxy.SnapshotExposureDecision.ADMITTED
import com.datadog.android.flags._FlagsInternalProxy.SnapshotExposureDecision.DUPLICATE
import com.datadog.android.flags._FlagsInternalProxy.SnapshotExposureDecision.UNAVAILABLE
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.FlagsTelemetryTestFixture
import com.datadog.android.flags.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagSnapshotFeature
import com.datadog.android.flags.model.UnparsedFlagWithFeatures
import com.datadog.android.privacy.TrackingConsent
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ForgeExtension::class, TestConfigurationExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
internal class SnapshotExposureTest {
    private val flag = Snapshot()
    private val context = EvaluationContext("")

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M share record-only and delivery decisions W admitSnapshotExposure()`(trackExposures: Boolean, forge: Forge) {
        // Given
        FlagsTelemetryTestFixture(forge, trackExposures).use { fixture ->
            fixture.stubCore.setTrackingConsent(TrackingConsent.GRANTED)
            val proxy = _FlagsInternalProxy(FlagsClient.Builder(sdkCore = fixture.core).build())
            val changed = flag.copy(serialId = 9_007_199_254_740_993L)

            // When
            val first = proxy.admitSnapshotExposure(KEY, flag, context, false)
            proxy.trackFlagExposure(KEY, flag, context)
            val repeat = proxy.admitSnapshotExposure(KEY, flag, context, true)
            val next = proxy.admitSnapshotExposure(KEY, changed, context, true)
            fixture.flush()

            // Then
            assertThat(listOf(first, repeat, next)).containsExactly(ADMITTED, DUPLICATE, ADMITTED)
            val events = fixture.events(Feature.FLAGS_FEATURE_NAME)
            if (trackExposures) {
                assertThat(events).hasSize(1)
                assertThat(events.single()["serial_id"].asLong).isEqualTo(changed.serialId)
                assertThat(events.single()["subject"].asJsonObject["id"].asString).isEmpty()
            } else {
                assertThat(events).isEmpty()
            }
            assertThat(fixture.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).isEmpty()
            assertThat(fixture.stubCore.requestsSent()).isEmpty()
        }
    }

    @ParameterizedTest
    @EnumSource(value = TrackingConsent::class, names = ["PENDING", "NOT_GRANTED"])
    fun `M preserve first admission until granted W admitSnapshotExposure()`(consent: TrackingConsent, forge: Forge) {
        // Given
        FlagsTelemetryTestFixture(forge).use { fixture ->
            val proxy = _FlagsInternalProxy(FlagsClient.Builder(sdkCore = fixture.core).build())
            fixture.stubCore.setTrackingConsent(consent)

            // When
            val unavailable = proxy.admitSnapshotExposure(KEY, flag, context, true)
            fixture.stubCore.setTrackingConsent(TrackingConsent.GRANTED)
            val admitted = proxy.admitSnapshotExposure(KEY, flag, context, false)
            fixture.flush()

            // Then
            assertThat(listOf(unavailable, admitted)).containsExactly(UNAVAILABLE, ADMITTED)
            assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).isEmpty()
        }
    }

    @Test
    fun `M decline protected and stale contexts W admitSnapshotExposure()`(forge: Forge) {
        // Given
        FlagsTelemetryTestFixture(forge).use { fixture ->
            fixture.stubCore.setTrackingConsent(TrackingConsent.GRANTED)
            val client = FlagsClient.Builder(sdkCore = fixture.core)
                .experimentationOptOutMode(ExperimentationOptOutMode.IMMEDIATE).build()
            val proxy = _FlagsInternalProxy(client)
            val protected =
                EvaluationContext("subject", mapOf("dd_experimentation_opt_out_at" to "2026-01-01T00:00:00Z"))
            client.setEvaluationContext(protected)

            // When
            val marked = proxy.admitSnapshotExposure(KEY, flag, protected, true)
            val stale = proxy.admitSnapshotExposure(KEY, flag, EvaluationContext("subject"), true)
            fixture.flush()

            // Then
            assertThat(listOf(marked, stale)).containsExactly(UNAVAILABLE, UNAVAILABLE)
            assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).isEmpty()
        }
    }

    @Test
    fun `M route replayed features W admitSnapshotExposure() and trackFlagEvaluationEvent()`(forge: Forge) {
        // Given
        FlagsTelemetryTestFixture(forge).use { fixture ->
            fixture.stubCore.setTrackingConsent(TrackingConsent.GRANTED)
            val proxy = _FlagsInternalProxy(FlagsClient.Builder(sdkCore = fixture.core).build())
            val captured = EvaluationContext("subject", mapOf("dd_assignment_token" to "private"))
            val featured = flag.copy(
                features = listOf(
                    FlagSnapshotFeature("hook", false, listOf("HOOK")),
                    FlagSnapshotFeature("exposure", "2026", listOf("EXPOSURE")),
                    FlagSnapshotFeature("count", 0.0, listOf("EVALUATION"))
                )
            )

            // When
            val admitted = proxy.admitSnapshotExposure(KEY, featured, captured, true)
            proxy.trackFlagEvaluationEvent(KEY, featured, captured, null)
            fixture.flush()

            // Then
            assertThat(admitted).isEqualTo(ADMITTED)
            val event = fixture.events(Feature.FLAGS_FEATURE_NAME).single()
            assertThat(event.has("features")).isTrue()
            val features = event["features"].asJsonObject
            assertThat(features.keySet()).containsExactly("exposure")
            assertThat(features["exposure"].asJsonPrimitive.isString).isTrue()
            assertThat(features["exposure"].asString).isEqualTo("2026")
            assertThat(event.toString()).doesNotContain("dd_assignment_token")
            val row = fixture.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).single()
            assertThat(row.has("features")).isTrue()
            assertThat(row["features"].asJsonObject.keySet()).containsExactly("count")
            assertThat(row["features"].asJsonObject["count"].asJsonPrimitive.isNumber).isTrue()
            assertThat(row["features"].asJsonObject["count"].asDouble).isZero()
        }
    }

    private data class Snapshot(
        override val serialId: Long = 0L,
        override val features: List<FlagSnapshotFeature> = emptyList()
    ) : UnparsedFlagWithFeatures {
        override val allocationKey = "allocation"
        override val variationKey = "variant"
        override val doLog = true
        override val reason = "TARGETING_MATCH"
        override val extraLogging = JSONObject()
        override val variationType = "boolean"
        override val variationValue = "true"
    }

    companion object {
        private const val KEY = "offline"

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> = listOf(MainLooperTestConfiguration())
    }
}
