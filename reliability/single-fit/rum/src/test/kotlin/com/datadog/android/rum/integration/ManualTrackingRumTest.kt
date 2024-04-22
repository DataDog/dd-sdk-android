/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.integration.tests.assertj.hasRumEvent
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.tests.assertj.StubEventsAssert
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.net.URL

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualTrackingRumTest {

    private lateinit var stubSdkCore: StubSDKCore

    @StringForgery
    private lateinit var fakeApplicationId: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)

        val fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
    }

    // region Feature Init

    @RepeatedTest(16)
    fun `M create a GlobalRumMonitor W Rum#enable()`() {
        // Given

        // When
        val result = GlobalRumMonitor.get(stubSdkCore)

        // Then
        // We use reflection because that class is marked internal
        val classDatadogRum = Class.forName("com.datadog.android.rum.internal.monitor.DatadogRumMonitor")
        assertThat(result).isInstanceOf(classDatadogRum)
    }

    // endregion

    // region Basic Scenarios

    @RepeatedTest(16)
    fun `M send view event W startView()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // When
        GlobalRumMonitor.get(stubSdkCore).startView(viewKey, viewName, emptyMap())

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        StubEventsAssert.assertThat(eventsWritten)
            .hasSize(1)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
            }
    }

    @RepeatedTest(16)
    fun `M send view event W startView() + stopView()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(viewKey, viewName, emptyMap())
        rumMonitor.stopView(viewKey, emptyMap())

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        StubEventsAssert.assertThat(eventsWritten)
            .hasSize(2)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasViewIsActive(true)
                hasDocumentVersion(1)
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasViewIsActive(false)
                hasDocumentVersion(2)
            }
    }

    @RepeatedTest(16)
    fun `M send view event with feature flag W startView() + addFeatureFlagEvaluation()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery ffKey: String,
        @StringForgery ffValue: String
    ) {
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(viewKey, viewName, emptyMap())
        rumMonitor.addFeatureFlagEvaluation(ffKey, ffValue)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        print(eventsWritten[1].eventData)
        StubEventsAssert.assertThat(eventsWritten)
            .hasSize(2)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                doesNotHaveField("feature_flag")
                hasDocumentVersion(1)
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasFeatureFlag(ffKey, ffValue)
                hasDocumentVersion(2)
            }
    }

    @RepeatedTest(16)
    fun `M send view event with action W startView() + addAction()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery actionName: String
    ) {
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(viewKey, viewName, emptyMap())
        rumMonitor.addAction(RumActionType.CUSTOM, actionName, emptyMap())
        Thread.sleep(100)
        // Used to trigger the action event
        rumMonitor.stopView(viewKey, emptyMap())

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        StubEventsAssert.assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                // Initial view
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionCount(0)
                hasDocumentVersion(1)
            }
            .hasRumEvent(index = 1) {
                // Custom event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionName(actionName)
            }
            .hasRumEvent(index = 2) {
                // View updated with event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionCount(1)
                doesNotHaveField("feature_flag")
                hasDocumentVersion(2)
            }
            .hasRumEvent(index = 3) {
                // View updated with FF
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasActionCount(1)
                hasViewName(viewName)
                hasDocumentVersion(3)
            }
    }

    @RepeatedTest(16)
    fun `M send view event with error W startView() + addError()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery errorMessage: String,
        @Forgery errorSource: RumErrorSource,
        @Forgery exception: Exception
    ) {
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(viewKey, viewName, emptyMap())
        rumMonitor.addError(errorMessage, errorSource, exception, emptyMap())
        rumMonitor.stopView(viewKey, emptyMap())

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        StubEventsAssert.assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                // Initial view
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionCount(0)
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                // Custom event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("error")
                hasViewUrl(viewKey)
                hasViewName(viewName)
            }
            .hasRumEvent(index = 2) {
                // View updated with event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasErrorCount(1)
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 3) {
                // View updated with FF
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
            }
    }

    @RepeatedTest(16)
    fun `M send view event with resource W startView() + startResource() + stopResource()`(
        @StringForgery key: String,
        @StringForgery name: String,
        @StringForgery resourceKey: String,
        @Forgery resourceUrl: URL,
        @IntForgery(200, 599) resourceStatus: Int,
        @LongForgery(0) resourceSize: Long
    ) {
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(key, name, emptyMap())
        rumMonitor.startResource(resourceKey, RumResourceMethod.GET, resourceUrl.toString())
        Thread.sleep(100)
        rumMonitor.stopResource(resourceKey, resourceStatus, resourceSize, RumResourceKind.NATIVE, emptyMap())
        rumMonitor.stopView(key, emptyMap())

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        StubEventsAssert.assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                // Initial view
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasActionCount(0)
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                // Custom event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewUrl(key)
                hasViewName(name)
            }
            .hasRumEvent(index = 2) {
                // View updated with event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasResourceCount(1)
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 3) {
                // View updated with FF
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
            }
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
