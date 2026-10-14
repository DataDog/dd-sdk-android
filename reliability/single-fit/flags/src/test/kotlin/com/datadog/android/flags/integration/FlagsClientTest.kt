/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import android.app.Application
import android.content.pm.ApplicationInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.FakeAssignment
import com.datadog.android.flags.integration.tests.utils.httpResponse
import com.datadog.android.flags.integration.tests.utils.precomputedAssignmentsBody
import com.datadog.android.flags.integration.tests.utils.targetingKey
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

/**
 * Covers the [FlagsClient] registry: how clients are created, retrieved and isolated between SDK instances.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlagsClientTest {

    private lateinit var stubSdkCore: StubSDKCore

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
    }

    @Test
    fun `M return a client resolving default values W Builder#build() {Flags not enabled}`(
        @BoolForgery fakeDefaultValue: Boolean
    ) {
        // When
        val client = FlagsClient.Builder(sdkCore = stubSdkCore).build()

        // Then
        assertThat(client.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)).isEqualTo(fakeDefaultValue)
        assertThat(client.resolve(fakeFlagKey, fakeDefaultValue).errorCode)
            .isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.NotReady)
    }

    @Test
    fun `M return a client resolving default values W get() {client never built}`(
        @BoolForgery fakeDefaultValue: Boolean
    ) {
        // Given
        Flags.enable(FlagsConfiguration.Builder().build(), stubSdkCore)

        // When
        val client = FlagsClient.get(sdkCore = stubSdkCore)

        // Then
        assertThat(client.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)).isEqualTo(fakeDefaultValue)
        assertThat(client.resolve(fakeFlagKey, fakeDefaultValue).errorCode)
            .isEqualTo(ErrorCode.PROVIDER_NOT_READY)
    }

    @Test
    fun `M return the built client W get() {default name}`() {
        // Given
        Flags.enable(FlagsConfiguration.Builder().build(), stubSdkCore)
        val builtClient = FlagsClient.Builder(sdkCore = stubSdkCore).build()

        // When
        val client = FlagsClient.get(sdkCore = stubSdkCore)

        // Then
        assertThat(client).isSameAs(builtClient)
    }

    @Test
    fun `M return the existing client W Builder#build() {same name twice}`(
        @StringForgery fakeClientName: String
    ) {
        // Given
        Flags.enable(FlagsConfiguration.Builder().build(), stubSdkCore)
        val firstClient = FlagsClient.Builder(fakeClientName, stubSdkCore).build()

        // When
        val secondClient = FlagsClient.Builder(fakeClientName, stubSdkCore).build()

        // Then
        assertThat(secondClient).isSameAs(firstClient)
    }

    @Test
    fun `M keep contexts separate W Builder#build() {two named clients}`(
        @StringForgery fakeOtherTargetingKey: String,
        forge: Forge
    ) {
        // Given
        Flags.enable(flagsConfiguration(forge), stubSdkCore)
        stubAssignmentsPerTargetingKey(
            mapOf(fakeTargetingKey to true, fakeOtherTargetingKey to false)
        )
        val firstClient = FlagsClient.Builder(forge.anAlphabeticalString(), stubSdkCore).build()
        val secondClient = FlagsClient.Builder(forge.anAlphabeticalString(), stubSdkCore).build()

        // When
        firstClient.setEvaluationContext(EvaluationContext(fakeTargetingKey))
        secondClient.setEvaluationContext(EvaluationContext(fakeOtherTargetingKey))

        // Then
        assertThat(firstClient.resolveBooleanValue(fakeFlagKey, false)).isTrue()
        assertThat(secondClient.resolveBooleanValue(fakeFlagKey, true)).isFalse()
    }

    @Test
    fun `M isolate clients W Builder#build() {two SDK instances}`(
        @StringForgery fakeOtherTargetingKey: String,
        forge: Forge
    ) {
        // Given
        val otherSdkCore = createStubSdkCore(forge)
        val configuration = flagsConfiguration(forge)
        Flags.enable(configuration, stubSdkCore)
        Flags.enable(configuration, otherSdkCore)
        stubAssignmentsPerTargetingKey(mapOf(fakeTargetingKey to true), stubSdkCore)
        stubAssignmentsPerTargetingKey(mapOf(fakeOtherTargetingKey to false), otherSdkCore)
        val client = FlagsClient.Builder(sdkCore = stubSdkCore).build()
        val otherClient = FlagsClient.Builder(sdkCore = otherSdkCore).build()

        // When
        client.setEvaluationContext(EvaluationContext(fakeTargetingKey))
        otherClient.setEvaluationContext(EvaluationContext(fakeOtherTargetingKey))

        // Then
        assertThat(otherClient).isNotSameAs(client)
        assertThat(FlagsClient.get(sdkCore = stubSdkCore)).isSameAs(client)
        assertThat(FlagsClient.get(sdkCore = otherSdkCore)).isSameAs(otherClient)
        assertThat(client.resolveBooleanValue(fakeFlagKey, false)).isTrue()
        assertThat(otherClient.resolveBooleanValue(fakeFlagKey, true)).isFalse()
    }

    @Test
    fun `M drop the registered clients W Flags feature stopped`() {
        // Given
        Flags.enable(FlagsConfiguration.Builder().build(), stubSdkCore)
        val builtClient = FlagsClient.Builder(sdkCore = stubSdkCore).build()

        // When
        stubSdkCore.stopFeatures()

        // Then
        assertThat(FlagsClient.get(sdkCore = stubSdkCore)).isNotSameAs(builtClient)
    }

    // region Internal

    private fun flagsConfiguration(forge: Forge): FlagsConfiguration {
        return FlagsConfiguration.Builder()
            .useCustomFlagEndpoint("https://${forge.anAlphabeticalString()}.com/precompute-assignments")
            .build()
    }

    private fun stubAssignmentsPerTargetingKey(
        valuesPerTargetingKey: Map<String, Boolean>,
        sdkCore: StubSDKCore = stubSdkCore
    ) {
        sdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        sdkCore.stubNetworkResponses { request ->
            valuesPerTargetingKey[request.targetingKey()]?.let {
                httpResponse(
                    request,
                    body = precomputedAssignmentsBody(
                        mapOf(
                            fakeFlagKey to FakeAssignment(
                                variationType = "boolean",
                                variationValue = it,
                                variationKey = fakeVariationKey,
                                allocationKey = fakeAllocationKey
                            )
                        )
                    )
                )
            }
        }
    }

    // endregion

    companion object {
        internal fun createStubSdkCore(forge: Forge): StubSDKCore {
            // FlagsFeature reads the debuggable flag from the ApplicationInfo; a mock reports a release build.
            val mockApplicationInfo = mock<ApplicationInfo>()
            val mockApplication = mock<Application> {
                on { applicationInfo } doReturn mockApplicationInfo
            }
            return StubSDKCore(
                forge = forge,
                mockContext = mockApplication
            )
        }
    }
}
