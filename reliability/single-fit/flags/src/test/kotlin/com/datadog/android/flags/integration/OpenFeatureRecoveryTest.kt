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
import com.datadog.android.flags.integration.tests.utils.FakeAssignment
import com.datadog.android.flags.integration.tests.utils.httpResponse
import com.datadog.android.flags.integration.tests.utils.precomputedAssignmentsBody
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.openfeature.asOpenFeatureProvider
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.OpenFeatureStatus
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
internal class OpenFeatureRecoveryTest {
    private lateinit var stubSdkCore: StubSDKCore

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = createStubSdkCore(forge)
        stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        Flags.enable(
            FlagsConfiguration.Builder()
                .useCustomFlagEndpoint("https://${forge.anAlphabeticalString()}.com/assignments")
                .initializationTimeout(0)
                .build(),
            stubSdkCore
        )
    }

    @AfterEach
    fun `tear down`() = runBlocking {
        OpenFeatureAPI.shutdown()
        stubSdkCore.stopFeatures()
    }

    @Test
    fun `M recover OpenFeature status W setEvaluationContext() {network failure then valid assignments}`(
        @StringForgery fakeFlagKey: String,
        @StringForgery fakeTargetingKey: String,
        @StringForgery fakeNextTargetingKey: String,
        @StringForgery fakeVariationKey: String,
        @StringForgery fakeAllocationKey: String
    ) = runBlocking<Unit> {
        // Given
        val fakeAssignments = precomputedAssignmentsBody(
            mapOf(
                fakeFlagKey to FakeAssignment(
                    variationType = "boolean",
                    variationValue = true,
                    variationKey = fakeVariationKey,
                    allocationKey = fakeAllocationKey
                )
            )
        )
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = fakeAssignments) }
        val testedClient = FlagsClient.Builder(sdkCore = stubSdkCore).build()
        OpenFeatureAPI.setProviderAndWait(
            testedClient.asOpenFeatureProvider(),
            ImmutableContext(targetingKey = fakeTargetingKey),
            dispatcher = Dispatchers.Unconfined
        )
        stubSdkCore.stubNetworkResponses { null }

        // When
        testedClient.setEvaluationContext(EvaluationContext(fakeNextTargetingKey))
        val failedStatus = OpenFeatureAPI.getStatus()
        stubSdkCore.stubNetworkResponses { httpResponse(it, body = fakeAssignments) }
        testedClient.setEvaluationContext(EvaluationContext(fakeNextTargetingKey))
        val recoveredStatus = OpenFeatureAPI.getStatus()
        val recoveredValue = OpenFeatureAPI.getClient().getBooleanValue(fakeFlagKey, false)

        // Then
        assertThat(failedStatus).isInstanceOf(OpenFeatureStatus.Error::class.java)
        assertThat((failedStatus as OpenFeatureStatus.Error).error)
            .isInstanceOf(OpenFeatureError.GeneralError::class.java)
        assertThat(recoveredStatus).isEqualTo(OpenFeatureStatus.Ready)
        assertThat(recoveredValue).isTrue()
    }
}
