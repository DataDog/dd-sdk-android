/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.core.stub.StubTelemetryEvent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.integration.tests.assertj.StubTelemetryEventAssert.Companion.assertThat
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RumSessionEndedIntegrationTelemetryTest {

    @StringForgery
    private lateinit var fakeApplicationId: String

    private lateinit var fakeRumConfiguration: RumConfiguration

    private lateinit var stubSdkCore: StubSDKCore

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
        fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .setTelemetrySampleRate(100f)
            .build()
    }

    @Test
    fun `M not receive an event W the session is not sampled`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // Given
        val configuration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .setSessionSampleRate(0f)
            .build()
        Rum.enable(configuration, stubSdkCore)
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        rumMonitor.startView(key = viewKey, name = viewName)

        // When
        rumMonitor.stopSession()

        // Then
        assertThat(stubSdkCore.lastMetric()).isNull()
    }

    @Test
    fun `M receive an event with 'was_stopped' is true W stopSession()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // Given
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        rumMonitor.startView(key = viewKey, name = viewName)

        // When
        rumMonitor.stopSession()

        // Then
        assertThat(stubSdkCore.lastMetricWithMessage(RUM_SESSION_ENDED_METRIC_NAME))
            .hasWasStopped(true)
    }

    @Test
    fun `M receive an event with correct view counts W track multiple views`(
        @IntForgery(min = 1, max = 5) repeatCount: Int,
        forge: Forge
    ) {
        // Given
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        repeat(repeatCount) {
            val viewKey = forge.anAlphaNumericalString()
            rumMonitor.startView(key = viewKey, name = forge.anAlphaNumericalString())
            rumMonitor.stopView(key = viewKey)
        }

        // When
        rumMonitor.stopSession()

        // Then
        assertThat(stubSdkCore.lastMetric())
            .hasViewCount(repeatCount)
    }

    @Test
    fun `M have correct 'has_background_events_tracking_enabled' W stopSession()`(
        @BoolForgery trackBackgroundEvents: Boolean,
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        val fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .setTelemetrySampleRate(100f)
            .trackBackgroundEvents(trackBackgroundEvents)
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        rumMonitor.startView(key = viewKey, name = viewName)

        // When
        rumMonitor.stopSession()

        // Then
        assertThat(stubSdkCore.lastMetricWithMessage(RUM_SESSION_ENDED_METRIC_NAME))
            .hasBackgroundEventsTrackingEnable(trackBackgroundEvents)
    }

    @Test
    fun `M receive an event with correct 'ntp_offset' W stopSession()`(

        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @LongForgery ntpOffsetAtStart: Long,
        @LongForgery ntpOffsetAtEnd: Long
    ) {
        // Given
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        whenever(stubSdkCore.time.serverTimeOffsetMs).thenReturn(ntpOffsetAtStart)
        rumMonitor.startView(key = viewKey, name = viewName)

        // When
        whenever(stubSdkCore.time.serverTimeOffsetMs).thenReturn(ntpOffsetAtEnd)
        rumMonitor.stopSession()

        // Then
        assertThat(stubSdkCore.lastMetricWithMessage(RUM_SESSION_ENDED_METRIC_NAME))
            .hasNtpOffsetAtStart(ntpOffsetAtStart)
            .hasNtpOffsetAtEnd(ntpOffsetAtEnd)
    }

    @Test
    fun `M have correct missed type W events are recorded with no view active`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @IntForgery(min = 1, max = 5) missedActionCount: Int,
        @IntForgery(min = 1, max = 5) missedErrorCount: Int,
        @IntForgery(min = 1, max = 5) missedResourceCount: Int,
        forge: Forge
    ) {
        // Given
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(viewKey, viewName)
        rumMonitor.stopView(viewKey)

        repeat(missedActionCount) {
            val actionType = forge.aValueFrom(RumActionType::class.java)
            val name = forge.aString()
            rumMonitor.addAction(type = actionType, name = name)
        }

        repeat(missedErrorCount) {
            val source = forge.aValueFrom(RumErrorSource::class.java)
            val message = forge.aString()
            rumMonitor.addError(message = message, source = source, throwable = null)
        }

        repeat(missedResourceCount) {
            val key = forge.aString()
            val url = forge.aString()
            val method = forge.aValueFrom(RumResourceMethod::class.java)
            rumMonitor.startResource(key = key, method = method, url = url)
        }
        // Integration test for long task is skipped since we can not trigger long task of main thread in unit test.

        rumMonitor.stopSession()

        // Then
        assertThat(stubSdkCore.lastMetric())
            .hasNoViewActionEventCounts(missedActionCount)
            .hasNoViewErrorEventCounts(missedErrorCount)
            .hasNoViewResourceEventCounts(missedResourceCount)
    }

    private fun StubSDKCore.lastMetric(): StubTelemetryEvent? {
        return telemetryEventsWritten().lastOrNull { it.type == StubTelemetryEvent.Type.METRIC }
    }

    private fun StubSDKCore.lastMetricWithMessage(msg: String): StubTelemetryEvent? {
        return telemetryEventsWritten().lastOrNull {
            it.type == StubTelemetryEvent.Type.METRIC &&
                it.message == msg
        }
    }

    companion object {

        private const val RUM_SESSION_ENDED_METRIC_NAME: String = "[Mobile Metric] RUM Session Ended"

        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
