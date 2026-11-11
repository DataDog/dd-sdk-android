/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.FakeAssignment
import com.datadog.android.flags.integration.tests.utils.FlagsTelemetryTestFixture
import com.datadog.android.flags.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.flags.model.EvaluationContext
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

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

    @Test
    fun `M report the assignment subject W getBooleanValue() {new subject refresh failed}`(forge: Forge) {
        // Given
        start(forge)
        fixture.failAssignments()
        runBlocking { OpenFeatureAPI.setEvaluationContextAndWait(ImmutableContext("other")) }

        // When
        OpenFeatureAPI.getClient().getBooleanValue(FLAG, false)
        fixture.flush()

        // Then
        assertThat(evaluations.single().targetingKey).isEqualTo(SUBJECT)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME).single().getAsJsonObject("subject")["id"].asString)
            .isEqualTo(SUBJECT)
    }

    @ParameterizedTest
    @MethodSource("nestedEvaluations")
    fun `M keep the outer decision W getBooleanDetails() {hook evaluates other flags}`(
        hookStage: String,
        innerEvaluations: Int,
        trackExposures: Boolean,
        forge: Forge
    ) {
        // Given
        start(forge, trackExposures)
        val nestingHook = object : Hook<Boolean> {
            override fun after(
                ctx: HookContext<Boolean>,
                details: FlagEvaluationDetails<Boolean>,
                hints: Map<String, Any>
            ) {
                if (hookStage == AFTER) evaluateInnerFlag(innerEvaluations)
            }

            override fun finallyAfter(
                ctx: HookContext<Boolean>,
                details: FlagEvaluationDetails<Boolean>,
                hints: Map<String, Any>
            ) {
                if (hookStage == FINALLY_AFTER) evaluateInnerFlag(innerEvaluations)
            }
        }

        // When
        val details = OpenFeatureAPI.getClient().getBooleanDetails(FLAG, false, hookOptions(nestingHook))
        fixture.flush()

        // Then
        assertThat(details.errorCode).isNull()
        val outer = evaluations.single { it.flagKey == FLAG }
        assertThat(outer.isExposure()).isTrue()
        assertThat(outer.shouldSend()).isTrue()
        assertThat(outer.features).containsEntry("holdout.key", "holdout-1")
        val outerEvents = fixture.events(Feature.FLAGS_FEATURE_NAME)
            .filter { it.getAsJsonObject("flag")["key"].asString == FLAG }
        assertThat(outerEvents).hasSize(if (trackExposures) 1 else 0)
    }

    @Test
    fun `M send one exposure W getBooleanDetails() {after hook evaluates the same flag}`(forge: Forge) {
        // Given
        start(forge)
        val nestingHook = object : Hook<Boolean> {
            override fun after(
                ctx: HookContext<Boolean>,
                details: FlagEvaluationDetails<Boolean>,
                hints: Map<String, Any>
            ) {
                OpenFeatureAPI.getClient().getBooleanValue(FLAG, false)
            }
        }

        // When
        OpenFeatureAPI.getClient().getBooleanDetails(FLAG, false, hookOptions(nestingHook))
        fixture.flush()

        // Then
        assertThat(evaluations.map { it.shouldSend() }).containsExactly(true, false)
        assertThat(evaluations.map { it.isCacheHit() }).containsExactly(false, true)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
    }

    @Test
    fun `M log callback failures to the client's core W getBooleanValue() {callback throws}`(forge: Forge) {
        // Given
        val mockLogger = mock<InternalLogger>()
        start(
            forge,
            callback = { throw IllegalStateException("callback failed") },
            clientCore = { core -> object : InternalSdkCore by core { override val internalLogger = mockLogger } }
        )

        // When
        OpenFeatureAPI.getClient().getBooleanValue(FLAG, false)

        // Then
        val messageCaptor = argumentCaptor<() -> String>()
        verify(mockLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            messageCaptor.capture(),
            isA<IllegalStateException>(),
            eq(false),
            isNull()
        )
        assertThat(messageCaptor.firstValue())
            .isEqualTo("Exposure callback failed; later failures are logged at debug level.")
    }

    @Test
    fun `M report a non-exposure W getBooleanDetails() {after hook replaces the context}`(forge: Forge) {
        // Given
        lateinit var client: FlagsClient
        val contextHook = object : Hook<Boolean> {
            override fun after(
                ctx: HookContext<Boolean>,
                details: FlagEvaluationDetails<Boolean>,
                hints: Map<String, Any>
            ) {
                fixture.pauseAssignments = true
                client.setEvaluationContext(EvaluationContext("other"))
            }
        }
        client = start(forge, optOutMode = ExperimentationOptOutMode.IMMEDIATE)

        // When
        val details = OpenFeatureAPI.getClient().getBooleanDetails(FLAG, false, hookOptions(contextHook))
        fixture.flush()

        // Then
        assertThat(details.errorCode).isNull()
        assertThat(evaluations.single().isExposure()).isFalse()
        assertThat(evaluations.single().shouldSend()).isFalse()
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M give each callback the same features W getBooleanValue() {earlier callback edits the map}`(
        forge: Forge
    ) {
        // Given
        val editingHook = ExposureHook { evaluation ->
            @Suppress("UNCHECKED_CAST")
            (evaluation.features as MutableMap<String, Any>).clear()
        }
        start(forge, hooksBefore = listOf(editingHook))

        // When
        OpenFeatureAPI.getClient().getBooleanValue(FLAG, false)

        // Then
        assertThat(evaluations.single().features).containsEntry("holdout.key", "holdout-1")
    }

    @Test
    fun `M record the exposure once W getObjectDetails() twice {split features}`(forge: Forge) {
        // Given
        start(forge)

        // When
        val details = OpenFeatureAPI.getClient().getObjectDetails(OBJECT_FLAG, Value.Structure(emptyMap()))
        OpenFeatureAPI.getClient().getObjectDetails(OBJECT_FLAG, Value.Structure(emptyMap()))
        fixture.flush()

        // Then
        assertThat(details.errorCode).isNull()
        assertThat(details.value).isEqualTo(Value.Structure(mapOf("color" to Value.String("blue"))))
        assertThat(evaluations.map { it.shouldSend() }).containsExactly(true, false)
        assertThat(evaluations.first().features).containsExactlyEntriesOf(mapOf("holdout.key" to "holdout-2"))
        val exposure = fixture.events(Feature.FLAGS_FEATURE_NAME).single()
        assertThat(exposure.getAsJsonObject("flag")["key"].asString).isEqualTo(OBJECT_FLAG)
        assertThat(exposure.getAsJsonObject("features")["exposure.key"].asString).isEqualTo("object-exposed")
    }

    private fun evaluateInnerFlag(times: Int) {
        repeat(times) { OpenFeatureAPI.getClient().getBooleanValue(INNER_FLAG, false) }
    }

    private fun start(
        forge: Forge,
        trackExposures: Boolean = true,
        callback: (ExposureEvaluation) -> Unit = { evaluations.add(it) },
        optOutMode: ExperimentationOptOutMode = ExperimentationOptOutMode.DISABLED,
        hooksBefore: List<Hook<*>> = emptyList(),
        clientCore: (InternalSdkCore) -> InternalSdkCore = { it }
    ): FlagsClient {
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
                ),
                OBJECT_FLAG to FakeAssignment(
                    variationType = "object",
                    variationValue = JSONObject().put("color", "blue"),
                    variationKey = "blue",
                    allocationKey = "object-allocation",
                    features = JSONArray()
                        .put(feature("holdout.key", "holdout-2", "HOOK"))
                        .put(feature("exposure.key", "object-exposed", "EXPOSURE"))
                ),
                INNER_FLAG to FakeAssignment(
                    variationType = "boolean",
                    variationValue = true,
                    variationKey = "on",
                    allocationKey = "inner-allocation"
                )
            )
        )
        val client = FlagsClient.Builder("exposure-hook", clientCore(fixture.core))
            .experimentationOptOutMode(optOutMode)
            .build()
        OpenFeatureAPI.addHooks(hooksBefore + ExposureHook { callback(it) })
        runBlocking {
            OpenFeatureAPI.setProviderAndWait(
                client.asOpenFeatureProvider(),
                ImmutableContext(SUBJECT, mapOf("plan" to Value.String("pro"))),
                Dispatchers.Unconfined
            )
        }
        return client
    }

    private fun feature(key: String, value: Any, destination: String): JSONObject =
        JSONObject().put("key", key).put("value", value).put("destinations", JSONArray().put(destination))

    private fun hookOptions(hook: Hook<Boolean>) = FlagEvaluationOptions(hooks = listOf(hook))

    companion object {
        private const val FLAG = "holdout-flag"
        private const val SUBJECT = "subject"
        private const val ALLOCATION = "holdout-allocation"
        private const val INNER_FLAG = "inner-flag"
        private const val OBJECT_FLAG = "object-flag"
        private const val AFTER = "after"
        private const val FINALLY_AFTER = "finallyAfter"

        @JvmStatic
        fun nestedEvaluations(): List<Arguments> = listOf(AFTER, FINALLY_AFTER).flatMap { stage ->
            listOf(16, 17).flatMap { count -> listOf(true, false).map { Arguments.of(stage, count, it) } }
        }

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> = listOf(MainLooperTestConfiguration())
    }
}
