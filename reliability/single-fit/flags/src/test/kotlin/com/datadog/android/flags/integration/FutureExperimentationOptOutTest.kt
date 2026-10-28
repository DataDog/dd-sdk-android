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
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
internal class FutureExperimentationOptOutTest {
    @ParameterizedTest
    @ValueSource(
        strings = [CUTOFF, "2026-10-21T14:30:00.123456789+04:30", "2026-10-21t10:00:00.123456789987z"]
    )
    fun `M accept equivalent cutoff and retain existing exposure W setEvaluationContext()`(
        cutoff: String,
        forge: Forge
    ) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(acknowledgement(), doLog = true)) }

        // When
        testedClient.setEvaluationContext(context(cutoff))
        val result = testedClient.resolve("flag", false)
        stubSdkCore.stopFeatures()

        // Then
        assertThat(result.value).isTrue()
        assertThat(result.errorCode).isNull()
        val attributes = stubSdkCore.requestsSent().single().bodyAsJson()
            .getJSONObject("data").getJSONObject("attributes")
        assertThat(attributes.getJSONObject("evaluation_options").toString())
            .isEqualTo("{\"experimentation_opt_out_mode\":\"future_experiments_only\"}")
        assertThat(attributes.getJSONObject("subject").getJSONObject("targeting_attributes").getString(ATTRIBUTE))
            .isEqualTo(cutoff)
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).hasSize(1)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
        strings = [
            "{}", "{\"experimentation_opt_out\":true}",
            "{\"experimentation_opt_out_mode\":\"future_experiments_only\"}",
            "{\"experimentation_opt_out_mode\":\"immediate\",\"dd_experimentation_opt_out_at\":\"$CUTOFF\"}",
            "{\"experimentation_opt_out_mode\":\"future_experiments_only\",\"dd_experimentation_opt_out_at\":true}",
            "{\"experimentation_opt_out_mode\":\"future_experiments_only\",\"dd_experimentation_opt_out_at\":\"junk\"}",
            "{\"experimentation_opt_out_mode\":\"future_experiments_only\",\"dd_experimentation_opt_out_at\":" +
                "\"2026-10-21T10:00:00.123456788Z\"}",
            "{\"experimentation_opt_out_mode\":\"future_experiments_only\",\"dd_experimentation_opt_out_at\":" +
                "\"2026-10-21T10:00:00.123Z\"}"
        ]
    )
    fun `M reject mismatched acknowledgement W setEvaluationContext()`(options: String?, forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(options)) }

        // When
        testedClient.setEvaluationContext(context())
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(result.value).isFalse()
        assertThat(result.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(testedClient.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M permit experimentation without acknowledgement W setEvaluationContext() {timestamp absent}`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(null, doLog = true)) }

        // When
        testedClient.setEvaluationContext(EvaluationContext("subject"))
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(result.value).isTrue()
        assertThat(result.errorCode).isNull()
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "true", "2026-02-30T10:00:00Z", "2026-10-21", "junk"])
    fun `M reject invalid cutoff before HTTP W setEvaluationContext()`(cutoff: String, forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore)

        // When
        testedClient.setEvaluationContext(context(cutoff))
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(result.errorCode).isEqualTo(ErrorCode.INVALID_CONTEXT)
        assertThat(result.value).isFalse()
        assertThat(stubSdkCore.requestsSent()).isEmpty()
    }

    @Test
    fun `M forward future mode W initialize() {OpenFeature}`(forge: Forge) = runTest {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedProvider = buildClient(stubSdkCore).asOpenFeatureProvider()
        val context = ImmutableContext("subject", mapOf(ATTRIBUTE to Value.String(CUTOFF)))
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(acknowledgement())) }

        // When
        testedProvider.initialize(context)
        val result = testedProvider.getBooleanEvaluation("flag", false, context)

        // Then
        assertThat(result.value).isTrue()
        assertThat(result.errorCode).isNull()
        val attributes = stubSdkCore.requestsSent().single().bodyAsJson()
            .getJSONObject("data").getJSONObject("attributes")
        assertThat(attributes.getJSONObject("evaluation_options").getString("experimentation_opt_out_mode"))
            .isEqualTo("future_experiments_only")
    }

    @Test
    fun `M reject invalid typed cutoff W initialize() {OpenFeature}`(forge: Forge) = runTest {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedProvider = buildClient(stubSdkCore).asOpenFeatureProvider()
        val context = ImmutableContext("subject", mapOf(ATTRIBUTE to Value.Integer(123)))

        // When
        val failure = runCatching { testedProvider.initialize(context) }.exceptionOrNull()
        val result = testedProvider.getBooleanEvaluation("flag", false, context)

        // Then
        assertThat(failure).isNotNull()
        assertThat(result.errorCode?.name).isEqualTo("INVALID_CONTEXT")
        assertThat(stubSdkCore.requestsSent()).isEmpty()
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class, names = ["DISABLED", "IMMEDIATE", "FUTURE_EXPERIMENTS_ONLY"])
    fun `M reuse only matching mode cache W setEvaluationContext() {restart offline}`(
        initialMode: ExperimentationOptOutMode,
        forge: Forge
    ) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val dataStore = StubDataStoreHandler()
        val initialClient = buildClient(stubSdkCore, initialMode, dataStore)
        val options = when (initialMode) {
            ExperimentationOptOutMode.DISABLED -> null
            ExperimentationOptOutMode.IMMEDIATE -> "{\"experimentation_opt_out\":true}"
            ExperimentationOptOutMode.FUTURE_EXPERIMENTS_ONLY -> acknowledgement()
        }
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(options)) }
        initialClient.setEvaluationContext(context())
        assertThat(initialClient.resolve("flag", false).value).isTrue()
        val restartedSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(restartedSdkCore, dataStore = dataStore)

        // When
        val beforeContext = testedClient.resolve("flag", false)
        testedClient.setEvaluationContext(context())
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(beforeContext.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(result.value).isEqualTo(initialMode == ExperimentationOptOutMode.FUTURE_EXPERIMENTS_ONLY)
    }

    @Test
    fun `M reject future cache W setEvaluationContext() {restart in immediate mode}`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val dataStore = StubDataStoreHandler()
        val initialClient = buildClient(stubSdkCore, dataStore = dataStore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(acknowledgement(), doLog = true)) }
        initialClient.setEvaluationContext(context())
        val restartedSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(restartedSdkCore, ExperimentationOptOutMode.IMMEDIATE, dataStore)

        // When
        testedClient.setEvaluationContext(context())
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(result.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(result.value).isFalse()
        assertThat(restartedSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M not persist mismatched acknowledgement W setEvaluationContext() {restart offline}`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val dataStore = StubDataStoreHandler()
        val initialClient = buildClient(stubSdkCore, dataStore = dataStore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response("{\"experimentation_opt_out\":true}")) }
        initialClient.setEvaluationContext(context())
        val restartedSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(restartedSdkCore, dataStore = dataStore)

        // When
        testedClient.setEvaluationContext(context())
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(result.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(result.value).isFalse()
    }

    @Test
    fun `M reject different cutoff cache W setEvaluationContext() {restart offline}`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val dataStore = StubDataStoreHandler()
        val initialClient = buildClient(stubSdkCore, dataStore = dataStore)
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(acknowledgement())) }
        initialClient.setEvaluationContext(context())
        val restartedSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(restartedSdkCore, dataStore = dataStore)

        // When
        testedClient.setEvaluationContext(context("2026-10-21T10:00:00.123456788Z"))
        val result = testedClient.resolve("flag", false)

        // Then
        assertThat(result.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(result.value).isFalse()
    }

    @Test
    fun `M discard superseded cutoff response W setEvaluationContext()`(forge: Forge) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(stubSdkCore)
        val newerCutoff = "2026-10-21T10:00:00.123456788Z"
        var firstRequest = true
        stubSdkCore.stubNetworkResponses {
            if (firstRequest) {
                firstRequest = false
                testedClient.setEvaluationContext(context(newerCutoff))
                httpResponse(it, body = response(acknowledgement()))
            } else {
                httpResponse(it, body = response(acknowledgement(newerCutoff), value = false))
            }
        }

        // When
        testedClient.setEvaluationContext(context())
        val result = testedClient.resolve("flag", true)

        // Then
        assertThat(result.value).isFalse()
        assertThat(result.errorCode).isNull()
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M report cached keys before readability W onFirstFlags() {restart}`(
        mode: ExperimentationOptOutMode,
        forge: Forge
    ) {
        // Given
        val stubSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val dataStore = StubDataStoreHandler()
        val initialClient = buildClient(stubSdkCore, mode, dataStore)
        val options = when (mode) {
            ExperimentationOptOutMode.DISABLED -> null
            ExperimentationOptOutMode.IMMEDIATE -> "{\"experimentation_opt_out\":true}"
            ExperimentationOptOutMode.FUTURE_EXPERIMENTS_ONLY -> acknowledgement()
        }
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = response(options)) }
        initialClient.setEvaluationContext(context())
        assertThat(initialClient.resolve("flag", false).value).isTrue()
        val restartedSdkCore = FlagsClientTest.createStubSdkCore(forge)
        val testedClient = buildClient(restartedSdkCore, mode, dataStore)
        val notifications = mutableListOf<Set<String>>()

        // When
        testedClient.onFirstFlags { notifications.add(checkNotNull(it.flagsChanged).toSet()) }
        val beforeContext = testedClient.resolve("flag", false)
        testedClient.setEvaluationContext(context())
        val afterContext = testedClient.resolve("flag", false)

        // Then
        assertThat(notifications).containsExactly(setOf("flag"))
        assertThat(beforeContext.value).isEqualTo(mode == ExperimentationOptOutMode.DISABLED)
        assertThat(afterContext.value).isTrue()
    }

    private fun buildClient(
        sdkCore: StubSDKCore,
        mode: ExperimentationOptOutMode = ExperimentationOptOutMode.FUTURE_EXPERIMENTS_ONLY,
        dataStore: DataStoreHandler = StubDataStoreHandler()
    ): FlagsClient {
        sdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME, dataStore)
        Flags.enable(
            FlagsConfiguration.Builder().useCustomFlagEndpoint("https://flags.example/precompute").build(),
            sdkCore
        )
        return FlagsClient.Builder(sdkCore = sdkCore).experimentationOptOutMode(mode).build()
    }

    private fun context(cutoff: String = CUTOFF) = EvaluationContext("subject", mapOf(ATTRIBUTE to cutoff))

    private fun acknowledgement(cutoff: String = CUTOFF) =
        "{\"experimentation_opt_out_mode\":\"future_experiments_only\",\"dd_experimentation_opt_out_at\":\"$cutoff\"}"

    private fun response(options: String?, doLog: Boolean = false, value: Boolean = true): String {
        val body = JSONObject(
            precomputedAssignmentsBody(
                mapOf("flag" to FakeAssignment("boolean", value, "variant", "allocation", doLog))
            )
        )
        if (options != null) {
            body.getJSONObject("data").getJSONObject("attributes").put("evaluation_options", JSONObject(options))
        }
        return body.toString()
    }

    private companion object {
        const val ATTRIBUTE = "dd_experimentation_opt_out_at"
        const val CUTOFF = "2026-10-21T10:00:00.123456789Z"
    }
}
