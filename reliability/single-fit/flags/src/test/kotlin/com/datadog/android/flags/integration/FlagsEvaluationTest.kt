/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.integration.FlagsClientTest.Companion.createStubSdkCore
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.FakeAssignment
import com.datadog.android.flags.integration.tests.utils.bodyAsJson
import com.datadog.android.flags.integration.tests.utils.httpResponse
import com.datadog.android.flags.integration.tests.utils.precomputedAssignmentsBody
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.model.ResolutionReason
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

/**
 * Covers the flow from [FlagsClient.setEvaluationContext] to the resolved values and the events they produce.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlagsEvaluationTest {

    private lateinit var stubSdkCore: StubSDKCore

    private lateinit var fakeFlagEndpoint: String

    @StringForgery
    lateinit var fakeFlagKey: String

    @StringForgery
    lateinit var fakeTargetingKey: String

    @StringForgery
    lateinit var fakeVariationKey: String

    @StringForgery
    lateinit var fakeAllocationKey: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = createStubSdkCore(forge)
        stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        fakeFlagEndpoint = "https://${forge.anAlphabeticalString()}.com/precompute-assignments"
    }

    // region Request

    @Test
    fun `M send the evaluation context W setEvaluationContext()`(
        @StringForgery fakeAttributeKey: String,
        @StringForgery fakeAttributeValue: String
    ) {
        // Given
        val client = enableFlagsAndBuildClient()
        val datadogContext = stubSdkCore.getDatadogContext(emptySet())

        // When
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey, mapOf(fakeAttributeKey to fakeAttributeValue)))

        // Then
        val request = stubSdkCore.requestsSent().single()
        assertThat(request.url.toString()).isEqualTo(fakeFlagEndpoint)
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.header("dd-client-token")).isEqualTo(datadogContext.clientToken)
        val attributes = request.bodyAsJson().getJSONObject("data").getJSONObject("attributes")
        val subject = attributes.getJSONObject("subject")
        assertThat(subject.getString("targeting_key")).isEqualTo(fakeTargetingKey)
        assertThat(subject.getJSONObject("targeting_attributes").getString(fakeAttributeKey))
            .isEqualTo(fakeAttributeValue)
        assertThat(attributes.getJSONObject("env").getString("dd_env")).isEqualTo(datadogContext.env)
        assertThat(attributes.getJSONObject("source").getString("sdk_name")).isEqualTo("dd-sdk-android")
    }

    // endregion

    // region Resolution

    @Test
    fun `M resolve the assigned value W resolveXxxValue() {all flag types}`(
        @BoolForgery fakeBoolean: Boolean,
        @StringForgery fakeString: String,
        @IntForgery fakeInt: Int,
        @DoubleForgery fakeDouble: Double,
        @StringForgery fakeStructureKey: String,
        @StringForgery fakeStructureValue: String
    ) {
        // Given
        stubAssignments(
            mapOf(
                "bool" to assignment("boolean", fakeBoolean),
                "string" to assignment("string", fakeString),
                "int" to assignment("integer", fakeInt),
                "double" to assignment("number", fakeDouble),
                "object" to assignment("object", JSONObject().put(fakeStructureKey, fakeStructureValue))
            )
        )
        val client = enableFlagsAndBuildClient()

        // When
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))

        // Then
        assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        assertThat(client.resolveBooleanValue("bool", !fakeBoolean)).isEqualTo(fakeBoolean)
        assertThat(client.resolveStringValue("string", "")).isEqualTo(fakeString)
        assertThat(client.resolveIntValue("int", fakeInt.inv())).isEqualTo(fakeInt)
        assertThat(client.resolveDoubleValue("double", fakeDouble + 1)).isEqualTo(fakeDouble)
        assertThat(client.resolveStructureValue("object", JSONObject()).getString(fakeStructureKey))
            .isEqualTo(fakeStructureValue)
        assertThat(client.resolveStructureValue("object", emptyMap<String, Any?>()))
            .containsEntry(fakeStructureKey, fakeStructureValue)
    }

    @Test
    fun `M provide resolution details W resolve()`(
        @BoolForgery fakeValue: Boolean,
        @StringForgery fakeExtraKey: String,
        @StringForgery fakeExtraValue: String
    ) {
        // Given
        stubAssignments(
            mapOf(
                fakeFlagKey to assignment("boolean", fakeValue).copy(
                    reason = "SPLIT",
                    extraLogging = mapOf(fakeExtraKey to fakeExtraValue)
                )
            )
        )
        val client = enableFlagsAndBuildClient()
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))

        // When
        val details = client.resolve(fakeFlagKey, !fakeValue)

        // Then
        assertThat(details.value).isEqualTo(fakeValue)
        assertThat(details.variant).isEqualTo(fakeVariationKey)
        assertThat(details.reason).isEqualTo(ResolutionReason.SPLIT)
        assertThat(details.errorCode).isNull()
        assertThat(details.flagMetadata)
            .containsEntry("allocationKey", fakeAllocationKey)
            .containsEntry(fakeExtraKey, fakeExtraValue)
    }

    @Test
    fun `M resolve the default value W resolve() {flag not assigned}`(
        @StringForgery fakeOtherFlagKey: String,
        @BoolForgery fakeDefaultValue: Boolean
    ) {
        // Given
        stubAssignments(mapOf(fakeOtherFlagKey to assignment("boolean", !fakeDefaultValue)))
        val client = enableFlagsAndBuildClient()
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))

        // When
        val details = client.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(details.value).isEqualTo(fakeDefaultValue)
        assertThat(details.reason).isEqualTo(ResolutionReason.ERROR)
        assertThat(details.errorCode).isEqualTo(ErrorCode.FLAG_NOT_FOUND)
    }

    @Test
    fun `M resolve the default value W resolve() {type mismatch}`(
        @StringForgery fakeValue: String,
        @BoolForgery fakeDefaultValue: Boolean
    ) {
        // Given
        stubAssignments(mapOf(fakeFlagKey to assignment("string", fakeValue)))
        val client = enableFlagsAndBuildClient()
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))

        // When
        val details = client.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(details.value).isEqualTo(fakeDefaultValue)
        assertThat(details.errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
    }

    @Test
    fun `M resolve the default value W resolve() {context never set}`(
        @BoolForgery fakeDefaultValue: Boolean
    ) {
        // Given
        stubAssignments(mapOf(fakeFlagKey to assignment("boolean", !fakeDefaultValue)))
        val client = enableFlagsAndBuildClient()

        // When
        val details = client.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(details.value).isEqualTo(fakeDefaultValue)
        assertThat(details.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(stubSdkCore.requestsSent()).isEmpty()
    }

    // endregion

    // region Failures

    @Test
    fun `M report an error W setEvaluationContext() {network failure}`(
        @BoolForgery fakeDefaultValue: Boolean
    ) {
        // Given
        stubSdkCore.stubNetworkResponses { null }
        val client = enableFlagsAndBuildClient()
        val mockCallback = mock<EvaluationContextCallback>()

        // When
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey), mockCallback)

        // Then
        val errorCaptor = argumentCaptor<Throwable>()
        verify(mockCallback).onFailure(errorCaptor.capture())
        verify(mockCallback, never()).onSuccess()
        val state = client.state.getCurrentState()
        assertThat(state).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat((state as FlagsClientState.Error).error).isSameAs(errorCaptor.firstValue)
        assertThat(client.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M report an error W setEvaluationContext() {server error}`(
        @IntForgery(500, 600) fakeStatusCode: Int
    ) {
        // Given
        stubSdkCore.stubNetworkResponses { httpResponse(it, code = fakeStatusCode) }
        val client = enableFlagsAndBuildClient()

        // When
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))

        // Then
        assertThat(client.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(stubSdkCore.telemetryEventsWritten().map { it.message })
            .contains("Flag assignment server returned error ($fakeStatusCode)")
    }

    @Test
    fun `M keep the previous flags W setEvaluationContext() {second fetch fails}`(
        @StringForgery fakeOtherTargetingKey: String,
        @BoolForgery fakeValue: Boolean
    ) {
        // Given
        stubAssignments(mapOf(fakeFlagKey to assignment("boolean", fakeValue)))
        val client = enableFlagsAndBuildClient()
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))
        stubSdkCore.stubNetworkResponses { null }

        // When
        client.setEvaluationContext(EvaluationContext(fakeOtherTargetingKey))

        // Then
        assertThat(client.resolveBooleanValue(fakeFlagKey, !fakeValue)).isEqualTo(fakeValue)
    }

    // endregion

    // region Persistence

    @Test
    fun `M resolve the persisted flags W build() {after restart, network unavailable}`(
        @BoolForgery fakeValue: Boolean,
        forge: Forge
    ) {
        // Given
        val dataStore = stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        stubAssignments(mapOf(fakeFlagKey to assignment("boolean", fakeValue)))
        enableFlagsAndBuildClient().setEvaluationContext(EvaluationContext(fakeTargetingKey))
        val restartedSdkCore = createStubSdkCore(forge)
        restartedSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME, dataStore)
        restartedSdkCore.stubNetworkResponses { null }
        val client = enableFlagsAndBuildClient(sdkCore = restartedSdkCore)

        // When
        val details = client.resolve(fakeFlagKey, !fakeValue)

        // Then
        assertThat(details.value).isEqualTo(fakeValue)
        assertThat(details.reason).isEqualTo(ResolutionReason.CACHED)
        assertThat(details.variant).isEqualTo(fakeVariationKey)
        val exposure = JSONObject(restartedSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME).single().eventData)
        assertThat(exposure.getJSONObject("flag").getString("key")).isEqualTo(fakeFlagKey)
        assertThat(exposure.getJSONObject("subject").getString("id")).isEqualTo(fakeTargetingKey)
        assertThat(exposure.getJSONObject("variant").getString("key")).isEqualTo(fakeVariationKey)
        assertThat(exposure.getJSONObject("allocation").getString("key")).isEqualTo(fakeAllocationKey)
    }

    // endregion

    // region Exposures

    @Test
    fun `M write an exposure event W resolveBooleanValue() {doLog=true}`(
        @BoolForgery fakeValue: Boolean,
        @StringForgery fakeAttributeKey: String,
        @StringForgery fakeAttributeValue: String
    ) {
        // Given
        stubAssignments(mapOf(fakeFlagKey to assignment("boolean", fakeValue)))
        val client = enableFlagsAndBuildClient()
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey, mapOf(fakeAttributeKey to fakeAttributeValue)))

        // When
        client.resolveBooleanValue(fakeFlagKey, !fakeValue)

        // Then
        val exposure = JSONObject(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME).single().eventData)
        assertThat(exposure.getJSONObject("flag").getString("key")).isEqualTo(fakeFlagKey)
        assertThat(exposure.getJSONObject("variant").getString("key")).isEqualTo(fakeVariationKey)
        assertThat(exposure.getJSONObject("allocation").getString("key")).isEqualTo(fakeAllocationKey)
        val subject = exposure.getJSONObject("subject")
        assertThat(subject.getString("id")).isEqualTo(fakeTargetingKey)
        assertThat(subject.getJSONObject("attributes").getString(fakeAttributeKey)).isEqualTo(fakeAttributeValue)
    }

    @Test
    fun `M write a single exposure event W resolveBooleanValue() {same flag resolved twice}`(
        @BoolForgery fakeValue: Boolean
    ) {
        // Given
        stubAssignments(mapOf(fakeFlagKey to assignment("boolean", fakeValue)))
        val client = enableFlagsAndBuildClient()
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))

        // When
        client.resolveBooleanValue(fakeFlagKey, !fakeValue)
        client.resolveBooleanValue(fakeFlagKey, !fakeValue)

        // Then
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).hasSize(1)
    }

    @Test
    fun `M not write an exposure event W resolveBooleanValue() {doLog=false}`(
        @BoolForgery fakeValue: Boolean
    ) {
        // Given
        stubAssignments(mapOf(fakeFlagKey to assignment("boolean", fakeValue).copy(doLog = false)))
        val client = enableFlagsAndBuildClient()
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))

        // When
        client.resolveBooleanValue(fakeFlagKey, !fakeValue)

        // Then
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    @Test
    fun `M not write an exposure event W resolveBooleanValue() {trackExposures(false)}`(
        @BoolForgery fakeValue: Boolean
    ) {
        // Given
        stubAssignments(mapOf(fakeFlagKey to assignment("boolean", fakeValue)))
        val client = enableFlagsAndBuildClient(configure = { trackExposures(false) })
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))

        // When
        client.resolveBooleanValue(fakeFlagKey, !fakeValue)

        // Then
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_FEATURE_NAME)).isEmpty()
    }

    // endregion

    // region Evaluations

    @Test
    fun `M write an aggregated evaluation event W resolveBooleanValue() + stop`(
        @BoolForgery fakeValue: Boolean,
        @IntForgery(2, 10) fakeEvaluationCount: Int
    ) {
        // Given
        stubAssignments(mapOf(fakeFlagKey to assignment("boolean", fakeValue)))
        val client = enableFlagsAndBuildClient()
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))

        // When
        repeat(fakeEvaluationCount) { client.resolveBooleanValue(fakeFlagKey, !fakeValue) }
        stubSdkCore.stopFeatures()

        // Then
        val evaluation = JSONObject(
            stubSdkCore.eventsWritten(Feature.FLAGS_EVALUATIONS_FEATURE_NAME).single().eventData
        )
        assertThat(evaluation.getJSONObject("flag").getString("key")).isEqualTo(fakeFlagKey)
        assertThat(evaluation.getJSONObject("variant").getString("key")).isEqualTo(fakeVariationKey)
        assertThat(evaluation.getJSONObject("allocation").getString("key")).isEqualTo(fakeAllocationKey)
        assertThat(evaluation.getString("targeting_key")).isEqualTo(fakeTargetingKey)
        assertThat(evaluation.getInt("evaluation_count")).isEqualTo(fakeEvaluationCount)
    }

    @Test
    fun `M not write an evaluation event W resolveBooleanValue() + stop {trackEvaluations(false)}`(
        @BoolForgery fakeValue: Boolean
    ) {
        // Given
        stubAssignments(mapOf(fakeFlagKey to assignment("boolean", fakeValue)))
        val client = enableFlagsAndBuildClient(configure = { trackEvaluations(false) })
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))

        // When
        client.resolveBooleanValue(fakeFlagKey, !fakeValue)
        stubSdkCore.stopFeatures()

        // Then
        assertThat(stubSdkCore.eventsWritten(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)).isEmpty()
    }

    // endregion

    // region Internal

    private fun enableFlagsAndBuildClient(
        sdkCore: StubSDKCore = stubSdkCore,
        configure: FlagsConfiguration.Builder.() -> FlagsConfiguration.Builder = { this }
    ): FlagsClient {
        val configuration = FlagsConfiguration.Builder()
            .useCustomFlagEndpoint(fakeFlagEndpoint)
            .configure()
            .build()
        Flags.enable(configuration, sdkCore)
        return FlagsClient.Builder(sdkCore = sdkCore).build()
    }

    private fun stubAssignments(assignments: Map<String, FakeAssignment>) {
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = precomputedAssignmentsBody(assignments)) }
    }

    private fun assignment(variationType: String, variationValue: Any): FakeAssignment {
        return FakeAssignment(
            variationType = variationType,
            variationValue = variationValue,
            variationKey = fakeVariationKey,
            allocationKey = fakeAllocationKey
        )
    }

    // endregion
}
