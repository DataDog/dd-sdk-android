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
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.FakeAssignment
import com.datadog.android.flags.integration.tests.utils.bodyAsJson
import com.datadog.android.flags.integration.tests.utils.httpResponse
import com.datadog.android.flags.integration.tests.utils.precomputedAssignmentsBody
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.openfeature.asOpenFeatureProvider
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
internal class ExperimentationOptOutTest {
    @Test
    fun `M forward opt out W setEvaluationContext()`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        Flags.enable(
            FlagsConfiguration.Builder().useCustomFlagEndpoint("https://flags.example/precompute").build(),
            stubSdkCore
        )
        stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = precomputedAssignmentsBody(emptyMap())) }
        val testedClient = FlagsClient.Builder(sdkCore = stubSdkCore)
            .experimentationOptOutMode(ExperimentationOptOutMode.IMMEDIATE)
            .build()

        // When
        testedClient.setEvaluationContext(
            EvaluationContext(forge.anAlphabeticalString(), mapOf(OPT_OUT_ATTRIBUTE to OPT_OUT_TIME))
        )

        // Then
        assertThat(stubSdkCore.requestsSent()).hasSize(1)
        val attributes = stubSdkCore.requestsSent().single().bodyAsJson()
            .getJSONObject("data").getJSONObject("attributes")
        assertThat(attributes.optJSONObject("evaluation_options")?.opt("experimentation_opt_out_mode"))
            .isEqualTo("immediate")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "true", "2026-02-30T10:00:00Z", "2026-10-21", "2026-10-21T10:00:00", "junk"])
    fun `M reject invalid timestamp without a request W setEvaluationContext()`(timestamp: String, forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(true)) }
        testedClient.setEvaluationContext(EvaluationContext("subject"))

        // When
        testedClient.setEvaluationContext(EvaluationContext("subject", mapOf(OPT_OUT_ATTRIBUTE to timestamp)))
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(stubSdkCore.requestsSent()).hasSize(1)
        assertThat(result.value).isFalse()
        assertThat(result.errorCode).isEqualTo(ErrorCode.INVALID_CONTEXT)
        assertThat(result.errorMessage).isEqualTo(
            "Flag 'flag': dd_experimentation_opt_out_at must be an RFC3339 timestamp string."
        )
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["2026-10-21T10:00:00Z", "2026-10-21t10:00:00z", "2099-10-21T10:00:00.123456789+04:30"])
    fun `M deliver ordinary fallback with evaluation telemetry W resolve() {opted out}`(
        timestamp: String,
        forge: Forge
    ) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(true, acknowledgment = true)) }
        testedClient.setEvaluationContext(EvaluationContext("subject", mapOf(OPT_OUT_ATTRIBUTE to timestamp)))

        // When
        val result = testedClient.resolve("flag", false)
        stubSdkCore.stopFeatures()

        // Then
        assertThat(result.value).isTrue()
        assertThat(result.errorCode).isNull()
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).hasSize(1)
    }

    @Test
    fun `M preserve experimental assignments W setEvaluationContext() {attribute absent}`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(true, doLog = true)) }

        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"))
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(result.value).isTrue()
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
        assertThat(requestOptions(stubSdkCore)).isNull()
    }

    @Test
    fun `M preserve disabled behavior W setEvaluationContext() {invalid opt out attribute}`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore, ExperimentationOptOutMode.DISABLED)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(true, doLog = true)) }

        // When
        testedClient.setEvaluationContext(EvaluationContext("subject", mapOf(OPT_OUT_ATTRIBUTE to "not a date")))
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(result.value).isTrue()
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
        assertThat(requestOptions(stubSdkCore)).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["false", "\"true\"", "null", "1", "{}"])
    @NullSource
    fun `M reject unacknowledged response W setEvaluationContext()`(acknowledgment: String?, forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(true, doLog = true)) }
        testedClient.setEvaluationContext(EvaluationContext("subject"))
        val body = JSONObject(response(true))
        if (acknowledgment != null) {
            body.getJSONObject("data").getJSONObject("attributes")
                .put("evaluation_options", JSONObject("{\"experimentation_opt_out\":$acknowledgment}"))
        }
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = body.toString()) }

        // When
        testedClient.setEvaluationContext(optedOutContext())
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(result.value).isFalse()
        assertThat(result.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(testedClient.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M reject conflicting mode acknowledgement W setEvaluationContext()`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore)
        val body = JSONObject(response(true, acknowledgment = true))
        body.getJSONObject("data").getJSONObject("attributes").getJSONObject("evaluation_options")
            .put("experimentation_opt_out_mode", "future_experiments_only")
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = body.toString()) }

        // When
        testedClient.setEvaluationContext(optedOutContext())
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(result.value).isFalse()
        assertThat(result.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
    }

    @Test
    fun `M block old assignments during opt out W setEvaluationContext()`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(true, doLog = true)) }
        testedClient.setEvaluationContext(EvaluationContext("subject"))
        var pendingValue: Boolean? = null
        stubSdkCore.stubNetworkResponses {
            pendingValue = testedClient.resolveBooleanValue("flag", false)
            httpResponse(it, body = response(false, acknowledgment = true))
        }

        // When
        testedClient.setEvaluationContext(optedOutContext())

        // Then
        assertThat(pendingValue).isFalse()
        assertThat(testedClient.resolve("flag", true).value).isFalse()
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M ignore earlier response W setEvaluationContext() {new context completes first}`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore)
        var firstRequest = true
        stubSdkCore.stubNetworkResponses {
            if (firstRequest) {
                firstRequest = false
                testedClient.setEvaluationContext(optedOutContext())
                httpResponse(it, body = response(true, doLog = true))
            } else {
                httpResponse(it, body = response(false, acknowledgment = true))
            }
        }

        // When
        testedClient.setEvaluationContext(EvaluationContext("previous-subject"))
        val result = testedClient.resolve("flag", true)

        // Then
        assertThat(result.value).isFalse()
        assertThat(result.errorCode).isNull()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M reject cached assignments from disabled mode W setEvaluationContext() {restart}`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val dataStore = stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        val initialClient = buildClient(stubSdkCore, ExperimentationOptOutMode.DISABLED, dataStore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(true, doLog = true)) }
        initialClient.setEvaluationContext(optedOutContext())
        val restartedSdkCore = FlagsClientTest.createStubSdkCore(forge)
        restartedSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME, dataStore)
        val testedClient = buildClient(restartedSdkCore, dataStore = dataStore)

        // When
        testedClient.setEvaluationContext(optedOutContext())
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(result.value).isFalse()
        assertThat(result.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(restartedSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M not persist unacknowledged assignments W setEvaluationContext() {restart offline}`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val dataStore = stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        val initialClient = buildClient(stubSdkCore, dataStore = dataStore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(true, doLog = true)) }
        initialClient.setEvaluationContext(optedOutContext())
        val restartedSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(restartedSdkCore, dataStore = dataStore)

        // When
        testedClient.setEvaluationContext(optedOutContext())
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(result.value).isFalse()
        assertThat(result.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(restartedSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M reuse acknowledged matching cache W setEvaluationContext() {restart offline}`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val dataStore = stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        val initialClient = buildClient(stubSdkCore, dataStore = dataStore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(true, acknowledgment = true)) }
        initialClient.setEvaluationContext(optedOutContext())
        val restartedSdkCore = FlagsClientTest.createStubSdkCore(forge)
        restartedSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME, dataStore)
        val testedClient = buildClient(restartedSdkCore, dataStore = dataStore)

        // When
        val beforeContext = testedClient.resolve("flag", false)
        testedClient.setEvaluationContext(optedOutContext())
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(beforeContext.value).isFalse()
        assertThat(result.value).isTrue()
        assertThat(result.errorCode).isNull()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        assertThat(restartedSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M preserve invalid typed context W onContextSet() {OpenFeature}`(forge: Forge) = runTest {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedProvider = buildClient(stubSdkCore).asOpenFeatureProvider()
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(true, doLog = true)) }
        val initial = ImmutableContext("subject")
        testedProvider.initialize(initial)
        val invalid = ImmutableContext("subject", mapOf(OPT_OUT_ATTRIBUTE to Value.Boolean(true)))

        // When
        val failure = runCatching { testedProvider.onContextSet(initial, invalid) }.exceptionOrNull()
        val result = testedProvider.getBooleanEvaluation("flag", false, invalid)

        // Then
        assertThat(failure).isNotNull()
        assertThat(result.value).isFalse()
        assertThat(result.errorCode?.name).isEqualTo("INVALID_CONTEXT")
        assertThat(stubSdkCore.requestsSent()).hasSize(1)
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M forward opt out W initialize() {OpenFeature}`(forge: Forge) = runTest {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedProvider = buildClient(stubSdkCore).asOpenFeatureProvider()
        val context = ImmutableContext("subject", mapOf(OPT_OUT_ATTRIBUTE to Value.String(OPT_OUT_TIME)))
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(true, acknowledgment = true)) }

        // When
        testedProvider.initialize(context)
        val result = testedProvider.getBooleanEvaluation("flag", false, context)

        // Then
        assertThat(result.value).isTrue()
        assertThat(result.errorCode).isNull()
        assertThat(requestOptions(stubSdkCore)?.opt("experimentation_opt_out_mode")).isEqualTo("immediate")
    }

    @Test
    fun `M return invalid context W initialize() {invalid initial OpenFeature context}`(forge: Forge) = runTest {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedProvider = buildClient(stubSdkCore).asOpenFeatureProvider()
        val context = ImmutableContext("subject", mapOf(OPT_OUT_ATTRIBUTE to Value.Null))

        // When
        val failure = runCatching { testedProvider.initialize(context) }.exceptionOrNull()
        val result = testedProvider.getBooleanEvaluation("flag", false, context)

        // Then
        assertThat(
            failure
        ).isInstanceOf(OpenFeatureError.InvalidContextError::class.java)
        assertThat(result.errorCode?.name).isEqualTo("INVALID_CONTEXT")
        assertThat(result.value).isFalse()
        assertThat(stubSdkCore.requestsSent()).isEmpty()
    }

    @Test
    fun `M deliver ordinary values W resolve() {opted out all types}`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore)
        val values = mapOf(
            "boolean" to true,
            "string" to forge.anAlphabeticalString(),
            "integer" to forge.anInt(min = 1, max = 100),
            "number" to 1.5,
            "object" to JSONObject().put("key", "value")
        )
        val body = JSONObject(
            precomputedAssignmentsBody(
                values.mapValues { (type, value) ->
                    FakeAssignment(type, value, "variant", "allocation", doLog = false)
                }
            )
        )
        body.getJSONObject("data").getJSONObject("attributes")
            .put("evaluation_options", JSONObject().put("experimentation_opt_out", true))
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = body.toString()) }

        // When
        testedClient.setEvaluationContext(optedOutContext())

        // Then
        assertThat(testedClient.resolveBooleanValue("boolean", false)).isTrue()
        assertThat(testedClient.resolveStringValue("string", "")).isEqualTo(values["string"])
        assertThat(testedClient.resolveIntValue("integer", 0)).isEqualTo(values["integer"])
        assertThat(testedClient.resolveDoubleValue("number", 0.0)).isEqualTo(1.5)
        assertThat(testedClient.resolveStructureValue("object", emptyMap<String, Any?>())).containsEntry("key", "value")
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M hide previous subject assignments W setEvaluationContext() {new subject offline}`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(true, doLog = true)) }
        testedClient.setEvaluationContext(EvaluationContext("previous-subject"))
        stubSdkCore.stubNetworkResponses { null }

        // When
        testedClient.setEvaluationContext(optedOutContext())
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(result.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(result.value).isFalse()
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M isolate pending and failed subjects W setEvaluationContext() {no cutoff}`(
        mode: ExperimentationOptOutMode,
        forge: Forge
    ) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore, mode)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(true, doLog = true)) }
        testedClient.setEvaluationContext(EvaluationContext("previous-subject"))
        var pendingValue: Boolean? = null
        var pendingError: ErrorCode? = null
        stubSdkCore.stubNetworkResponses {
            val pending = testedClient.resolve("flag", false)
            pendingValue = pending.value
            pendingError = pending.errorCode
            null
        }

        // When
        testedClient.setEvaluationContext(EvaluationContext("requested-subject"))
        val result = testedClient.resolve("flag", false)

        // Then
        val disabled = mode == ExperimentationOptOutMode.DISABLED
        assertThat(pendingValue).isEqualTo(disabled)
        assertThat(result.value).isEqualTo(disabled)
        assertThat(pendingError).isEqualTo(if (disabled) null else ErrorCode.PROVIDER_NOT_READY)
        assertThat(result.errorCode).isEqualTo(if (disabled) null else ErrorCode.PROVIDER_NOT_READY)
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M require requested context before cache reuse W resolve() {startup without cutoff}`(
        mode: ExperimentationOptOutMode,
        forge: Forge
    ) {
        // Given
        val initialCore = FlagsClientTest.createStubSdkCore(forge)
        val store = initialCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        val initialClient = buildClient(initialCore, mode, store)
        initialCore.stubNetworkResponses { httpResponse(it, body = response(true, doLog = true)) }
        val context = EvaluationContext("subject")
        initialClient.setEvaluationContext(context)
        val restartedCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(restartedCore, mode, store)

        // When
        val beforeContext = testedClient.resolve("flag", false)
        testedClient.setEvaluationContext(context)
        val afterContext = testedClient.resolve("flag", false)

        // Then
        val disabled = mode == ExperimentationOptOutMode.DISABLED
        assertThat(beforeContext.value).isEqualTo(disabled)
        assertThat(beforeContext.errorCode).isEqualTo(if (disabled) null else ErrorCode.PROVIDER_NOT_READY)
        assertThat(afterContext.value).isTrue()
        assertThat(afterContext.errorCode).isNull()
    }

    @ParameterizedTest
    @MethodSource("independentControls")
    fun `M keep telemetry and experimentation independent W initialize() {OpenFeature}`(
        mode: ExperimentationOptOutMode,
        telemetryOptedOut: Boolean,
        forge: Forge
    ) = runTest {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore, mode, telemetryParameterEnabled = true)
        val provider = testedClient.asOpenFeatureProvider()
        val attributes = mapOf(
            OPT_OUT_ATTRIBUTE to Value.String(OPT_OUT_TIME),
            "dd_evaluation_telemetry_opt_out" to Value.Boolean(telemetryOptedOut)
        )
        val body = JSONObject(response(true, acknowledgment = true))
        if (mode == ExperimentationOptOutMode.FUTURE_EXPERIMENTS_ONLY) {
            body.getJSONObject("data").getJSONObject("attributes").getJSONObject("evaluation_options")
                .put("experimentation_opt_out_mode", "future_experiments_only")
                .put(OPT_OUT_ATTRIBUTE, OPT_OUT_TIME)
        }
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = body.toString()) }
        val context = ImmutableContext("subject", attributes)

        // When
        provider.initialize(context)
        val result = provider.getBooleanEvaluation("flag", false, context)
        stubSdkCore.stopFeatures()

        // Then
        assertThat(result.value).isTrue()
        assertThat(result.errorCode).isNull()
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_EVALUATIONS_FEATURE_NAME))
            .hasSize(if (telemetryOptedOut) 0 else 1)
        val request = stubSdkCore.requestsSent().single().bodyAsJson()
            .getJSONObject("data").getJSONObject("attributes").getJSONObject("subject")
        assertThat(request.getString("targeting_key")).isEqualTo("subject")
    }

    private fun buildClient(
        sdkCore: StubSDKCore,
        mode: ExperimentationOptOutMode = ExperimentationOptOutMode.IMMEDIATE,
        dataStore: DataStoreHandler = StubDataStoreHandler(),
        telemetryParameterEnabled: Boolean = false
    ): FlagsClient {
        sdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME, dataStore)
        Flags.enable(
            FlagsConfiguration.Builder().useCustomFlagEndpoint("https://flags.example/precompute").build(),
            sdkCore
        )
        return FlagsClient.Builder(sdkCore = sdkCore).experimentationOptOutMode(mode)
            .enableEvaluationTelemetryOptOutParameter(telemetryParameterEnabled).build()
    }

    private fun optedOutContext() = EvaluationContext("subject", mapOf(OPT_OUT_ATTRIBUTE to OPT_OUT_TIME))

    private fun requestOptions(sdkCore: StubSDKCore) = sdkCore.requestsSent().single().bodyAsJson()
        .getJSONObject("data").getJSONObject("attributes").optJSONObject("evaluation_options")

    private fun response(value: Boolean, acknowledgment: Boolean? = null, doLog: Boolean = false): String {
        val body = JSONObject(
            precomputedAssignmentsBody(
                mapOf("flag" to FakeAssignment("boolean", value, "variant", "allocation", doLog))
            )
        )
        acknowledgment?.let {
            body.getJSONObject("data").getJSONObject("attributes")
                .put("evaluation_options", JSONObject().put("experimentation_opt_out", it))
        }
        return body.toString()
    }

    private companion object {
        @JvmStatic
        fun independentControls() = listOf(
            Arguments.of(ExperimentationOptOutMode.IMMEDIATE, false),
            Arguments.of(ExperimentationOptOutMode.IMMEDIATE, true),
            Arguments.of(ExperimentationOptOutMode.FUTURE_EXPERIMENTS_ONLY, false),
            Arguments.of(ExperimentationOptOutMode.FUTURE_EXPERIMENTS_ONLY, true)
        )

        const val OPT_OUT_ATTRIBUTE = "dd_experimentation_opt_out_at"
        const val OPT_OUT_TIME = "2026-10-21T10:00:00Z"
    }
}
