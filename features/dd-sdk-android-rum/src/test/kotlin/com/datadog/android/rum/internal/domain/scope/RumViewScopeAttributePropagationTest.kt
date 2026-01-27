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
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.assertj.ActionEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.ErrorEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.LongTaskEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.ViewEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.accessibility.AccessibilitySnapshotManager
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher
import com.datadog.android.rum.internal.metric.ViewInitializationMetricsState
import com.datadog.android.rum.internal.metric.ViewMetricDispatcher
import com.datadog.android.rum.internal.metric.interactiontonextview.InteractionToNextViewMetricResolver
import com.datadog.android.rum.internal.metric.networksettled.NetworkSettledMetricResolver
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
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
internal class RumViewScopeAttributePropagationTest {

    lateinit var testedScope: RumViewScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockAccessibilitySnapshotManager: AccessibilitySnapshotManager

    @Mock
    lateinit var mockBatteryInfoProvider: InfoProvider<BatteryInfo>

    @Mock
    lateinit var mockDisplayInfoProvider: InfoProvider<DisplayInfo>

    @Mock
    private lateinit var mockInsightsCollector: InsightsCollector

    @Mock
    lateinit var mockResolver: FirstPartyHostHeaderTypeResolver

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
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockSlowFramesListener: SlowFramesListener

    @Forgery
    lateinit var fakeKey: RumScopeKey

    lateinit var fakeParentAttributes: Map<String, Any?>

    lateinit var fakeViewAttributes: Map<String, Any?>

    lateinit var fakeChildAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    lateinit var fakeEventTime: Time

    lateinit var fakeEvent: RumRawEvent

    @Forgery
    lateinit var fakeTimeInfoAtScopeStart: TimeInfo

    @Forgery
    lateinit var fakeNetworkInfoAtScopeStart: NetworkInfo

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BoolForgery
    var fakeHasReplay: Boolean = false

    @Mock
    lateinit var mockFeaturesContextResolver: FeaturesContextResolver

    @Mock
    lateinit var mockViewChangedListener: RumViewChangedListener

    @Mock
    lateinit var mockSessionEndedMetricDispatcher: SessionMetricDispatcher

    @Mock
    lateinit var mockNetworkSettledMetricResolver: NetworkSettledMetricResolver

    @Mock
    lateinit var mockInteractionToNextViewMetricResolver: InteractionToNextViewMetricResolver

    @Mock
    lateinit var mockViewEndedMetricDispatcher: ViewEndedMetricDispatcher

    @Forgery
    lateinit var fakeTnsState: ViewInitializationMetricsState

    @Forgery
    lateinit var fakeInvState: ViewInitializationMetricsState

    private var fakeNetworkSettledMetricValue: Long? = null
    private var fakeInteractionToNextViewMetricValue: Long? = null

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    private var fakeRumSessionType: RumSessionType? = null

    private var fakeSampleRate: Float = 0.0f

    @BeforeEach
    fun `set up`(forge: Forge) {
        val fakeBrightness = forge.aFloat(0f, 255f)
        val fakeBatteryLevel = forge.aFloat(0f, 100f)
        val fakeLowPowerMode = forge.aBool()
        fakeNetworkSettledMetricValue = forge.aNullable { aPositiveLong() }
        fakeRumSessionType = forge.aNullable { aValueFrom(RumSessionType::class.java) }
        fakeInteractionToNextViewMetricValue = forge.aNullable { aPositiveLong() }
        whenever(mockNetworkSettledMetricResolver.resolveMetric()) doReturn fakeNetworkSettledMetricValue
        whenever(mockNetworkSettledMetricResolver.getState()) doReturn fakeTnsState
        whenever(mockInteractionToNextViewMetricResolver.getState(any())) doReturn fakeInvState
        whenever(mockInteractionToNextViewMetricResolver.resolveMetric(any())) doReturn
            fakeInteractionToNextViewMetricValue

        fakeDatadogContext = fakeDatadogContext.copy(
            source = forge.aValueFrom(ViewEvent.ViewEventSource::class.java).toJson().asString
        )

        fakeParentContext =
            fakeParentContext.copy(syntheticsTestId = null, syntheticsResultId = null)

        val fakeOffset = -forge.aLong(1000, 50000)
        val fakeTimestamp = System.currentTimeMillis() + fakeOffset
        val fakeNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(fakeOffset)
        val maxLimit = max(Long.MAX_VALUE - fakeTimestamp, Long.MAX_VALUE)
        val minLimit = min(-fakeTimestamp, maxLimit)
        fakeSampleRate = forge.aFloat(min = 0.0f, max = 100.0f)

        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeTimeInfoAtScopeStart.copy(
                serverTimeOffsetMs = forge.aLong(min = minLimit, max = maxLimit)
            )
        )
        fakeEventTime = Time(fakeTimestamp, fakeNanos)
        fakeParentAttributes = forge.exhaustiveAttributes()
        fakeViewAttributes = forge.exhaustiveAttributes()
        fakeChildAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.getForgery()

        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockParentScope.getCustomAttributes()) doReturn fakeParentAttributes.toMutableMap()

        whenever(rumMonitor.mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(rumMonitor.mockSdkCore.time) doReturn fakeTimeInfoAtScopeStart
        whenever(rumMonitor.mockSdkCore.networkInfo) doReturn fakeNetworkInfoAtScopeStart
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger

        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockWriter.write(eq(mockEventBatchWriter), any(), any())) doReturn true
        whenever(mockBatteryInfoProvider.getState()) doReturn BatteryInfo(
            batteryLevel = fakeBatteryLevel,
            lowPowerMode = fakeLowPowerMode
        )
        whenever(mockDisplayInfoProvider.getState()) doReturn DisplayInfo(
            screenBrightness = fakeBrightness
        )
    }

    // region Propagate parent attributes in View Event

    @Test
    fun `M send event with parent attributes W handleEvent(KeepAlive) on active view`() {
        // Given
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        testedScope = newRumViewScope(initialAttributes = emptyMap())

        // When
        val result =
            testedScope.handleEvent(RumRawEvent.KeepAlive(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .containsExactlyContextAttributes(expectedAttributes)
        }
        assertThat(result).isNotNull()
    }

    @Test
    fun `M send event with both parent and view attributes W handleEvent(KeepAlive) on active view`() {
        // Given
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeViewAttributes)
        testedScope = newRumViewScope(initialAttributes = fakeViewAttributes)

        // When
        val result =
            testedScope.handleEvent(RumRawEvent.KeepAlive(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .containsExactlyContextAttributes(expectedAttributes)
        }
        assertThat(result).isNotNull()
    }

    @Test
    fun `M send event with overridden parent attributes W handleEvent(KeepAlive) on active view`(
        forge: Forge
    ) {
        // Given
        val overriddenAttributes = fakeParentAttributes.map { it.key to forge.aString() }.toMap()
        testedScope = newRumViewScope(initialAttributes = overriddenAttributes)

        // When
        val result =
            testedScope.handleEvent(RumRawEvent.KeepAlive(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .containsExactlyContextAttributes(overriddenAttributes)
        }
        assertThat(result).isNotNull()
    }

    @Test
    fun `M send event with original parent attributes W handleEvent(StopView+ErrorSent) on active view`(
        @StringForgery fakeAttrKey: String,
        @StringForgery fakeAttrValue: String
    ) {
        // Given
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeViewAttributes)
        testedScope = newRumViewScope(initialAttributes = fakeViewAttributes)
        val fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId)

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap(), eventTime = fakeEventTime),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        whenever(mockParentScope.getCustomAttributes()) doReturn fakeParentAttributes + (fakeAttrKey to fakeAttrValue)
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(
                eq(mockEventBatchWriter),
                capture(),
                eq(EventType.DEFAULT)
            )
            assertThat(lastValue)
                .containsExactlyContextAttributes(expectedAttributes)
        }
    }

    @Test
    fun `M send event with added view attributes W handleEvent(AddViewAttributes+KeepAlive) on active view`() {
        // Given
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeViewAttributes)
        testedScope = newRumViewScope(initialAttributes = emptyMap())

        // When
        testedScope.handleEvent(
            RumRawEvent.AddViewAttributes(fakeViewAttributes),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val result =
            testedScope.handleEvent(RumRawEvent.KeepAlive(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .containsExactlyContextAttributes(expectedAttributes)
        }
        assertThat(result).isNotNull()
    }

    @Test
    fun `M send event without removed view attributes W handleEvent(AddViewAttributes+KeepAlive) on active view`(
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)]),
            value = AdvancedForgery(string = [StringForgery()])
        ) fakeViewAttributes: Map<String, String>
    ) {
        // Given
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        testedScope = newRumViewScope(initialAttributes = fakeViewAttributes)

        // When
        testedScope.handleEvent(
            RumRawEvent.RemoveViewAttributes(fakeViewAttributes.keys),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val result =
            testedScope.handleEvent(RumRawEvent.KeepAlive(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .containsExactlyContextAttributes(expectedAttributes)
        }
        assertThat(result).isNotNull()
    }

    // endregion

    // region Propagate parent attributes in Error Event

    @Test
    fun `M send event with parent attributes W handleEvent(AddError) on active view`(
        @StringForgery fakeMessage: String,
        @Forgery fakeSource: RumErrorSource,
        @Forgery fakeThrowable: Throwable?,
        @StringForgery fakeStacktrace: String?,
        @BoolForgery fakeIsFatal: Boolean,
        @StringForgery fakeType: String?
    ) {
        // Given
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeChildAttributes)
        testedScope = newRumViewScope(initialAttributes = emptyMap())
        val fakeEvent = RumRawEvent.AddError(
            message = fakeMessage,
            source = fakeSource,
            throwable = fakeThrowable,
            stacktrace = fakeStacktrace,
            isFatal = fakeIsFatal,
            attributes = fakeChildAttributes,
            type = fakeType,
            threads = emptyList()
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        val expectedType = if (fakeIsFatal) EventType.CRASH else EventType.DEFAULT
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(expectedType))
            assertThat(lastValue)
                .containsExactlyContextAttributes(expectedAttributes)
        }
        assertThat(result).isNotNull()
    }

    @Test
    fun `M send event with both parent and view attributes W handleEvent(AddError) on active view`(
        @StringForgery fakeMessage: String,
        @Forgery fakeSource: RumErrorSource,
        @Forgery fakeThrowable: Throwable?,
        @StringForgery fakeStacktrace: String?,
        @BoolForgery fakeIsFatal: Boolean,
        @StringForgery fakeType: String?
    ) {
        // Given
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeViewAttributes)
        expectedAttributes.putAll(fakeChildAttributes)
        testedScope = newRumViewScope(initialAttributes = fakeViewAttributes)
        val fakeEvent = RumRawEvent.AddError(
            message = fakeMessage,
            source = fakeSource,
            throwable = fakeThrowable,
            stacktrace = fakeStacktrace,
            isFatal = fakeIsFatal,
            attributes = fakeChildAttributes,
            type = fakeType,
            threads = emptyList()
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        val expectedType = if (fakeIsFatal) EventType.CRASH else EventType.DEFAULT
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(expectedType))
            assertThat(lastValue)
                .containsExactlyContextAttributes(expectedAttributes)
        }
        assertThat(result).isNotNull()
    }

    @Test
    fun `M send event with overridden parent attributes W handleEvent(AddError) on active view`(
        @StringForgery fakeMessage: String,
        @Forgery fakeSource: RumErrorSource,
        @Forgery fakeThrowable: Throwable?,
        @StringForgery fakeStacktrace: String?,
        @BoolForgery fakeIsFatal: Boolean,
        @StringForgery fakeType: String?,
        forge: Forge
    ) {
        // Given
        val overriddenAttributes =
            fakeParentAttributes.map { it.key to (forge.aString() as? Any) }.toMap()
        val expectedAttributes = overriddenAttributes.toMutableMap()
        expectedAttributes.putAll(fakeChildAttributes)
        testedScope = newRumViewScope(initialAttributes = overriddenAttributes)
        val fakeEvent = RumRawEvent.AddError(
            message = fakeMessage,
            source = fakeSource,
            throwable = fakeThrowable,
            stacktrace = fakeStacktrace,
            isFatal = fakeIsFatal,
            attributes = fakeChildAttributes,
            type = fakeType,
            threads = emptyList()
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        val expectedType = if (fakeIsFatal) EventType.CRASH else EventType.DEFAULT
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(expectedType))
            assertThat(lastValue)
                .containsExactlyContextAttributes(expectedAttributes)
        }
        assertThat(result).isNotNull()
    }

    // endregion

    // region Propagate parent attributes in Long Task Events

    @Test
    fun `M send event with parent attributes W handleEvent(AddLongTask) on active view`(
        @LongForgery(0) fakeDuration: Long,
        @StringForgery fakeTarget: String
    ) {
        // Given
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.put(RumAttributes.LONG_TASK_TARGET, fakeTarget)
        testedScope = newRumViewScope(initialAttributes = emptyMap())
        val fakeEvent = RumRawEvent.AddLongTask(fakeDuration, fakeTarget)

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .containsExactlyContextAttributes(expectedAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNotNull()
    }

    @Test
    fun `M send event with both parent and view attributes W handleEvent(AddLongTask) on active view`(
        @LongForgery(0) fakeDuration: Long,
        @StringForgery fakeTarget: String
    ) {
        // Given
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeViewAttributes)
        expectedAttributes.put(RumAttributes.LONG_TASK_TARGET, fakeTarget)
        testedScope = newRumViewScope(initialAttributes = fakeViewAttributes)
        val fakeEvent = RumRawEvent.AddLongTask(fakeDuration, fakeTarget)

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .containsExactlyContextAttributes(expectedAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNotNull()
    }

    @Test
    fun `M send event with overridden parent attributes W handleEvent(AddLongTask) on active view`(
        @LongForgery(0) fakeDuration: Long,
        @StringForgery fakeTarget: String,
        forge: Forge
    ) {
        // Given
        val overriddenAttributes = fakeParentAttributes.map { it.key to forge.aString() }.toMap()
        val expectedAttributes = overriddenAttributes.toMutableMap()
        expectedAttributes.put(RumAttributes.LONG_TASK_TARGET, fakeTarget)
        testedScope = newRumViewScope(initialAttributes = overriddenAttributes)
        val fakeEvent = RumRawEvent.AddLongTask(fakeDuration, fakeTarget)

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .containsExactlyContextAttributes(expectedAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNotNull()
    }

    // endregion

    // region Helper

    private fun newRumViewScope(
        parentScope: RumScope = mockParentScope,
        sdkCore: InternalSdkCore = rumMonitor.mockSdkCore,
        sessionEndedMetricDispatcher: SessionMetricDispatcher = mockSessionEndedMetricDispatcher,
        key: RumScopeKey = fakeKey,
        eventTime: Time = fakeEventTime,
        initialAttributes: Map<String, Any?> = fakeViewAttributes,
        viewChangedListener: RumViewChangedListener? = mockViewChangedListener,
        firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver = mockResolver,
        cpuVitalMonitor: VitalMonitor = mockCpuVitalMonitor,
        memoryVitalMonitor: VitalMonitor = mockMemoryVitalMonitor,
        frameRateVitalMonitor: VitalMonitor = mockFrameRateVitalMonitor,
        featuresContextResolver: FeaturesContextResolver = mockFeaturesContextResolver,
        type: RumViewType = RumViewType.FOREGROUND,
        trackFrustrations: Boolean = fakeTrackFrustrations,
        sampleRate: Float = fakeSampleRate,
        interactionNextViewMetricResolver: InteractionToNextViewMetricResolver =
            mockInteractionToNextViewMetricResolver,
        networkSettledMetricResolver: NetworkSettledMetricResolver = mockNetworkSettledMetricResolver,
        viewEndedMetricDispatcher: ViewMetricDispatcher = mockViewEndedMetricDispatcher,
        slowFramesListener: SlowFramesListener = mockSlowFramesListener,
        rumSessionType: RumSessionType? = fakeRumSessionType,
        accessibilitySnapshotManager: AccessibilitySnapshotManager = mockAccessibilitySnapshotManager,
        batteryInfoProvider: InfoProvider<BatteryInfo> = mockBatteryInfoProvider,
        displayInfoProvider: InfoProvider<DisplayInfo> = mockDisplayInfoProvider,
        insightsCollector: InsightsCollector = mockInsightsCollector
    ) = RumViewScope(
        parentScope = parentScope,
        sdkCore = sdkCore,
        sessionEndedMetricDispatcher = sessionEndedMetricDispatcher,
        key = key,
        eventTime = eventTime,
        initialAttributes = initialAttributes,
        viewChangedListener = viewChangedListener,
        firstPartyHostHeaderTypeResolver = firstPartyHostHeaderTypeResolver,
        cpuVitalMonitor = cpuVitalMonitor,
        memoryVitalMonitor = memoryVitalMonitor,
        frameRateVitalMonitor = frameRateVitalMonitor,
        featuresContextResolver = featuresContextResolver,
        type = type,
        trackFrustrations = trackFrustrations,
        sampleRate = sampleRate,
        interactionToNextViewMetricResolver = interactionNextViewMetricResolver,
        networkSettledMetricResolver = networkSettledMetricResolver,
        viewEndedMetricDispatcher = viewEndedMetricDispatcher,
        slowFramesListener = slowFramesListener,
        accessibilitySnapshotManager = accessibilitySnapshotManager,
        batteryInfoProvider = batteryInfoProvider,
        displayInfoProvider = displayInfoProvider,
        rumSessionTypeOverride = rumSessionType,
        insightsCollector = insightsCollector
    )

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
