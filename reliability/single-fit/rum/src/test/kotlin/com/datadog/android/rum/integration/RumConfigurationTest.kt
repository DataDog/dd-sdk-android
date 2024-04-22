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
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import kotlin.math.roundToInt

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RumConfigurationTest {

    private lateinit var stubSdkCore: StubSDKCore

    @StringForgery
    private lateinit var fakeApplicationId: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
    }

    @RepeatedTest(16)
    fun `M send no event W samplingRate(0)`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // Given
        val fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .setSessionSampleRate(0f)
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(viewKey, viewName, emptyMap())
        rumMonitor.stopView(viewKey, emptyMap())

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        StubEventsAssert.assertThat(eventsWritten).hasSize(0)
    }

    @RepeatedTest(16)
    fun `M send sampled event W samplingRate(x)`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @FloatForgery(10f, 90f) samplingRate: Float
    ) {
        // Given
        val fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .setSessionSampleRate(samplingRate)
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)
        val repeatCount = 1024
        val expectedSessionOffset = (repeatCount * 5) / 100
        val expectedSessionCount = ((repeatCount * samplingRate) / 100).roundToInt()
        val expectedEventsCount = expectedSessionCount * 2 // 2 events per view, i.e. Start & Stop
        val expectedEventsOffset = expectedSessionOffset * 2 // 2 events per view, i.e. Start & Stop

        // When
        repeat(repeatCount) {
            rumMonitor.startView(viewKey, viewName, emptyMap())
            rumMonitor.stopView(viewKey, emptyMap())
            rumMonitor.stopSession()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten.size).isCloseTo(expectedEventsCount, offset(expectedEventsOffset))
    }

    @RepeatedTest(16)
    fun `M send all events W samplingRate(100)`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // Given
        val fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .setSessionSampleRate(100f)
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)
        val repeatCount = 64
        val expectedEventsCount = repeatCount * 2 // 2 events per view, i.e. Start & Stop

        // When
        repeat(repeatCount) {
            rumMonitor.startView(viewKey, viewName, emptyMap())
            rumMonitor.stopView(viewKey, emptyMap())
            rumMonitor.stopSession()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten.size).isEqualTo(expectedEventsCount)
    }

    @RepeatedTest(16)
    fun `M send mapped events W setResourceEventMapper()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery originalResourceUrl: String,
        @StringForgery mappedResourceUrl: String,
        @Forgery resourceMethod: RumResourceMethod,
        @IntForgery(200, 599) statusCode: Int,
        @LongForgery size: Long,
        @Forgery resourceKind: RumResourceKind
    ) {
        // Given
        val fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .setResourceEventMapper {
                it.apply {
                    resource.url = mappedResourceUrl
                }
            }
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(viewKey, viewName, emptyMap())
        rumMonitor.startResource(originalResourceUrl, resourceMethod, originalResourceUrl, emptyMap())
        rumMonitor.stopResource(originalResourceUrl, statusCode, size, resourceKind, emptyMap())

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        StubEventsAssert.assertThat(eventsWritten)
            .hasRumEvent(index = 1) {
                hasResourceUrl(mappedResourceUrl)
            }
    }

    @RepeatedTest(16)
    fun `M send mapped events W setErrorEventMapper()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery originalErrorMessage: String,
        @StringForgery mappedErrorMessage: String,
        @Forgery errorSource: RumErrorSource,
        @StringForgery mappedErrorFingerprint: String,
        @Forgery throwable: Throwable
    ) {
        // Given
        val fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .setErrorEventMapper {
                it.apply {
                    error.message = mappedErrorMessage
                    error.fingerprint = mappedErrorFingerprint
                }
            }
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(viewKey, viewName, emptyMap())
        rumMonitor.addError(originalErrorMessage, errorSource, throwable, emptyMap())

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        StubEventsAssert.assertThat(eventsWritten)
            .hasRumEvent(index = 1) {
                hasErrorMessage(mappedErrorMessage)
                hasErrorFingerprint(mappedErrorFingerprint)
            }
    }

    @RepeatedTest(16)
    fun `M send mapped events W setActionEventMapper()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @Forgery actionType: RumActionType,
        @StringForgery originalTargetName: String,
        @StringForgery mappedTargetName: String
    ) {
        // Given
        val fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .setActionEventMapper {
                it.apply {
                    action.target?.name = mappedTargetName
                }
            }
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(viewKey, viewName, emptyMap())
        rumMonitor.startAction(actionType, originalTargetName, emptyMap())
        rumMonitor.stopAction(actionType, originalTargetName, emptyMap())
        Thread.sleep(100)
        // Used to trigger the action event
        rumMonitor.stopView(viewKey, emptyMap())

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        StubEventsAssert.assertThat(eventsWritten)
            .hasRumEvent(index = 1) {
                hasActionTargetName(mappedTargetName)
            }
    }

    @RepeatedTest(16)
    fun `M send mapped events W setViewEventMapper()`(
        @StringForgery originalViewName: String,
        @StringForgery originalViewUrl: String,
        @StringForgery mappedViewName: String,
        @StringForgery mappedViewUrl: String
    ) {
        // Given
        val fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .setViewEventMapper {
                it.apply {
                    view.name = mappedViewName
                    view.url = mappedViewUrl
                }
            }
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)

        // When
        GlobalRumMonitor.get(stubSdkCore).startView(originalViewUrl, originalViewName, emptyMap())

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        StubEventsAssert.assertThat(eventsWritten)
            .hasRumEvent(index = 0) {
                hasViewName(mappedViewName)
                hasViewUrl(mappedViewUrl)
            }
    }

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
