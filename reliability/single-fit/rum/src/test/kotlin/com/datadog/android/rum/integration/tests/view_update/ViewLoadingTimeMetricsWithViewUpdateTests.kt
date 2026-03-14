/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration.tests.view_update

import com.datadog.android.api.feature.Feature
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.integration.BaseViewLoadingTimeMetricsTests
import com.datadog.android.rum.integration.tests.assertj.hasRumEvent
import com.datadog.android.rum.integration.tests.assertj.hasRumViewUpdateEvent
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.interactiontonextview.PreviousViewLastInteractionContext
import com.datadog.android.rum.metric.interactiontonextview.TimeBasedInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.metric.networksettled.NetworkSettledResourceContext
import com.datadog.android.rum.metric.networksettled.TimeBasedInitialResourceIdentifier
import com.datadog.android.rum.model.ViewUpdateEvent
import com.datadog.android.tests.assertj.StubEventsAssert.Companion.assertThat
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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
class ViewLoadingTimeMetricsWithViewUpdateTests : BaseViewLoadingTimeMetricsTests() {

    // region Time to network settle

    @RepeatedTest(10)
    fun `M provide the TTNS metric for each view W initial resource was stopped and sent`() {
        // Given
        val fakeRumConfiguration = configurationBuilder()
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        monitor.startView(viewKey, viewName)
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind)
        monitor.stopView(viewKey)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionCount(0)
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasResourceUrl(resourceUrl)
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
                    hasNetworkSettledTimeNotNull()
                    resource { hasCount(1) }
                    hasNoAction()
                    hasNoError()
                    hasLoadingTime(null)
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
            .hasRumViewUpdateEvent(index = 3) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    @RepeatedTest(10)
    fun `M provide consistent TTNS metric for each view event W view updated multiple times`(
        forge: Forge
    ) {
        // Given
        val fakeRumConfiguration = configurationBuilder()
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        monitor.startView(viewKey, viewName)
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind)
        monitor.addTiming(forge.anAlphabeticalString())
        stubSdkCore.advanceTimeBy(100)
        monitor.addTiming(forge.anAlphabeticalString())
        monitor.stopView(viewKey)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionCount(0)
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasResourceUrl(resourceUrl)
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
                    hasNetworkSettledTimeNotNull()
                    resource { hasCount(1) }
                    hasNoAction()
                    hasNoError()
                    hasLoadingTime(null)
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
            .hasRumViewUpdateEvent(index = 3) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
                    hasNoAction()
                    hasNoError()
                    hasNoResource()
                    hasLoadingTime(null)
                    hasNetworkSettledTime(null)
                    hasNoCrash()
                    hasNoLongTask()
                    hasNoFrozenFrame()
                    hasNoFrustration()
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
            .hasRumViewUpdateEvent(index = 4) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
                    hasNoAction()
                    hasNoError()
                    hasNoResource()
                    hasLoadingTime(null)
                    hasNetworkSettledTime(null)
                    hasNoCrash()
                    hasNoLongTask()
                    hasNoFrozenFrame()
                    hasNoFrustration()
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
            .hasRumViewUpdateEvent(index = 5) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    @RepeatedTest(10)
    fun `M provide the TTNS metric for each view event W initial resource stopped with error`(
        @StringForgery errorMessage: String,
        @Forgery errorSource: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // Given
        val fakeRumConfiguration = configurationBuilder()
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        monitor.startView(viewKey, viewName)
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopResourceWithError(resourceKey, resourceStatus, errorMessage, errorSource, throwable)
        monitor.stopView(viewKey)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionCount(0)
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("error")
                hasViewUrl(viewKey)
                hasViewName(viewName)
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
                    hasNetworkSettledTimeNotNull()
                    error { hasCount(1) }
                    hasNoAction()
                    hasNoResource()
                    hasLoadingTime(null)
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
            .hasRumViewUpdateEvent(index = 3) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    @RepeatedTest(10)
    fun `M not provide the TTNS metric for each view event W resource stopped with error, error dropped by mapper`(
        @StringForgery errorMessage: String,
        @Forgery errorSource: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // Given
        val fakeRumConfiguration = configurationBuilder()
            .setErrorEventMapper { null }
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        monitor.startView(viewKey, viewName)
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopResourceWithError(resourceKey, resourceStatus, errorMessage, errorSource, throwable)
        monitor.stopView(viewKey)

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
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionCount(0)
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumViewUpdateEvent(index = 1) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    @RepeatedTest(10)
    fun `M not provide the TTNS metric W initial resource dropped through mapper`() {
        // Given
        val fakeRumConfiguration = configurationBuilder()
            .setResourceEventMapper({ null })
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        monitor.startView(viewKey, viewName)
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind)
        monitor.stopView(viewKey)

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
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionCount(0)
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumViewUpdateEvent(index = 1) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    @RepeatedTest(10)
    fun `M not provide TTNS metric W default identifier, resource dropped`() {
        // Given
        val fakeRumConfiguration = configurationBuilder()
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        monitor.startView(viewKey, viewName)
        // wait for more than the default threshold in the default identifier (100ms)
        stubSdkCore.advanceTimeBy(110)
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind)
        monitor.stopView(viewKey)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionCount(0)
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasResourceUrl(resourceUrl.toString())
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
                    resource { hasCount(1) }
                    hasNoAction()
                    hasNoError()
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
            .hasRumViewUpdateEvent(index = 3) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    @RepeatedTest(10)
    fun `M not provide TTNS metric W default identifier, custom threshold, resource dropped`(
        @LongForgery(min = 100, max = 400) resourceIdentifierThresholdMs: Long
    ) {
        // Given
        val fakeRumConfiguration = configurationBuilder()
            .setInitialResourceIdentifier(TimeBasedInitialResourceIdentifier(resourceIdentifierThresholdMs))
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        monitor.startView(viewKey, viewName)
        // wait for more than the custom threshold
        stubSdkCore.advanceTimeBy(resourceIdentifierThresholdMs + 10)
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind)
        monitor.stopView(viewKey)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionCount(0)
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasResourceUrl(resourceUrl)
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
                    resource { hasCount(1) }
                    hasNoAction()
                    hasNoError()
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
            .hasRumViewUpdateEvent(index = 3) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    @RepeatedTest(10)
    fun `M not provide TNS metric W custom identifier, not valid resource`() {
        // Given
        val fakeRumConfiguration = configurationBuilder()
            .setInitialResourceIdentifier(object : InitialResourceIdentifier {
                override fun validate(context: NetworkSettledResourceContext): Boolean {
                    return false
                }
            })
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        monitor.startView(viewKey, viewName)
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind)
        monitor.stopView(viewKey)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionCount(0)
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasResourceUrl(resourceUrl)
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
                    resource { hasCount(1) }
                    hasNoAction()
                    hasNoError()
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
            .hasRumViewUpdateEvent(index = 3) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    // endregion

    // region Interaction to next view

    @ParameterizedTest
    @MethodSource("getValidLastInteractionTypes")
    fun `M provide the ITNV metric for current view W last action on previous view was sent { valid type }`(
        validActionType: RumActionType
    ) {
        // Given
        val fakeRumConfiguration = configurationBuilder().build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        val appExpectedItnvTime = runSuccessfulItnvTestScenario(monitor, validActionType)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasActionName(lastInteractionName)
                hasViewName(previousViewName)
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
                    action { hasCount(1) }
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
            .hasRumViewUpdateEvent(index = 3) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
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
            .hasRumEvent(index = 4) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasInteractionToNextViewTime(appExpectedItnvTime, ITNV_METRIC_OFFSET_IN_NANOSECONDS)
            }
            .hasRumViewUpdateEvent(index = 5) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    @ParameterizedTest
    @MethodSource("getNonValidLastInteractionTypes")
    fun `M not provide the ITNV metric for current view W last action on previous view was sent { invalid type }`(
        invalidActionType: RumActionType
    ) {
        // Given
        val fakeRumConfiguration = configurationBuilder().build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        runSuccessfulItnvTestScenario(monitor, invalidActionType)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasActionName(lastInteractionName)
                hasViewName(previousViewName)
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
                    action { hasCount(1) }
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
            .hasRumViewUpdateEvent(index = 3) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
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
            .hasRumEvent(index = 4) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                doesNotHaveInteractionToNextViewTime()
            }
            .hasRumViewUpdateEvent(index = 5) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    @RepeatedTest(10)
    fun `M not provide the ITNV metric for current view W last action on previous view was dropped`(
        forge: Forge
    ) {
        // Given
        val validActionType = forge.aValidLastInteractionActionType()
        val fakeRumConfiguration = configurationBuilder()
            .setActionEventMapper { null }
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        runSuccessfulItnvTestScenario(monitor, validActionType)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumViewUpdateEvent(index = 1) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
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
            .hasRumEvent(index = 2) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                doesNotHaveInteractionToNextViewTime()
            }
            .hasRumViewUpdateEvent(index = 3) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    @RepeatedTest(10)
    fun `M provide the ITNV metric for current view W using custom identifier, action validated`(
        forge: Forge
    ) {
        // Given
        val validActionType = forge.aValidLastInteractionActionType()
        val fakeRumConfiguration = configurationBuilder()
            .setLastInteractionIdentifier(object : LastInteractionIdentifier {
                override fun validate(context: PreviousViewLastInteractionContext): Boolean {
                    return true
                }
            })
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        val appExpectedItnvTime = runSuccessfulItnvTestScenario(monitor, validActionType)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasActionName(lastInteractionName)
                hasViewName(previousViewName)
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
                    action { hasCount(1) }
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
            .hasRumViewUpdateEvent(index = 3) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
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
            .hasRumEvent(index = 4) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasInteractionToNextViewTime(appExpectedItnvTime, ITNV_METRIC_OFFSET_IN_NANOSECONDS)
            }
            .hasRumViewUpdateEvent(index = 5) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    @RepeatedTest(10)
    fun `M not provide the ITNV metric for current view W using custom identifier, action not validated`(
        forge: Forge
    ) {
        // Given
        val validActionType = forge.aValidLastInteractionActionType()
        val fakeRumConfiguration = configurationBuilder()
            .setLastInteractionIdentifier(object : LastInteractionIdentifier {
                override fun validate(context: PreviousViewLastInteractionContext): Boolean {
                    return false
                }
            })
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        runUnsuccessfulItnvTestScenario(monitor, validActionType)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasActionName(lastInteractionName)
                hasViewName(previousViewName)
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
                    action { hasCount(1) }
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
            .hasRumViewUpdateEvent(index = 3) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
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
            .hasRumEvent(index = 4) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                doesNotHaveInteractionToNextViewTime()
            }
            .hasRumViewUpdateEvent(index = 5) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    @RepeatedTest(10)
    fun `M provide the ITNV metric for current view W using time based identifier, threshold respected`(
        forge: Forge
    ) {
        // Given
        val validActionType = forge.aValidLastInteractionActionType()
        val fakeRumConfiguration = configurationBuilder()
            .setLastInteractionIdentifier(TimeBasedInteractionIdentifier())
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)
        val appExpectedItnvTime = runSuccessfulItnvTestScenario(monitor, validActionType)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasActionName(lastInteractionName)
                hasViewName(previousViewName)
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
                    action { hasCount(1) }
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
            .hasRumViewUpdateEvent(index = 3) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
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
            .hasRumEvent(index = 4) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasInteractionToNextViewTime(appExpectedItnvTime, ITNV_METRIC_OFFSET_IN_NANOSECONDS)
            }
            .hasRumViewUpdateEvent(index = 5) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    @RepeatedTest(2)
    fun `M not provide the ITNV metric for current view W using time based identifier, threshold surpassed`(
        forge: Forge
    ) {
        // Given
        val validActionType = forge.aValidLastInteractionActionType()
        val fakeRumConfiguration = configurationBuilder()
            .setLastInteractionIdentifier(TimeBasedInteractionIdentifier())
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        monitor.startView(previousViewKey, previousViewName)
        monitor.startAction(validActionType, lastInteractionName)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopAction(validActionType, lastInteractionName)
        monitor.stopView(previousViewKey)
        // Wait for more than the default threshold in the default identifier (3000ms)
        stubSdkCore.advanceTimeBy(3010)
        monitor.startView(viewKey, viewName)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopView(viewKey)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasActionName(lastInteractionName)
                hasViewName(previousViewName)
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
                    action { hasCount(1) }
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
            .hasRumViewUpdateEvent(index = 3) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
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
            .hasRumEvent(index = 4) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                doesNotHaveInteractionToNextViewTime()
            }
            .hasRumViewUpdateEvent(index = 5) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    @RepeatedTest(2)
    fun `M not provide the ITNV metric for current view W using time based identifier {custom threshold, failed}`(
        forge: Forge
    ) {
        // Given
        val customThreshold = forge.aLong(min = 300, max = 4000)
        val validActionType = forge.aValidLastInteractionActionType()
        val fakeRumConfiguration = configurationBuilder()
            .setLastInteractionIdentifier(TimeBasedInteractionIdentifier(customThreshold))
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        monitor.startView(previousViewKey, previousViewName)
        monitor.startAction(validActionType, lastInteractionName)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopAction(validActionType, lastInteractionName)
        monitor.stopView(previousViewKey)
        // Wait for more than the custom threshold
        stubSdkCore.advanceTimeBy(customThreshold + 10)
        monitor.startView(viewKey, viewName)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopView(viewKey)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasActionName(lastInteractionName)
                hasViewName(previousViewName)
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
                    action { hasCount(1) }
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
            .hasRumViewUpdateEvent(index = 3) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(previousViewKey)
                    hasTimeSpentNotNull()
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
            .hasRumEvent(index = 4) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                doesNotHaveInteractionToNextViewTime()
            }
            .hasRumViewUpdateEvent(index = 5) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    hasTimeSpentNotNull()
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

    // endregion

    // endregion

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }

        @JvmStatic
        fun getValidLastInteractionTypes(): List<RumActionType> {
            return listOf(
                RumActionType.TAP,
                RumActionType.SWIPE,
                RumActionType.CLICK,
                RumActionType.BACK
            )
        }

        @JvmStatic
        fun getNonValidLastInteractionTypes(): List<RumActionType> {
            return listOf(
                RumActionType.SCROLL,
                RumActionType.CUSTOM
            )
        }
    }
}
