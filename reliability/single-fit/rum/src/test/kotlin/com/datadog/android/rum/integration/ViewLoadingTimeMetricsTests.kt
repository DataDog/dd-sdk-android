/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum._RumInternalProxy
import com.datadog.android.rum.configuration.RumViewEventWriteConfig
import com.datadog.android.rum.integration.tests.assertj.hasRumEvent
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.interactiontonextview.PreviousViewLastInteractionContext
import com.datadog.android.rum.metric.interactiontonextview.TimeBasedInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.metric.networksettled.NetworkSettledResourceContext
import com.datadog.android.rum.metric.networksettled.TimeBasedInitialResourceIdentifier
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
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ViewLoadingTimeMetricsTests : BaseViewLoadingTimeMetricsTests() {

    override fun configureRumBuilder(builder: RumConfiguration.Builder) {
        _RumInternalProxy.setRumViewEventWriteConfig(
            builder = builder,
            config = RumViewEventWriteConfig.AlwaysFullView
        )
    }

    // region Time to network settle

    @RepeatedTest(10)
    fun `M provide the TTNS metric for each view W initial resource was stopped and sent`() {
        // When
        val monitor = enableRum()
        runTtnsResourceScenario(monitor)
        val appExpectedTtnsTime = TimeUnit.MILLISECONDS.toNanos(100)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
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
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                // Custom event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasResourceUrl(resourceUrl)
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
                hasNetworkSettledTime(appExpectedTtnsTime, TTNS_METRIC_OFFSET_IN_NANOSECONDS)
                hasResourceCount(1)
            }
            .hasRumEvent(index = 3) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasNetworkSettledTime(appExpectedTtnsTime, TTNS_METRIC_OFFSET_IN_NANOSECONDS)
                hasViewName(viewName)
            }
    }

    @RepeatedTest(10)
    fun `M provide consistent TTNS metric for each view event W view updated multiple times`(
        forge: Forge
    ) {
        // When
        val monitor = enableRum()
        runTtnsResourceWithTimingsScenario(monitor, forge)
        val appExpectedTtnsTime = TimeUnit.MILLISECONDS.toNanos(100)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
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
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                // Custom event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasResourceUrl(resourceUrl)
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
                hasNetworkSettledTime(appExpectedTtnsTime, TTNS_METRIC_OFFSET_IN_NANOSECONDS)
                hasResourceCount(1)
            }
            .hasRumEvent(index = 3) {
                // View updated with event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasNetworkSettledTime(appExpectedTtnsTime, TTNS_METRIC_OFFSET_IN_NANOSECONDS)
                hasResourceCount(1)
            }
            .hasRumEvent(index = 4) {
                // View updated with event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasNetworkSettledTime(appExpectedTtnsTime, TTNS_METRIC_OFFSET_IN_NANOSECONDS)
                hasResourceCount(1)
            }
            .hasRumEvent(index = 5) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasNetworkSettledTime(appExpectedTtnsTime, TTNS_METRIC_OFFSET_IN_NANOSECONDS)
                hasViewName(viewName)
            }
    }

    @RepeatedTest(10)
    fun `M provide the TTNS metric for each view event W initial resource stopped with error`(
        @StringForgery errorMessage: String,
        @Forgery errorSource: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // When
        val monitor = enableRum()
        runTtnsResourceErrorScenario(monitor, errorMessage, errorSource, throwable)
        val appExpectedTtnsTime = TimeUnit.MILLISECONDS.toNanos(100)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
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
                doesNotHaveNetworkSettledTime()
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
                hasNetworkSettledTime(appExpectedTtnsTime, TTNS_METRIC_OFFSET_IN_NANOSECONDS)
                hasResourceCount(0)
            }
            .hasRumEvent(index = 3) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasErrorCount(1)
                hasNetworkSettledTime(appExpectedTtnsTime, TTNS_METRIC_OFFSET_IN_NANOSECONDS)
                hasViewName(viewName)
            }
    }

    @RepeatedTest(10)
    fun `M not provide the TTNS metric for each view event W resource stopped with error, error dropped by mapper`(
        @StringForgery errorMessage: String,
        @Forgery errorSource: RumErrorSource,
        @Forgery throwable: Throwable
    ) {
        // When
        val monitor = enableRum { setErrorEventMapper { null } }
        runTtnsResourceErrorScenario(monitor, errorMessage, errorSource, throwable)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(2)
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
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasErrorCount(0)
                doesNotHaveNetworkSettledTime()
                hasViewName(viewName)
            }
    }

    @RepeatedTest(10)
    fun `M not provide the TTNS metric W initial resource dropped through mapper`() {
        // When
        val monitor = enableRum { setResourceEventMapper { null } }
        runTtnsResourceScenario(monitor)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(2)
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
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                doesNotHaveNetworkSettledTime()
                hasViewName(viewName)
                hasResourceCount(0)
            }
    }

    @RepeatedTest(10)
    fun `M not provide TTNS metric W default identifier, resource dropped`() {
        // When
        val monitor = enableRum()
        runTtnsDelayedResourceScenario(monitor, 110)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
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
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                // Custom event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasResourceUrl(resourceUrl.toString())
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
                doesNotHaveNetworkSettledTime()
                hasResourceCount(1)
            }
            .hasRumEvent(index = 3) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                doesNotHaveNetworkSettledTime()
                hasViewName(viewName)
            }
    }

    @RepeatedTest(10)
    fun `M not provide TTNS metric W default identifier, custom threshold, resource dropped`(
        @LongForgery(min = 100, max = 400) resourceIdentifierThresholdMs: Long
    ) {
        // When
        val monitor = enableRum {
            setInitialResourceIdentifier(TimeBasedInitialResourceIdentifier(resourceIdentifierThresholdMs))
        }
        runTtnsDelayedResourceScenario(monitor, resourceIdentifierThresholdMs + 10)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
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
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                // Custom event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasResourceUrl(resourceUrl)
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
                doesNotHaveNetworkSettledTime()
                hasResourceCount(1)
            }
            .hasRumEvent(index = 3) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                doesNotHaveNetworkSettledTime()
                hasViewName(viewName)
            }
    }

    @RepeatedTest(10)
    fun `M not provide TNS metric W custom identifier, not valid resource`() {
        // When
        val monitor = enableRum {
            setInitialResourceIdentifier(object : InitialResourceIdentifier {
                override fun validate(context: NetworkSettledResourceContext): Boolean {
                    return false
                }
            })
        }
        runTtnsResourceScenario(monitor)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
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
                doesNotHaveNetworkSettledTime()
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                // Custom event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasResourceUrl(resourceUrl)
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
                doesNotHaveNetworkSettledTime()
                hasResourceCount(1)
            }
            .hasRumEvent(index = 3) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                doesNotHaveNetworkSettledTime()
                hasViewName(viewName)
            }
    }

    // endregion

    // region Interaction to next view

    @ParameterizedTest
    @MethodSource("getValidLastInteractionTypes")
    fun `M provide the ITNV metric for current view W last action on previous view was sent { valid type }`(
        validActionType: RumActionType
    ) {
        // When
        val monitor = enableRum()
        val appExpectedItnvTime = runSuccessfulItnvTestScenario(monitor, validActionType)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                // Initial previous view
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
                // Action event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasActionName(lastInteractionName)
                hasViewName(previousViewName)
            }
            .hasRumEvent(index = 2) {
                // View updated with event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                hasActionCount(1)
            }
            .hasRumEvent(index = 3) {
                // Previous view stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                doesNotHaveInteractionToNextViewTime()
                hasViewName(previousViewName)
                hasActionCount(1)
            }
            .hasRumEvent(index = 4) {
                // New View Event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasInteractionToNextViewTime(appExpectedItnvTime, ITNV_METRIC_OFFSET_IN_NANOSECONDS)
            }
            .hasRumEvent(index = 5) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasInteractionToNextViewTime(appExpectedItnvTime, ITNV_METRIC_OFFSET_IN_NANOSECONDS)
                hasViewName(viewName)
            }
    }

    @ParameterizedTest
    @MethodSource("getNonValidLastInteractionTypes")
    fun `M not provide the ITNV metric for current view W last action on previous view was sent { invalid type }`(
        invalidActionType: RumActionType
    ) {
        // When
        val monitor = enableRum()
        runSuccessfulItnvTestScenario(monitor, invalidActionType)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                // Initial previous view
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
                // Action event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasActionName(lastInteractionName)
                hasViewName(previousViewName)
            }
            .hasRumEvent(index = 2) {
                // View updated with event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                hasActionCount(1)
            }
            .hasRumEvent(index = 3) {
                // Previous view stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                doesNotHaveInteractionToNextViewTime()
                hasActionCount(1)
                hasViewName(previousViewName)
            }
            .hasRumEvent(index = 4) {
                // New View Event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                doesNotHaveInteractionToNextViewTime()
            }
            .hasRumEvent(index = 5) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                doesNotHaveInteractionToNextViewTime()
                hasViewName(viewName)
            }
    }

    @RepeatedTest(10)
    fun `M not provide the ITNV metric for current view W last action on previous view was dropped`(
        forge: Forge
    ) {
        // When
        val validActionType = forge.aValidLastInteractionActionType()
        val monitor = enableRum { setActionEventMapper { null } }
        runSuccessfulItnvTestScenario(monitor, validActionType)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                // Initial previous view
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
                // Previous view stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                doesNotHaveInteractionToNextViewTime()
                hasActionCount(0)
                hasViewName(previousViewName)
            }
            .hasRumEvent(index = 2) {
                // New View Event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                doesNotHaveInteractionToNextViewTime()
            }
            .hasRumEvent(index = 3) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                doesNotHaveInteractionToNextViewTime()
                hasViewName(viewName)
            }
    }

    @RepeatedTest(10)
    fun `M provide the ITNV metric for current view W using custom identifier, action validated`(
        forge: Forge
    ) {
        // When
        val validActionType = forge.aValidLastInteractionActionType()
        val monitor = enableRum {
            setLastInteractionIdentifier(object : LastInteractionIdentifier {
                override fun validate(context: PreviousViewLastInteractionContext): Boolean {
                    return true
                }
            })
        }
        val appExpectedItnvTime = runSuccessfulItnvTestScenario(monitor, validActionType)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                // Initial previous view
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
                // Action event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasActionName(lastInteractionName)
                hasViewName(previousViewName)
            }
            .hasRumEvent(index = 2) {
                // View updated with event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                hasActionCount(1)
            }
            .hasRumEvent(index = 3) {
                // Previous view stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                doesNotHaveInteractionToNextViewTime()
                hasActionCount(1)
                hasViewName(previousViewName)
            }
            .hasRumEvent(index = 4) {
                // New View Event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasInteractionToNextViewTime(appExpectedItnvTime, ITNV_METRIC_OFFSET_IN_NANOSECONDS)
            }
            .hasRumEvent(index = 5) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasInteractionToNextViewTime(appExpectedItnvTime, ITNV_METRIC_OFFSET_IN_NANOSECONDS)
                hasViewName(viewName)
            }
    }

    @RepeatedTest(10)
    fun `M not provide the ITNV metric for current view W using custom identifier, action not validated`(
        forge: Forge
    ) {
        // When
        val validActionType = forge.aValidLastInteractionActionType()
        val monitor = enableRum {
            setLastInteractionIdentifier(object : LastInteractionIdentifier {
                override fun validate(context: PreviousViewLastInteractionContext): Boolean {
                    return false
                }
            })
        }
        runUnsuccessfulItnvTestScenario(monitor, validActionType)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                // Initial previous view
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
                // Action event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasActionName(lastInteractionName)
                hasViewName(previousViewName)
            }
            .hasRumEvent(index = 2) {
                // View updated with event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                hasActionCount(1)
            }
            .hasRumEvent(index = 3) {
                // Previous view stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                doesNotHaveInteractionToNextViewTime()
                hasActionCount(1)
                hasViewName(previousViewName)
            }
            .hasRumEvent(index = 4) {
                // New View Event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                doesNotHaveInteractionToNextViewTime()
            }
            .hasRumEvent(index = 5) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                doesNotHaveInteractionToNextViewTime()
                hasViewName(viewName)
            }
    }

    @RepeatedTest(10)
    fun `M provide the ITNV metric for current view W using time based identifier, threshold respected`(
        forge: Forge
    ) {
        // When
        val validActionType = forge.aValidLastInteractionActionType()
        val monitor = enableRum { setLastInteractionIdentifier(TimeBasedInteractionIdentifier()) }
        val appExpectedItnvTime = runSuccessfulItnvTestScenario(monitor, validActionType)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                // Initial previous view
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
                // Action event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasActionName(lastInteractionName)
                hasViewName(previousViewName)
            }
            .hasRumEvent(index = 2) {
                // View updated with event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                hasActionCount(1)
            }
            .hasRumEvent(index = 3) {
                // Previous view stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                doesNotHaveInteractionToNextViewTime()
                hasActionCount(1)
                hasViewName(previousViewName)
            }
            .hasRumEvent(index = 4) {
                // New View Event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasInteractionToNextViewTime(appExpectedItnvTime, ITNV_METRIC_OFFSET_IN_NANOSECONDS)
            }
            .hasRumEvent(index = 5) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasInteractionToNextViewTime(appExpectedItnvTime, ITNV_METRIC_OFFSET_IN_NANOSECONDS)
                hasViewName(viewName)
            }
    }

    @RepeatedTest(2)
    fun `M not provide the ITNV metric for current view W using time based identifier, threshold surpassed`(
        forge: Forge
    ) {
        // When
        val validActionType = forge.aValidLastInteractionActionType()
        val monitor = enableRum { setLastInteractionIdentifier(TimeBasedInteractionIdentifier()) }
        runItnvThresholdSurpassedScenario(monitor, validActionType, 3010)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                // Initial previous view
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
                // Action event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasActionName(lastInteractionName)
                hasViewName(previousViewName)
            }
            .hasRumEvent(index = 2) {
                // View updated with event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                hasActionCount(1)
            }
            .hasRumEvent(index = 3) {
                // Previous view stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                doesNotHaveInteractionToNextViewTime()
                hasActionCount(1)
                hasViewName(previousViewName)
            }
            .hasRumEvent(index = 4) {
                // New View Event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                doesNotHaveInteractionToNextViewTime()
            }
            .hasRumEvent(index = 5) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                doesNotHaveInteractionToNextViewTime()
                hasViewName(viewName)
            }
    }

    @RepeatedTest(2)
    fun `M not provide the ITNV metric for current view W using time based identifier {custom threshold, failed}`(
        forge: Forge
    ) {
        // When
        val customThreshold = forge.aLong(min = 300, max = 4000)
        val validActionType = forge.aValidLastInteractionActionType()
        val monitor = enableRum {
            setLastInteractionIdentifier(TimeBasedInteractionIdentifier(customThreshold))
        }
        runItnvThresholdSurpassedScenario(monitor, validActionType, customThreshold + 10)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(6)
            .hasRumEvent(index = 0) {
                // Initial previous view
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
                // Action event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasActionName(lastInteractionName)
                hasViewName(previousViewName)
            }
            .hasRumEvent(index = 2) {
                // View updated with event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                hasViewName(previousViewName)
                doesNotHaveInteractionToNextViewTime()
                hasActionCount(1)
            }
            .hasRumEvent(index = 3) {
                // Previous view stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(previousViewKey)
                doesNotHaveInteractionToNextViewTime()
                hasActionCount(1)
                hasViewName(previousViewName)
            }
            .hasRumEvent(index = 4) {
                // New View Event
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                doesNotHaveInteractionToNextViewTime()
            }
            .hasRumEvent(index = 5) {
                // View stopped
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                doesNotHaveInteractionToNextViewTime()
                hasViewName(viewName)
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
