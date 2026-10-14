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
import com.datadog.android.flags.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.flags.integration.tests.utils.httpResponse
import com.datadog.android.flags.integration.tests.utils.precomputedAssignmentsBody
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.quality.Strictness

/**
 * Covers the flag evaluations reported to RUM, with both features running on the same SDK instance.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlagsRumIntegrationTest {

    private lateinit var stubSdkCore: StubSDKCore

    @StringForgery
    lateinit var fakeApplicationId: String

    @StringForgery
    lateinit var fakeFlagKey: String

    @StringForgery
    lateinit var fakeVariationKey: String

    @StringForgery
    lateinit var fakeTargetingKey: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = createStubSdkCore(forge)
        stubSdkCore.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        stubSdkCore.stubNetworkResponses { request ->
            httpResponse(
                request,
                body = precomputedAssignmentsBody(
                    mapOf(
                        fakeFlagKey to FakeAssignment(
                            variationType = "boolean",
                            variationValue = forge.aBool(),
                            variationKey = fakeVariationKey,
                            allocationKey = forge.anAlphabeticalString()
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `M add the flag to the view W resolveBooleanValue() {RUM integration enabled}`(
        @StringForgery fakeViewKey: String,
        @StringForgery fakeViewName: String,
        @BoolForgery fakeDefaultValue: Boolean
    ) {
        // Given
        val client = enableRumAndFlags(rumIntegrationEnabled = true)
        GlobalRumMonitor.get(stubSdkCore).startView(fakeViewKey, fakeViewName)

        // When
        client.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)
        GlobalRumMonitor.get(stubSdkCore).stopView(fakeViewKey)

        // Then
        val lastViewEvent = lastViewEvent()
        assertThat(lastViewEvent.getJSONObject("view").getString("name")).isEqualTo(fakeViewName)
        assertThat(lastViewEvent.getJSONObject("feature_flags").getString(fakeFlagKey))
            .isEqualTo(fakeVariationKey)
    }

    @Test
    fun `M not add the flag to the view W resolveBooleanValue() {RUM integration disabled}`(
        @StringForgery fakeViewKey: String,
        @StringForgery fakeViewName: String,
        @BoolForgery fakeDefaultValue: Boolean
    ) {
        // Given
        val client = enableRumAndFlags(rumIntegrationEnabled = false)
        GlobalRumMonitor.get(stubSdkCore).startView(fakeViewKey, fakeViewName)

        // When
        client.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)
        GlobalRumMonitor.get(stubSdkCore).stopView(fakeViewKey)

        // Then
        val featureFlags = lastViewEvent().optJSONObject("feature_flags")
        assertThat(featureFlags?.has(fakeFlagKey) ?: false).isFalse()
    }

    // region Internal

    private fun enableRumAndFlags(rumIntegrationEnabled: Boolean): FlagsClient {
        Rum.enable(
            RumConfiguration.Builder(fakeApplicationId)
                .trackNonFatalAnrs(false) // required to prevent infinite loop in tests
                .build(),
            stubSdkCore
        )
        Flags.enable(
            FlagsConfiguration.Builder()
                .useCustomFlagEndpoint("https://$fakeApplicationId.com/precompute-assignments")
                .rumIntegrationEnabled(rumIntegrationEnabled)
                .build(),
            stubSdkCore
        )
        return FlagsClient.Builder(sdkCore = stubSdkCore).build().apply {
            setEvaluationContext(EvaluationContext(fakeTargetingKey))
        }
    }

    private fun lastViewEvent(): JSONObject {
        return stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
            .map { JSONObject(it.eventData) }
            .last { it.getString("type") == "view" }
    }

    // endregion

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
