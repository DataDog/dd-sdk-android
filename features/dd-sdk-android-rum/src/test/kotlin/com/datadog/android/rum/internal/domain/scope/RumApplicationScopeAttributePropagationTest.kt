/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumApplicationScopeAttributePropagationTest {

    lateinit var testedScope: RumApplicationScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockResolver: FirstPartyHostHeaderTypeResolver

    @Mock
    lateinit var mockSessionListener: RumSessionListener

    @Mock
    lateinit var mockLastInteractionIdentifier: LastInteractionIdentifier

    @Mock
    lateinit var mockCpuVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockMemoryVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockFrameRateVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockFeaturesContextResolver: FeaturesContextResolver

    @Mock
    lateinit var mockSessionEndedMetricDispatcher: SessionMetricDispatcher

    @Mock
    lateinit var mockInitialResourceIdentifier: InitialResourceIdentifier

    @Mock
    lateinit var mockSlowFramesListener: SlowFramesListener

    lateinit var fakeEventTime: Time

    lateinit var fakeEvent: RumRawEvent

    @Forgery
    lateinit var fakeTimeInfoAtScopeStart: TimeInfo

    @Forgery
    lateinit var fakeNetworkInfoAtScopeStart: NetworkInfo

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    lateinit var fakeGlobalAttributes: Map<String, Any?>

    @BoolForgery
    var fakeHasReplay: Boolean = false

    @FloatForgery(min = 0f, max = 100f)
    var fakeSampleRate: Float = 0f

    @BoolForgery
    var fakeBackgroundTrackingEnabled: Boolean = false

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    @StringForgery
    lateinit var fakeApplicationId: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeGlobalAttributes = forge.exhaustiveAttributes()

        fakeDatadogContext = fakeDatadogContext.copy(
            source = forge.aValueFrom(ViewEvent.ViewEventSource::class.java).toJson().asString
        )

        val fakeOffset = -forge.aLong(1000, 50000)
        val fakeTimestamp = System.currentTimeMillis() + fakeOffset
        val fakeNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(fakeOffset)
        val maxLimit = max(Long.MAX_VALUE - fakeTimestamp, Long.MAX_VALUE)
        val minLimit = min(-fakeTimestamp, maxLimit)

        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeTimeInfoAtScopeStart.copy(
                serverTimeOffsetMs = forge.aLong(min = minLimit, max = maxLimit)
            )
        )
        fakeEventTime = Time(fakeTimestamp, fakeNanos)

        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }

        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mock()

        testedScope = RumApplicationScope(
            applicationId = fakeApplicationId,
            sdkCore = rumMonitor.mockSdkCore,
            sampleRate = fakeSampleRate,
            backgroundTrackingEnabled = fakeBackgroundTrackingEnabled,
            trackFrustrations = fakeTrackFrustrations,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            sessionListener = mockSessionListener,
            initialResourceIdentifier = mockInitialResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener
        )
    }

    // region Propagate parent attributes

    @Test
    fun `M return global attributes W getCustomAttributes()`() {
        // Given
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn fakeGlobalAttributes

        // When
        val customAttributes = testedScope.getCustomAttributes()

        // Then
        assertThat(customAttributes)
            .containsExactlyInAnyOrderEntriesOf(fakeGlobalAttributes)
    }

    // endregion

    companion object {

        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
