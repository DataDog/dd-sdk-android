/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration.tests.view_update

import com.datadog.android.api.feature.Feature
import com.datadog.android.rum.integration.BaseActivityViewTrackingStrategyTest
import com.datadog.android.rum.integration.tests.assertj.hasRumEvent
import com.datadog.android.rum.integration.tests.assertj.hasRumViewUpdateEvent
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.rum.model.ViewUpdateEvent
import com.datadog.android.tests.assertj.StubEventsAssert.Companion.assertThat
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityViewTrackingStrategyWithViewUpdateTest : BaseActivityViewTrackingStrategyTest() {

    // region Activity Lifecycle

    @RepeatedTest(4)
    fun `M send RUM View W onActivityResumed()`() {
        // When
        runOnActivityResumed()

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(1)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl("com/datadog/android/rum/integration/BaseActivityViewTrackingStrategyTest/StubActivity")
                hasViewName("com.datadog.android.rum.integration.BaseActivityViewTrackingStrategyTest.StubActivity")
                hasDocumentVersion(2)
            }
    }

    @RepeatedTest(4)
    fun `M send RUM View update W onActivityResumed() + onActivityStopped()`() {
        // When
        runOnActivityResumedAndStopped()

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(2)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl("com/datadog/android/rum/integration/BaseActivityViewTrackingStrategyTest/StubActivity")
                hasViewName("com.datadog.android.rum.integration.BaseActivityViewTrackingStrategyTest.StubActivity")
                hasViewIsActive(true)
                hasDocumentVersion(2)
            }
            .hasRumViewUpdateEvent(index = 1) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                dd { hasDocumentVersion(3) }
                view {
                    hasUrl("com/datadog/android/rum/integration/BaseActivityViewTrackingStrategyTest/StubActivity")
                    hasIsActive(false)
                    hasNoAction()
                    hasNoError()
                    hasNoResource()
                    hasLoadingTime(null)
                    hasNetworkSettledTime(null)
                    hasNoCrash()
                    hasNoLongTask()
                    hasNoFrozenFrame()
                    hasNoFrustration()
                    hasNoCustomTimings()
                    hasNoFlutterBuildTime()
                    hasNoFlutterRasterTime()
                    hasNoJsRefreshRate()
                    hasNoPerformance()
                    hasNoAccessibility()
                }
                hasNoFeatureFlags()
                hasNoContainer()
                hasNoPrivacy()
                hasNoDisplay()
                hasNoUsr()
                hasNoAccount()
                hasNoConnectivity()
                hasNoSynthetics()
                hasNoCiTest()
                hasNoOs()
                hasNoDevice()
                hasNoContext()
            }
    }

    @RepeatedTest(4)
    fun `M not send RUM View update W onActivityStopped() {start not tracked}`() {
        // When
        runOnActivityStopped()

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(0)
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
