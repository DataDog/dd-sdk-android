/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.FakeAssignment
import com.datadog.android.flags.integration.tests.utils.FlagsTelemetryTestFixture
import com.datadog.android.flags.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.flags.openfeature.ExposureEvaluation
import com.datadog.android.flags.openfeature.ExposureHook
import com.datadog.android.flags.openfeature.asOpenFeatureProvider
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import dev.openfeature.kotlin.sdk.FlagEvaluationDetails
import dev.openfeature.kotlin.sdk.FlagEvaluationOptions
import dev.openfeature.kotlin.sdk.Hook
import dev.openfeature.kotlin.sdk.HookContext
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class, TestConfigurationExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
internal class ExposureHookTest {
    private lateinit var fixture: FlagsTelemetryTestFixture
    private val evaluations = mutableListOf<ExposureEvaluation>()

    @AfterEach
    fun `tear down`() {
        runBlocking { OpenFeatureAPI.shutdown() }
        fixture.close()
    }

    @Test
    fun `M route each feature to its destination W getBooleanDetails() {split features}`(forge: Forge) {
        // Given
        start(forge)

        // When
        val details = OpenFeatureAPI.getClient().getBooleanDetails(FLAG, false)
        fixture.flush()

        // Then
        assertThat(details.metadata.getString("__dd_feature.holdout.key")).isEqualTo("holdout-1")
        assertThat(details.metadata.getBoolean("__dd_feature.holdout.should_include_in_holdout_analysis"))
            .isFalse()
        assertThat(details.metadata.getString("__dd_exposure_feature.exposure.key")).isEqualTo("exposed")
        assertThat(details.metadata.getDouble("__dd_evaluation_feature.evaluation.key")).isEqualTo(3.0)
        assertThat(details.metadata.getString("__dd_feature.exposure.key")).isNull()
        assertThat(details.metadata.getString("__dd_feature.unknown.key")).isNull()
        assertThat(evaluations.single().features).containsExactlyEntriesOf(
            mapOf("holdout.key" to "holdout-1", "holdout.should_include_in_holdout_analysis" to false)
        )
        val exposure = fixture.events(Feature.FLAGS_FEATURE_NAME).single()
        assertThat(exposure.getAsJsonObject("features").entrySet().associate { it.key to it.value.asString })
            .containsExactlyEntriesOf(mapOf("exposure.key" to "exposed"))
        val evaluation = fixture.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).single()
        assertThat(evaluation.getAsJsonObject("features").entrySet().associate { it.key to it.value.asDouble })
            .containsExactlyEntriesOf(mapOf("evaluation.key" to 3.0))
    }

    @Test
    fun `M share one verdict W getBooleanValue() twice`(forge: Forge) {
        // Given
        start(forge)

        // When
        OpenFeatureAPI.getClient().getBooleanValue(FLAG, false)
        OpenFeatureAPI.getClient().getBooleanValue(FLAG, false)
        fixture.flush()

        // Then
        assertThat(evaluations.map { it.isExposure() }).containsExactly(true, true)
        assertThat(evaluations.map { it.shouldSend() }).containsExactly(true, false)
        assertThat(evaluations.map { it.isCacheHit() }).containsExactly(false, true)
        assertThat(evaluations.map { it.targetingKey }).containsOnly(SUBJECT)
        assertThat(evaluations.map { it.allocationKey }).containsOnly(ALLOCATION)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
    }

    @Test
    fun `M record without sending W getBooleanValue() twice {trackExposures false}`(forge: Forge) {
        // Given
        start(forge, trackExposures = false)

        // When
        OpenFeatureAPI.getClient().getBooleanValue(FLAG, false)
        OpenFeatureAPI.getClient().getBooleanValue(FLAG, false)
        fixture.flush()

        // Then
        assertThat(evaluations.map { it.shouldSend() }).containsExactly(true, false)
        assertThat(evaluations.map { it.isCacheHit() }).containsExactly(false, true)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M record nothing W getBooleanDetails() {after hook fails}`(forge: Forge) {
        // Given
        start(forge)
        val failingHook = object : Hook<Boolean> {
            override fun after(
                ctx: HookContext<Boolean>,
                details: FlagEvaluationDetails<Boolean>,
                hints: Map<String, Any>
            ) {
                throw IllegalStateException("after hook failed")
            }
        }

        // When
        val failed = OpenFeatureAPI.getClient().getBooleanDetails(FLAG, false, hookOptions(failingHook))
        OpenFeatureAPI.getClient().getBooleanValue(FLAG, false)
        fixture.flush()

        // Then
        assertThat(failed.errorCode).isEqualTo(ErrorCode.GENERAL)
        assertThat(evaluations.map { it.isExposure() }).containsExactly(false, true)
        assertThat(evaluations.map { it.shouldSend() }).containsExactly(false, true)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
    }

    @Test
    fun `M keep the evaluation W getBooleanDetails() {callback throws}`(forge: Forge) {
        // Given
        start(forge, callback = { throw IllegalStateException("callback failed") })

        // When
        val details = OpenFeatureAPI.getClient().getBooleanDetails(FLAG, false)
        fixture.flush()

        // Then
        assertThat(details.value).isTrue()
        assertThat(details.errorCode).isNull()
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
    }

    @Test
    fun `M give a copy of the context W getBooleanValue()`(forge: Forge) {
        // Given
        start(forge)

        // When
        OpenFeatureAPI.getClient().getBooleanValue(FLAG, false)

        // Then
        val context = checkNotNull(evaluations.single().evaluationContext)
        assertThat(context).isNotSameAs(OpenFeatureAPI.getEvaluationContext())
        assertThat(context.getTargetingKey()).isEqualTo(SUBJECT)
        assertThat(context.getValue("plan")).isEqualTo(Value.String("pro"))
    }

    private fun start(
        forge: Forge,
        trackExposures: Boolean = true,
        callback: (ExposureEvaluation) -> Unit = { evaluations.add(it) }
    ) {
        fixture = FlagsTelemetryTestFixture(forge, trackExposures)
        fixture.assignments(
            mapOf(
                FLAG to FakeAssignment(
                    variationType = "boolean",
                    variationValue = true,
                    variationKey = "on",
                    allocationKey = ALLOCATION,
                    serialId = 42L,
                    features = JSONArray()
                        .put(feature("holdout.key", "holdout-1", "HOOK"))
                        .put(feature("holdout.key", "ignored-duplicate", "HOOK"))
                        .put(feature("holdout.should_include_in_holdout_analysis", false, "HOOK"))
                        .put(feature("exposure.key", "exposed", "EXPOSURE"))
                        .put(feature("evaluation.key", 3, "EVALUATION"))
                        .put(feature("unknown.key", "nowhere", "FUTURE"))
                        .put(JSONObject().put("key", "invalid.value").put("value", JSONObject()))
                )
            )
        )
        val provider = FlagsClient.Builder("exposure-hook", fixture.core).build().asOpenFeatureProvider()
        OpenFeatureAPI.addHooks(listOf(ExposureHook { callback(it) }))
        runBlocking {
            OpenFeatureAPI.setProviderAndWait(
                provider,
                ImmutableContext(SUBJECT, mapOf("plan" to Value.String("pro"))),
                Dispatchers.Unconfined
            )
        }
    }

    private fun feature(key: String, value: Any, destination: String): JSONObject =
        JSONObject().put("key", key).put("value", value).put("destinations", JSONArray().put(destination))

    private fun hookOptions(hook: Hook<Boolean>) = FlagEvaluationOptions(hooks = listOf(hook))

    companion object {
        private const val FLAG = "holdout-flag"
        private const val SUBJECT = "subject"
        private const val ALLOCATION = "holdout-allocation"

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> = listOf(MainLooperTestConfiguration())
    }
}
