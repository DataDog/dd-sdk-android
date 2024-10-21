/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import android.app.Activity
import android.app.Application
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.integration.tests.assertj.hasRumEvent
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.tests.assertj.StubEventsAssert.Companion.assertThat
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityViewTrackingStrategyTest {

    private lateinit var stubSdkCore: StubSDKCore

    private lateinit var testedViewTrackingStrategy: ActivityViewTrackingStrategy

    @Mock
    lateinit var stubActivity: StubActivity

    @Mock
    lateinit var mockApplicationContext: Application

    @StringForgery
    private lateinit var fakeApplicationId: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)

        val fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false) // required to prevent infinite loop in tests
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)

        testedViewTrackingStrategy = ActivityViewTrackingStrategy(true)
        testedViewTrackingStrategy.register(stubSdkCore, mockApplicationContext)
        verify(mockApplicationContext).registerActivityLifecycleCallbacks(testedViewTrackingStrategy)
    }

    @AfterEach
    fun `tear down`() {
        testedViewTrackingStrategy.unregister(mockApplicationContext)
        verify(mockApplicationContext).unregisterActivityLifecycleCallbacks(testedViewTrackingStrategy)
    }

    // region Activity Lifecycle

    @RepeatedTest(4)
    fun `M send RUM View W onActivityResumed()`() {
        // Given

        // When
        testedViewTrackingStrategy.onActivityResumed(stubActivity)

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
                hasViewUrl("com/datadog/android/rum/integration/ActivityViewTrackingStrategyTest/StubActivity")
                hasViewName("com.datadog.android.rum.integration.ActivityViewTrackingStrategyTest.StubActivity")
            }
    }

    @RepeatedTest(4)
    fun `M send RUM View update W onActivityResumed() + onActivityStopped()`() {
        // Given

        // When
        testedViewTrackingStrategy.onActivityResumed(stubActivity)
        testedViewTrackingStrategy.onActivityStopped(stubActivity)
        Thread.sleep(250L)

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
                hasViewUrl("com/datadog/android/rum/integration/ActivityViewTrackingStrategyTest/StubActivity")
                hasViewName("com.datadog.android.rum.integration.ActivityViewTrackingStrategyTest.StubActivity")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl("com/datadog/android/rum/integration/ActivityViewTrackingStrategyTest/StubActivity")
                hasViewName("com.datadog.android.rum.integration.ActivityViewTrackingStrategyTest.StubActivity")
                hasViewIsActive(false)
            }
    }

    @RepeatedTest(4)
    fun `M not send RUM View update W onActivityStopped() {start not tracked}`() {
        // Given

        // When
        testedViewTrackingStrategy.onActivityStopped(stubActivity)
        Thread.sleep(250L)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(0)
    }

    // endregion

    class StubActivity : Activity()

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
