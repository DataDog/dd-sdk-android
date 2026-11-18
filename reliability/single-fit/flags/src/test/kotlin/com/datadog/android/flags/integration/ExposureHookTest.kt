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
import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.FlagEvaluationDetails
import dev.openfeature.kotlin.sdk.FlagEvaluationOptions
import dev.openfeature.kotlin.sdk.Hook
import dev.openfeature.kotlin.sdk.HookContext
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.ProviderMetadata
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
    @StringForgery
    lateinit var fakeHoldoutValue: String

    @StringForgery
    lateinit var fakeDuplicateValue: String

    @StringForgery
    lateinit var fakeExposureValue: String

    @IntForgery
    var fakeEvaluationValue: Int = 0

    @StringForgery
    lateinit var fakeUnroutedValue: String

    @StringForgery
    lateinit var fakeObjectHoldoutValue: String

    @StringForgery
    lateinit var fakeObjectExposureValue: String

    @StringForgery
    lateinit var fakeObjectAllocationKey: String

    @StringForgery
    lateinit var fakeInnerAllocationKey: String

    @StringForgery
    lateinit var fakeColor: String

    @StringForgery
    lateinit var fakeVariant: String

    @StringForgery
    lateinit var fakeClientName: String

    @StringForgery
    lateinit var fakeWrapperName: String

    @StringForgery
    lateinit var fakeOtherSubject: String

    @StringForgery
    lateinit var fakeAttributeKey: String

    @StringForgery
    lateinit var fakeAttributeValue: String

    @StringForgery
    lateinit var fakeFailureMessage: String

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
        assertThat(details.metadata.getString("__dd_feature.holdout.key")).isEqualTo(fakeHoldoutValue)
        assertThat(details.metadata.getBoolean("__dd_feature.holdout.should_include_in_holdout_analysis"))
            .isFalse()
        assertThat(details.metadata.getString("__dd_exposure_feature.exposure.key")).isNull()
        assertThat(details.metadata.getDouble("__dd_evaluation_feature.evaluation.key")).isNull()
        assertThat(details.metadata.getString("__dd_feature.exposure.key")).isNull()
        assertThat(details.metadata.getString("__dd_feature.unknown.key")).isNull()
        assertThat(evaluations.single().features).isEqualTo(hookFeatures())
        val exposure = fixture.events(Feature.FLAGS_FEATURE_NAME).single()
        assertThat(exposure.getAsJsonObject("features").entrySet().associate { it.key to it.value.asString })
            .isEqualTo(mapOf("exposure.key" to fakeExposureValue))
        val evaluation = fixture.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).single()
        assertThat(evaluation.getAsJsonObject("features").entrySet().associate { it.key to it.value.asDouble })
            .isEqualTo(mapOf("evaluation.key" to fakeEvaluationValue.toDouble()))
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
        assertThat(evaluations.map { it.isExposure }).containsExactly(true, true)
        assertThat(evaluations.map { it.shouldSend() }).containsExactly(true, false)
        assertThat(evaluations.map { it.isCacheHit }).containsExactly(false, true)
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
        assertThat(evaluations.map { it.isCacheHit }).containsExactly(false, true)
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
                throw IllegalStateException(fakeFailureMessage)
            }
        }

        // When
        val failed = OpenFeatureAPI.getClient().getBooleanDetails(FLAG, false, hookOptions(failingHook))
        OpenFeatureAPI.getClient().getBooleanValue(FLAG, false)
        fixture.flush()

        // Then
        assertThat(failed.errorCode).isEqualTo(ErrorCode.GENERAL)
        assertThat(evaluations.map { it.isExposure }).containsExactly(false, true)
        assertThat(evaluations.map { it.shouldSend() }).containsExactly(false, true)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
    }

    @Test
    fun `M keep the evaluation W getBooleanDetails() {callback throws}`(forge: Forge) {
        // Given
        start(forge, callback = { throw IllegalStateException(fakeFailureMessage) })

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
        assertThat(context.getValue(fakeAttributeKey)).isEqualTo(Value.String(fakeAttributeValue))
    }

    @Test
    fun `M report the assignment subject W getBooleanValue() {new subject refresh failed}`(forge: Forge) {
        // Given
        start(forge)
        fixture.failAssignments()
        runBlocking { OpenFeatureAPI.setEvaluationContextAndWait(ImmutableContext(fakeOtherSubject)) }

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
        assertThat(outer.isExposure).isTrue()
        assertThat(outer.shouldSend()).isTrue()
        assertThat(outer.features).isEqualTo(hookFeatures())
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
        assertThat(evaluations.map { it.isCacheHit }).containsExactly(false, true)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
    }

    @Test
    fun `M log callback failures to the client's core W getBooleanValue() {callback throws}`(forge: Forge) {
        // Given
        val mockLogger = mock<InternalLogger>()
        start(
            forge,
            callback = { throw IllegalStateException(fakeFailureMessage) },
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
    fun `M log callback failures to the client's core W getBooleanValue() {no-op client on a named core}`(
        forge: Forge
    ) {
        // Given
        start(forge)
        val mockLogger = mock<InternalLogger>()
        val namedCore = FlagsClientTest.createStubSdkCore(forge)
        val loggingCore = object : InternalSdkCore by namedCore {
            override val internalLogger = mockLogger
        }
        val noOpClient = FlagsClient.Builder(fakeClientName, loggingCore).build()
        OpenFeatureAPI.clearHooks()
        OpenFeatureAPI.addHooks(listOf(ExposureHook { throw IllegalStateException(fakeFailureMessage) }))
        runBlocking {
            OpenFeatureAPI.setProviderAndWait(
                noOpClient.asOpenFeatureProvider(),
                ImmutableContext(SUBJECT),
                Dispatchers.Unconfined
            )
        }

        // When
        val value = OpenFeatureAPI.getClient().getBooleanValue(FLAG, false)

        // Then
        assertThat(value).isFalse()
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
        val contextHook = afterEvaluation {
            fixture.pauseAssignments = true
            client.setEvaluationContext(EvaluationContext(fakeOtherSubject))
        }
        client = start(forge, optOutMode = ExperimentationOptOutMode.IMMEDIATE)

        // When
        val details = OpenFeatureAPI.getClient().getBooleanDetails(FLAG, false, hookOptions(contextHook))
        fixture.flush()

        // Then
        assertThat(details.errorCode).isNull()
        assertThat(evaluations.single().isExposure).isFalse()
        assertThat(evaluations.single().shouldSend()).isFalse()
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M keep evaluation and skip exposure W getBooleanDetails() {authorized subject replaced in after hook}`(
        forge: Forge
    ) {
        // Given
        lateinit var client: FlagsClient
        val contextHook = afterEvaluation {
            fixture.pauseAssignments = true
            client.setEvaluationContext(
                EvaluationContext(
                    fakeOtherSubject,
                    mapOf(ASSIGNMENT_TOKEN_ATTRIBUTE to REPLACEMENT_ASSIGNMENT_TOKEN)
                )
            )
        }
        client = start(
            forge,
            initialContext = ImmutableContext(
                SUBJECT,
                mapOf(
                    fakeAttributeKey to Value.String(fakeAttributeValue),
                    ASSIGNMENT_TOKEN_ATTRIBUTE to Value.String(ASSIGNMENT_TOKEN)
                )
            )
        )

        // When
        val details = OpenFeatureAPI.getClient().getBooleanDetails(FLAG, false, hookOptions(contextHook))
        fixture.flush()

        // Then
        assertThat(details.value).isTrue()
        assertThat(details.errorCode).isNull()
        val evaluation = evaluations.single()
        assertThat(evaluation.isExposure).isFalse()
        assertThat(evaluation.shouldSend()).isFalse()
        assertThat(evaluation.targetingKey).isEqualTo(SUBJECT)
        assertThat(evaluation.features).isEqualTo(hookFeatures())
        val callbackContext = checkNotNull(evaluation.evaluationContext)
        assertThat(callbackContext.asMap()).doesNotContainKey(ASSIGNMENT_TOKEN_ATTRIBUTE)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).isEmpty()
        assertThat(fixture.events(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).single().toString())
            .doesNotContain(ASSIGNMENT_TOKEN_ATTRIBUTE, ASSIGNMENT_TOKEN, REPLACEMENT_ASSIGNMENT_TOKEN)
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
        assertThat(details.value).isEqualTo(Value.Structure(mapOf("color" to Value.String(fakeColor))))
        assertThat(evaluations.map { it.shouldSend() }).containsExactly(true, false)
        assertThat(evaluations.first().features).isEqualTo(mapOf("holdout.key" to fakeObjectHoldoutValue))
        val exposure = fixture.events(Feature.FLAGS_FEATURE_NAME).single()
        assertThat(exposure.getAsJsonObject("flag")["key"].asString).isEqualTo(OBJECT_FLAG)
        assertThat(exposure.getAsJsonObject("features").entrySet().associate { it.key to it.value.asString })
            .isEqualTo(mapOf("exposure.key" to fakeObjectExposureValue))
    }

    @Test
    fun `M keep the exposure and skip later hooks W getBooleanValue() {client finallyAfter hook throws}`(
        forge: Forge
    ) {
        // Given
        start(forge)
        val failingHook = object : Hook<Boolean> {
            override fun finallyAfter(
                ctx: HookContext<Boolean>,
                details: FlagEvaluationDetails<Boolean>,
                hints: Map<String, Any>
            ) {
                throw IllegalStateException(fakeFailureMessage)
            }
        }
        val client = OpenFeatureAPI.getClient()
        client.addHooks(listOf(failingHook))

        // When
        val failure = runCatching { client.getBooleanValue(FLAG, false) }.exceptionOrNull()
        fixture.flush()

        // Then
        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
        assertThat(evaluations).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("wrappers")
    fun `M follow what the wrapper returns W getBooleanValue() {provider wrapped}`(
        wrapper: String,
        registerExposureHook: Boolean,
        expectedEvents: Int,
        expectedCallbacks: Int,
        forge: Forge
    ) {
        // Given
        start(forge, wrapProvider = { wrap(it, wrapper) }, registerExposureHook = registerExposureHook)

        // When
        OpenFeatureAPI.getClient().getBooleanValue(FLAG, false)
        fixture.flush()

        // Then
        assertThat(fixture.events(Feature.FLAGS_FEATURE_NAME)).hasSize(expectedEvents)
        assertThat(evaluations).hasSize(expectedCallbacks)
    }

    private fun hookFeatures() =
        mapOf("holdout.key" to fakeHoldoutValue, "holdout.should_include_in_holdout_analysis" to false)

    private fun wrap(inner: FeatureProvider, wrapper: String): FeatureProvider {
        val ownMetadata = object : ProviderMetadata {
            override val name: String = fakeWrapperName
        }
        return when (wrapper) {
            DELEGATES_ALL -> object : FeatureProvider by inner {}
            OWN_METADATA -> object : FeatureProvider by inner {
                override val metadata: ProviderMetadata = ownMetadata
            }
            DROPS_HOOKS -> object : FeatureProvider by inner {
                override val hooks: List<Hook<*>> = emptyList()
            }
            else -> object : FeatureProvider by inner {
                override val hooks: List<Hook<*>> = emptyList()
                override val metadata: ProviderMetadata = ownMetadata
            }
        }
    }

    private fun evaluateInnerFlag(times: Int) {
        repeat(times) { OpenFeatureAPI.getClient().getBooleanValue(INNER_FLAG, false) }
    }

    private fun afterEvaluation(action: () -> Unit) = object : Hook<Boolean> {
        override fun after(
            ctx: HookContext<Boolean>,
            details: FlagEvaluationDetails<Boolean>,
            hints: Map<String, Any>
        ) = action()
    }

    private fun start(
        forge: Forge,
        trackExposures: Boolean = true,
        callback: (ExposureEvaluation) -> Unit = { evaluations.add(it) },
        optOutMode: ExperimentationOptOutMode = ExperimentationOptOutMode.DISABLED,
        hooksBefore: List<Hook<*>> = emptyList(),
        clientCore: (InternalSdkCore) -> InternalSdkCore = { it },
        wrapProvider: (FeatureProvider) -> FeatureProvider = { it },
        registerExposureHook: Boolean = true,
        initialContext: ImmutableContext = ImmutableContext(
            SUBJECT,
            mapOf(fakeAttributeKey to Value.String(fakeAttributeValue))
        )
    ): FlagsClient {
        fixture = FlagsTelemetryTestFixture(forge, trackExposures)
        fixture.assignments(
            mapOf(
                FLAG to FakeAssignment(
                    variationType = "boolean",
                    variationValue = true,
                    variationKey = fakeVariant,
                    allocationKey = ALLOCATION,
                    serialId = 42L,
                    features = JSONArray()
                        .put(feature("holdout.key", fakeHoldoutValue, "HOOK"))
                        .put(feature("holdout.key", fakeDuplicateValue, "HOOK"))
                        .put(feature("holdout.should_include_in_holdout_analysis", false, "HOOK"))
                        .put(feature("exposure.key", fakeExposureValue, "EXPOSURE"))
                        .put(feature("evaluation.key", fakeEvaluationValue, "EVALUATION"))
                        .put(feature("unknown.key", fakeUnroutedValue, "FUTURE"))
                        .put(JSONObject().put("key", "invalid.value").put("value", JSONObject()))
                ),
                OBJECT_FLAG to FakeAssignment(
                    variationType = "object",
                    variationValue = JSONObject().put("color", fakeColor),
                    variationKey = fakeColor,
                    allocationKey = fakeObjectAllocationKey,
                    features = JSONArray()
                        .put(feature("holdout.key", fakeObjectHoldoutValue, "HOOK"))
                        .put(feature("exposure.key", fakeObjectExposureValue, "EXPOSURE"))
                ),
                INNER_FLAG to FakeAssignment(
                    variationType = "boolean",
                    variationValue = true,
                    variationKey = fakeVariant,
                    allocationKey = fakeInnerAllocationKey
                )
            )
        )
        val client = FlagsClient.Builder(fakeClientName, clientCore(fixture.core))
            .experimentationOptOutMode(optOutMode)
            .build()
        OpenFeatureAPI.addHooks(if (registerExposureHook) hooksBefore + ExposureHook { callback(it) } else hooksBefore)
        runBlocking {
            OpenFeatureAPI.setProviderAndWait(
                wrapProvider(client.asOpenFeatureProvider()),
                initialContext,
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
        private const val ASSIGNMENT_TOKEN_ATTRIBUTE = "dd_assignment_token"
        private const val ASSIGNMENT_TOKEN = "header.payload.signature"
        private const val REPLACEMENT_ASSIGNMENT_TOKEN = "replacement.header.payload.signature"
        private const val DELEGATES_ALL = "delegates hooks and metadata"
        private const val OWN_METADATA = "own metadata"
        private const val DROPS_HOOKS = "drops hooks"
        private const val OWN_METADATA_DROPS_HOOKS = "own metadata, drops hooks"

        @JvmStatic
        fun wrappers(): List<Arguments> = listOf(
            Arguments.of(DELEGATES_ALL, true, 1, 1),
            Arguments.of(OWN_METADATA, true, 1, 0),
            Arguments.of(DROPS_HOOKS, true, 1, 1),
            Arguments.of(DROPS_HOOKS, false, 0, 0),
            Arguments.of(OWN_METADATA_DROPS_HOOKS, true, 0, 0)
        )
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
