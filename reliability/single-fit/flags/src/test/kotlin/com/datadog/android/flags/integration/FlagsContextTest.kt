/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.integration.FlagsClientTest.Companion.createStubSdkCore
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.DeferredDataStore
import com.datadog.android.flags.integration.tests.utils.FakeAssignment
import com.datadog.android.flags.integration.tests.utils.httpResponse
import com.datadog.android.flags.integration.tests.utils.precomputedAssignmentsBody
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.model.ResolutionReason
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
internal class FlagsContextTest {
    private lateinit var stubSdkCore: StubSDKCore
    private lateinit var testedClient: FlagsClient
    private lateinit var fakeContext: EvaluationContext
    private lateinit var fakeAssignment: FakeAssignment
    private lateinit var fakeEndpoint: String
    private lateinit var fakeDataStore: DeferredDataStore
    private val firstFlags = mutableListOf<List<String>?>()

    @StringForgery
    lateinit var fakeFlagKey: String

    @BoolForgery
    var fakeValue: Boolean = false

    @BeforeEach
    fun setUp(forge: Forge) {
        fakeContext = EvaluationContext(forge.anAlphabeticalString())
        fakeAssignment =
            FakeAssignment("boolean", fakeValue, forge.anAlphabeticalString(), forge.anAlphabeticalString())
        fakeEndpoint = "https://${forge.anAlphabeticalString()}.com/precompute-assignments"
        fakeDataStore = DeferredDataStore()
        stubSdkCore = createStubSdkCore(forge)
        buildClient()
    }

    @AfterEach
    fun tearDown() {
        stubSdkCore.stopFeatures()
    }

    @ParameterizedTest
    @EnumSource(FetchFailure::class)
    fun `M retain matching flags W setEvaluationContext() {refresh fails}`(failure: FetchFailure) {
        // Given
        installFlags()
        stubFailure(failure)

        // When
        testedClient.setEvaluationContext(fakeContext)

        // Then
        assertThat(testedClient.resolveBooleanValue(fakeFlagKey, !fakeValue)).isEqualTo(fakeValue)
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        assertThat(firstFlags).containsExactly(listOf(fakeFlagKey))
    }

    @ParameterizedTest
    @EnumSource(FetchFailure::class)
    fun `M return default W setEvaluationContext() {different targeting key fails}`(
        failure: FetchFailure,
        @StringForgery fakeOtherTargetingKey: String
    ) {
        // Given
        installFlags()
        stubFailure(failure)

        // When
        testedClient.setEvaluationContext(fakeContext.copy(targetingKey = fakeOtherTargetingKey))

        // Then
        assertDefaultAfterFailedSwitch()
    }

    @ParameterizedTest
    @EnumSource(FetchFailure::class)
    fun `M return default W setEvaluationContext() {different attributes fail}`(
        failure: FetchFailure,
        @StringForgery fakeAttributeKey: String,
        @StringForgery fakeAttributeValue: String
    ) {
        // Given
        installFlags()
        stubFailure(failure)

        // When
        testedClient.setEvaluationContext(fakeContext.copy(attributes = mapOf(fakeAttributeKey to fakeAttributeValue)))

        // Then
        assertDefaultAfterFailedSwitch()
    }

    @Test
    fun `M wait for first flags W setEvaluationContext() {initial fetch fails}`(
        @BoolForgery malformedResponse: Boolean
    ) {
        // Given
        stubFailure(if (malformedResponse) FetchFailure.MALFORMED_RESPONSE else FetchFailure.NETWORK)

        // When
        testedClient.setEvaluationContext(fakeContext)

        // Then
        assertThat(firstFlags).isEmpty()
        val details = testedClient.resolve(fakeFlagKey, !fakeValue)
        assertThat(details.value).isEqualTo(!fakeValue)
        assertThat(details.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
    }

    @Test
    fun `M deliver first flags once W setEvaluationContext() {empty success after failure}`() {
        // Given
        stubFailure(FetchFailure.NETWORK)
        testedClient.setEvaluationContext(fakeContext)
        stubAssignments(emptyMap())

        // When
        testedClient.setEvaluationContext(fakeContext)
        testedClient.setEvaluationContext(fakeContext)

        // Then
        assertThat(firstFlags).containsExactly(emptyList())
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
    }

    @ParameterizedTest
    @EnumSource(FetchFailure::class)
    fun `M reject old cached flags W completeReads() {different context failed}`(
        failure: FetchFailure,
        @StringForgery fakeOtherTargetingKey: String,
        forge: Forge
    ) {
        // Given
        restartWithPendingCache(forge)
        stubFailure(failure)
        testedClient.setEvaluationContext(EvaluationContext(fakeOtherTargetingKey))

        // When
        fakeDataStore.completeReads()

        // Then
        assertThat(testedClient.resolveBooleanValue(fakeFlagKey, !fakeValue)).isEqualTo(!fakeValue)
        assertThat(firstFlags).isEmpty()
    }

    @Test
    fun `M accept matching cached flags W completeReads() {same context failed}`(forge: Forge) {
        // Given
        restartWithPendingCache(forge)
        stubFailure(FetchFailure.NETWORK)
        testedClient.setEvaluationContext(fakeContext)

        // When
        fakeDataStore.completeReads()

        // Then
        assertThat(testedClient.resolve(fakeFlagKey, !fakeValue).reason).isEqualTo(ResolutionReason.CACHED)
        assertThat(testedClient.resolveBooleanValue(fakeFlagKey, !fakeValue)).isEqualTo(fakeValue)
        assertThat(firstFlags).containsExactly(listOf(fakeFlagKey))
    }

    @ParameterizedTest
    @EnumSource(FetchFailure::class)
    fun `M deliver first flags once W setEvaluationContext() {success after rejected cache}`(
        failure: FetchFailure,
        @StringForgery fakeOtherTargetingKey: String,
        @StringForgery fakeNewFlagKey: String,
        forge: Forge
    ) {
        // Given
        restartWithPendingCache(forge)
        val fakeNewContext = EvaluationContext(fakeOtherTargetingKey)
        stubFailure(failure)
        testedClient.setEvaluationContext(fakeNewContext)
        fakeDataStore.completeReads()
        stubAssignments(mapOf(fakeNewFlagKey to fakeAssignment))

        // When
        testedClient.setEvaluationContext(fakeNewContext)
        testedClient.setEvaluationContext(fakeNewContext)

        // Then
        assertThat(firstFlags).containsExactly(listOf(fakeNewFlagKey))
        assertThat(testedClient.resolveBooleanValue(fakeNewFlagKey, !fakeValue)).isEqualTo(fakeValue)
        assertThat(testedClient.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
    }

    @Test
    fun `M preserve disk cache W build() {previous context switch failed}`(
        @StringForgery fakeOtherTargetingKey: String,
        forge: Forge
    ) {
        // Given
        installFlags()
        stubFailure(FetchFailure.NETWORK)
        testedClient.setEvaluationContext(EvaluationContext(fakeOtherTargetingKey))
        restartSdkCore(forge)

        // When
        buildClient()

        // Then
        assertThat(testedClient.resolveBooleanValue(fakeFlagKey, !fakeValue)).isEqualTo(fakeValue)
        assertThat(testedClient.resolve(fakeFlagKey, !fakeValue).reason).isEqualTo(ResolutionReason.CACHED)
    }

    private fun buildClient() {
        stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME, fakeDataStore)
        Flags.enable(
            FlagsConfiguration.Builder().useCustomFlagEndpoint(fakeEndpoint)
                .initializationTimeout(0).trackEvaluations(false).trackExposures(false).build(),
            stubSdkCore
        )
        testedClient = FlagsClient.Builder(sdkCore = stubSdkCore).build()
        testedClient.onFirstFlags { firstFlags.add(it.flagsChanged) }
    }

    private fun installFlags() {
        stubAssignments(mapOf(fakeFlagKey to fakeAssignment))
        testedClient.setEvaluationContext(fakeContext)
    }

    private fun restartWithPendingCache(forge: Forge) {
        installFlags()
        fakeDataStore.deferReads = true
        restartSdkCore(forge)
        buildClient()
    }

    private fun restartSdkCore(forge: Forge) {
        stubSdkCore.stopFeatures()
        stubSdkCore = createStubSdkCore(forge)
        firstFlags.clear()
    }

    private fun stubAssignments(assignments: Map<String, FakeAssignment>) {
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = precomputedAssignmentsBody(assignments)) }
    }

    private fun stubFailure(failure: FetchFailure) {
        stubSdkCore.stubNetworkResponses {
            when (failure) {
                FetchFailure.NETWORK -> null
                FetchFailure.MALFORMED_RESPONSE -> httpResponse(it, body = "{ invalid json }")
            }
        }
    }

    private fun assertDefaultAfterFailedSwitch() {
        assertThat(testedClient.resolveBooleanValue(fakeFlagKey, !fakeValue)).isEqualTo(!fakeValue)
        assertThat(testedClient.state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(firstFlags).containsExactly(listOf(fakeFlagKey))
    }

    internal enum class FetchFailure { NETWORK, MALFORMED_RESPONSE }
}
