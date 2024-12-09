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
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.integration.tests.assertj.hasRumEvent
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.metric.networksettled.NetworkSettledResourceContext
import com.datadog.android.rum.metric.networksettled.TimeBasedInitialResourceIdentifier
import com.datadog.android.tests.assertj.StubEventsAssert.Companion.assertThat
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
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
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
class ViewLoadingTimeMetricsTests {

    private lateinit var stubSdkCore: StubSDKCore

    @StringForgery
    private lateinit var fakeApplicationId: String

    @StringForgery
    private lateinit var viewKey: String

    @StringForgery
    private lateinit var viewName: String

    @StringForgery
    private lateinit var resourceKey: String

    @StringForgery(regex = "https://[a-z]+/[a-z]+\\.com")
    private lateinit var resourceUrl: String

    @IntForgery(200, 599)
    private var resourceStatus: Int = 0

    @LongForgery(0)
    var resourceSize: Long = 0L

    @Forgery
    private lateinit var rumResourceMethod: RumResourceMethod

    @Forgery
    private lateinit var rumResourceKind: RumResourceKind

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
    }

    // region Time to network settle

    @RepeatedTest(10)
    fun `M provide the TTNS metric for each view W initial resource was stopped and sent`() {
        // Given
        val fakeRumConfiguration = configurationBuilder()
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        val startViewTime = System.nanoTime()
        monitor.startView(viewKey, viewName, emptyMap())
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl, emptyMap())
        Thread.sleep(100)
        val stopResourceTime = System.nanoTime()
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind, emptyMap())
        monitor.stopView(viewKey, emptyMap())
        val appExpectedTtnsTime = (stopResourceTime - startViewTime)

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
                hasNetworkSettledTime(appExpectedTtnsTime, METRIC_OFFSET_IN_NANOSECONDS)
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
                hasNetworkSettledTime(appExpectedTtnsTime, METRIC_OFFSET_IN_NANOSECONDS)
                hasViewName(viewName)
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
        val startViewTime = System.nanoTime()
        monitor.startView(viewKey, viewName, emptyMap())
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl, emptyMap())
        Thread.sleep(100)
        val stopResourceTime = System.nanoTime()
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind, emptyMap())
        monitor.addTiming(forge.anAlphabeticalString())
        Thread.sleep(100)
        monitor.addTiming(forge.anAlphabeticalString())
        monitor.stopView(viewKey, emptyMap())
        val appExpectedTtnsTime = (stopResourceTime - startViewTime)

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
                hasNetworkSettledTime(appExpectedTtnsTime, METRIC_OFFSET_IN_NANOSECONDS)
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
                hasNetworkSettledTime(appExpectedTtnsTime, METRIC_OFFSET_IN_NANOSECONDS)
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
                hasNetworkSettledTime(appExpectedTtnsTime, METRIC_OFFSET_IN_NANOSECONDS)
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
                hasNetworkSettledTime(appExpectedTtnsTime, METRIC_OFFSET_IN_NANOSECONDS)
                hasViewName(viewName)
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
        val startViewTime = System.nanoTime()
        monitor.startView(viewKey, viewName, emptyMap())
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl, emptyMap())
        Thread.sleep(100)
        val stopResourceTime = System.nanoTime()
        monitor.stopResourceWithError(resourceKey, resourceStatus, errorMessage, errorSource, throwable, emptyMap())
        monitor.stopView(viewKey, emptyMap())
        val appExpectedTtnsTime = (stopResourceTime - startViewTime)

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
                hasNetworkSettledTime(appExpectedTtnsTime, METRIC_OFFSET_IN_NANOSECONDS)
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
                hasNetworkSettledTime(appExpectedTtnsTime, METRIC_OFFSET_IN_NANOSECONDS)
                hasViewName(viewName)
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
        monitor.startView(viewKey, viewName, emptyMap())
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl, emptyMap())
        Thread.sleep(100)
        monitor.stopResourceWithError(resourceKey, resourceStatus, errorMessage, errorSource, throwable, emptyMap())
        monitor.stopView(viewKey, emptyMap())

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
        // Given
        val fakeRumConfiguration = configurationBuilder()
            .setResourceEventMapper({ null })
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        monitor.startView(viewKey, viewName, emptyMap())
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl, emptyMap())
        Thread.sleep(100)
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind, emptyMap())
        monitor.stopView(viewKey, emptyMap())

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
        // Given
        val fakeRumConfiguration = configurationBuilder()
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        monitor.startView(viewKey, viewName, emptyMap())
        // wait for more than the default threshold in the default identifier (100ms)
        Thread.sleep(100 + 10)
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl, emptyMap())
        Thread.sleep(100)
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind, emptyMap())
        monitor.stopView(viewKey, emptyMap())

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
        // Given
        val fakeRumConfiguration = configurationBuilder()
            .setInitialResourceIdentifier(TimeBasedInitialResourceIdentifier(resourceIdentifierThresholdMs))
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val monitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        monitor.startView(viewKey, viewName, emptyMap())
        // wait for more than the custom threshold
        Thread.sleep(resourceIdentifierThresholdMs + 10)
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl, emptyMap())
        Thread.sleep(100)
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind, emptyMap())
        monitor.stopView(viewKey, emptyMap())

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
        monitor.startView(viewKey, viewName, emptyMap())
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl, emptyMap())
        Thread.sleep(100)
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind, emptyMap())
        monitor.stopView(viewKey, emptyMap())

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

    // region internals

    private fun configurationBuilder() = RumConfiguration.Builder(fakeApplicationId)
        .trackNonFatalAnrs(false)

    // endregion

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        private val METRIC_OFFSET_IN_NANOSECONDS = Offset.offset(TimeUnit.MILLISECONDS.toNanos(10))

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
