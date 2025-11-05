/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.app.ActivityManager.RunningAppProcessInfo
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.anr.ANRDetectorRunnable
import com.datadog.android.rum.internal.anr.ANRException
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.accessibility.AccessibilitySnapshotManager
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.domain.state.ViewUIPerformanceReport
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyApiUsage
import com.datadog.android.rum.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumViewManagerScopeTest {

    lateinit var testedScope: RumViewManagerScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockChildScope: RumViewScope

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockResolver: FirstPartyHostHeaderTypeResolver

    @Mock
    lateinit var mockCpuVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockMemoryVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockFrameRateVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockSessionEndedMetricDispatcher: SessionMetricDispatcher

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockViewChangedListener: RumViewChangedListener

    @Mock
    lateinit var mockNetworkSettledResourceIdentifier: InitialResourceIdentifier

    @Mock
    lateinit var mockLastInteractionIdentifier: LastInteractionIdentifier

    @Mock
    lateinit var mockSlowFramesListener: SlowFramesListener

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeViewUIPerformanceReport: ViewUIPerformanceReport

    @Forgery
    lateinit var fakeTime: TimeInfo

    @Mock
    lateinit var mockAccessibilitySnapshotManager: AccessibilitySnapshotManager

    @Mock
    lateinit var mockBatteryInfoProvider: InfoProvider<BatteryInfo>

    @Mock
    lateinit var mockDisplayInfoProvider: InfoProvider<DisplayInfo>

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    private var fakeSampleRate: Float = 0.0f

    private var fakeRumSessionType: RumSessionType? = null

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeSampleRate = forge.aFloat(min = 0.0f, max = 100.0f)

        whenever(mockSdkCore.time) doReturn fakeTime

        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockChildScope.handleEvent(any(), any(), any(), any())) doReturn mockChildScope
        whenever(mockChildScope.isActive()) doReturn true
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSlowFramesListener.resolveReport(any(), any(), any())) doReturn fakeViewUIPerformanceReport
        whenever(mockAccessibilitySnapshotManager.getIfChanged()) doReturn mock()

        fakeRumSessionType = forge.aNullable { aValueFrom(RumSessionType::class.java) }

        testedScope = RumViewManagerScope(
            mockParentScope,
            mockSdkCore,
            mockSessionEndedMetricDispatcher,
            true,
            fakeTrackFrustrations,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            applicationDisplayed = false,
            sampleRate = fakeSampleRate,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider
        )
    }

    @AfterEach
    fun `tear down`() {
        // whatever happens
        assertThat(testedScope.getRumContext()).isEqualTo(fakeParentContext)
    }

    // region Children scopes

    @Test
    fun `M delegate to child scope W handleEvent()`() {
        // Given
        val fakeEvent: RumRawEvent = mock()
        whenever(fakeEvent.eventTime) doReturn Time()
        testedScope.childrenScopes.add(mockChildScope)

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        assertThat(result).isSameAs(testedScope)
        verifyNoInteractions(mockWriter)
    }

    @Test
    fun `M keep children scope W handleEvent child returns non null`() {
        // Given
        val fakeEvent: RumRawEvent = mock()
        testedScope.childrenScopes.add(mockChildScope)
        whenever(
            mockChildScope.handleEvent(
                fakeEvent,
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        ) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).containsExactly(mockChildScope)
        assertThat(result).isSameAs(testedScope)
        verifyNoInteractions(mockWriter)
    }

    @Test
    fun `M remove children scope W handleEvent child returns null`() {
        // Given
        val fakeEvent: RumRawEvent = mock()
        testedScope.childrenScopes.add(mockChildScope)
        whenever(
            mockChildScope.handleEvent(
                fakeEvent,
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        ) doReturn null

        // When
        val result = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).isEmpty()
        assertThat(result).isSameAs(testedScope)
        verifyNoInteractions(mockWriter)
    }

    // endregion

    // region Foreground View

    @Test
    fun `M start a foreground ViewScope W handleEvent(StartView) { app displayed }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.startViewEvent()
        testedScope.applicationDisplayed = true

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp)
                    .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                assertThat(it.key).isEqualTo(fakeEvent.key)
                assertThat(it.type).isEqualTo(RumViewType.FOREGROUND)
                assertThat(it.viewAttributes).containsAllEntriesOf(fakeEvent.attributes)
                assertThat(it.firstPartyHostHeaderTypeResolver).isSameAs(mockResolver)
                assertThat(it.version).isEqualTo(2)
            }
        assertThat(testedScope.applicationDisplayed).isTrue()
        assertThat(testedScope.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
    }

    @Test
    fun `M start a foreground ViewScope W handleEvent(StartView) { app not displayed }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.startViewEvent()
        testedScope.applicationDisplayed = false

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp)
                    .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                assertThat(it.key).isEqualTo(fakeEvent.key)
                assertThat(it.type).isEqualTo(RumViewType.FOREGROUND)
                assertThat(it.viewAttributes).containsAllEntriesOf(fakeEvent.attributes)
                assertThat(it.firstPartyHostHeaderTypeResolver).isSameAs(mockResolver)
                assertThat(it.version).isEqualTo(2)
            }
        assertThat(testedScope.applicationDisplayed).isTrue()
        assertThat(testedScope.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
    }

    @Test
    fun `M reset feature flags W handleEvent(StartView) { view already active }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.startViewEvent()
        testedScope.applicationDisplayed = true
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                forge.anAlphabeticalString(),
                forge.anAlphaNumericalString()
            ),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val secondViewEvent = forge.startViewEvent()

        // When
        testedScope.handleEvent(secondViewEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.featureFlags).isEmpty()
            }
    }

    @Test
    fun `M send gap message W handleEvent(StopView) + handleEvent(StartView)`(
        @LongForgery(10, 30) fakeSleepMs: Long,
        forge: Forge
    ) {
        // Given
        val firstViewEvent = forge.startViewEvent()
        testedScope.applicationDisplayed = true
        testedScope.handleEvent(firstViewEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val stopFirstViewEvent = RumRawEvent.StopView(firstViewEvent.key, emptyMap())
        testedScope.handleEvent(stopFirstViewEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // When
        Thread.sleep(fakeSleepMs)
        val secondViewEvent = forge.startViewEvent()
        testedScope.handleEvent(secondViewEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        val messageBuilderCaptor = argumentCaptor<() -> String>()
        val additionalPropertiesCaptor = argumentCaptor<Map<String, Any?>>()

        verify(mockInternalLogger).logMetric(
            messageBuilderCaptor.capture(),
            additionalPropertiesCaptor.capture(),
            eq(1f),
            eq(null)
        )

        assertThat(additionalPropertiesCaptor.firstValue).containsKey(RumViewManagerScope.ATTR_GAP_BETWEEN_VIEWS)
        val gapNs = additionalPropertiesCaptor.firstValue[RumViewManagerScope.ATTR_GAP_BETWEEN_VIEWS] as Long
        val minNs = TimeUnit.MILLISECONDS.toNanos(fakeSleepMs)
        val maxNs = TimeUnit.MILLISECONDS.toNanos(fakeSleepMs + 15)
        assertThat(gapNs).isBetween(minNs, maxNs)
        assertThat(messageBuilderCaptor.firstValue()).isEqualTo("[Mobile Metric] Gap between views")
    }

    @Test
    fun `M not send gap message W handleEvent(StartView) + handleEvent(StartView)`(
        forge: Forge
    ) {
        // Given
        val firstViewEvent = forge.startViewEvent()
        testedScope.applicationDisplayed = true
        testedScope.handleEvent(firstViewEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // When
        Thread.sleep(15)
        val secondViewEvent = forge.startViewEvent()
        testedScope.handleEvent(secondViewEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            listOf(InternalLogger.Target.TELEMETRY, InternalLogger.Target.MAINTAINER),
            { it.matches(Regex("Gap between views was \\d+ nanoseconds")) },
            mode = never()
        )
    }

    // endregion

    // region Background View

    @Test
    fun `M start a bg ViewScope W handleEvent { app displayed, event is relevant }`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = true
        val fakeEvent = forge.validBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp)
                    .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                assertThat(it.key.id).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_ID)
                assertThat(it.key.url).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_URL)
                assertThat(it.key.name).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_NAME)
                assertThat(it.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
            }
    }

    @Test
    fun `M start a bg ViewScope W handleEvent { stopped view, event is relevant }`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = true
        testedScope.childrenScopes.add(mockChildScope)
        whenever(mockChildScope.isActive()) doReturn false
        whenever(mockChildScope.handleEvent(any(), any(), any(), any())) doReturn mockChildScope
        val fakeEvent = forge.validBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(2)
        assertThat(testedScope.childrenScopes[0]).isSameAs(mockChildScope)
        assertThat(testedScope.childrenScopes[1])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp)
                    .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                assertThat(it.key.id).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_ID)
                assertThat(it.key.url).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_URL)
                assertThat(it.key.name).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_NAME)
                assertThat(it.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.type).isEqualTo(RumViewType.BACKGROUND)
                assertThat(it.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
            }
    }

    @Test
    fun `M start a bg ViewScope W handleEvent { event is relevant, not foreground }`(
        forge: Forge
    ) {
        // Given
        DdRumContentProvider.processImportance = forge.anElementFrom(
            RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
            RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING,
            @Suppress("DEPRECATION")
            RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING_PRE_28,
            RunningAppProcessInfo.IMPORTANCE_VISIBLE,
            RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE,
            RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE_PRE_26,
            RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE,
            RunningAppProcessInfo.IMPORTANCE_SERVICE,
            RunningAppProcessInfo.IMPORTANCE_CACHED,
            RunningAppProcessInfo.IMPORTANCE_GONE
        )

        testedScope.applicationDisplayed = false
        val fakeEvent = forge.validBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp)
                    .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                assertThat(it.key.id).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_ID)
                assertThat(it.key.url).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_URL)
                assertThat(it.key.name).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_NAME)
                assertThat(it.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.type).isEqualTo(RumViewType.BACKGROUND)
                assertThat(it.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
            }
    }

    @Test
    fun `M not start a bg ViewScope W handleEvent { app displayed, event not relevant}`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = true
        val fakeEvent = forge.invalidBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(0)
    }

    @Test
    fun `M not start a bg ViewScope W handleEvent { app displayed, event is background ANR}`() {
        // Given
        testedScope.applicationDisplayed = true
        val fakeEvent = RumRawEvent.AddError(
            message = ANRDetectorRunnable.ANR_MESSAGE,
            source = RumErrorSource.SOURCE,
            stacktrace = null,
            throwable = ANRException(Thread.currentThread()),
            isFatal = false,
            threads = emptyList(),
            attributes = emptyMap()
        )

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(0)
    }

    @Test
    fun `M not start a bg ViewScope W handleEvent { bg disabled, event is relevant }`(
        forge: Forge
    ) {
        // Given
        testedScope = RumViewManagerScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            viewChangedListener = mockViewChangedListener,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            applicationDisplayed = false,
            sampleRate = fakeSampleRate,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider
        )
        testedScope.applicationDisplayed = true
        val fakeEvent = forge.validBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(0)
    }

    @Test
    fun `M not start a bg ViewScope W handleEvent { app displayed, event relevant, active view }`(
        forge: Forge
    ) {
        // Given
        testedScope = RumViewManagerScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            viewChangedListener = mockViewChangedListener,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            applicationDisplayed = false,
            sampleRate = fakeSampleRate,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider
        )
        testedScope.childrenScopes.add(mockChildScope)
        whenever(mockChildScope.isActive()) doReturn true
        whenever(mockChildScope.handleEvent(any(), any(), any(), any())) doReturn mockChildScope
        val fakeEvent = forge.validBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0]).isSameAs(mockChildScope)
    }

    @Test
    fun `M send warn dev log W handleEvent { app displayed, event is relevant, bg disabled }`(
        forge: Forge
    ) {
        // Given
        testedScope = RumViewManagerScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            viewChangedListener = mockViewChangedListener,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            applicationDisplayed = false,
            sampleRate = fakeSampleRate,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider
        )
        testedScope.applicationDisplayed = true
        val fakeEvent = forge.validBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumViewManagerScope.MESSAGE_MISSING_VIEW
        )
    }

    // endregion

    // region AppLaunch View

    @Test
    fun `M start an AppLaunch ViewScope W handleEvent { app not displayed }`(
        forge: Forge
    ) {
        // Given
        DdRumContentProvider.processImportance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        testedScope.applicationDisplayed = false
        val fakeAppStartEvent = forge.applicationStartedEvent()

        // When
        testedScope.handleEvent(fakeAppStartEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp)
                    .isEqualTo(resolveExpectedTimestamp(fakeAppStartEvent.eventTime.timestamp))
                assertThat(it.key.id).isEqualTo(RumViewManagerScope.RUM_APP_LAUNCH_VIEW_ID)
                assertThat(it.key.url).isEqualTo(RumViewManagerScope.RUM_APP_LAUNCH_VIEW_URL)
                assertThat(it.key.name).isEqualTo(RumViewManagerScope.RUM_APP_LAUNCH_VIEW_NAME)
                assertThat(it.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.type).isEqualTo(RumViewType.APPLICATION_LAUNCH)
            }
    }

    @Test
    fun `M not start an AppLaunch ViewScope W handleEvent { app displayed }`(
        forge: Forge
    ) {
        // Given
        testedScope = RumViewManagerScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            viewChangedListener = mockViewChangedListener,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            applicationDisplayed = true,
            sampleRate = fakeSampleRate,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider
        )
        testedScope.childrenScopes.add(mockChildScope)
        whenever(mockChildScope.isActive()) doReturn true
        whenever(mockChildScope.handleEvent(any(), any(), any(), any())) doReturn mockChildScope

        val fakeEvent = forge.applicationStartedEvent()

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0]).isSameAs(mockChildScope)
    }

    @Test
    fun `M not start an AppLaunch ViewScope W handleEvent { session stopped }`(
        forge: Forge
    ) {
        // Given
        testedScope = RumViewManagerScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            viewChangedListener = mockViewChangedListener,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            applicationDisplayed = false,
            sampleRate = fakeSampleRate,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider
        )
        testedScope.stopped = true
        val fakeEvent = forge.applicationStartedEvent()

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
    }

    // endregion

    // region ApplicationStarted

    @Test
    fun `M send ApplicationStarted event W handleEvent ()`(
        forge: Forge
    ) {
        // Given
        val mockEventBatchWriter: EventBatchWriter = mock()
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        val fakeAppStartEvent = forge.applicationStartedEvent()

        // When
        testedScope.handleEvent(fakeAppStartEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter, atLeastOnce()).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(firstValue.action.type)
                .isEqualTo(ActionEvent.ActionEventActionType.APPLICATION_START)
            // Application start event occurs at the start time
            assertThat(firstValue.date)
                .isEqualTo(resolveExpectedTimestamp(fakeAppStartEvent.eventTime.timestamp))

            // Duration lasts until the first event is sent to RUM (whatever that is)
            val loadingTime = fakeAppStartEvent.applicationStartupNanos
            assertThat(firstValue.action.loadingTime).isEqualTo(loadingTime)
        }
    }

    @Test
    fun `M not send ApplicationStarted event W onViewDisplayed() {app already started}`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = true
        val fakeAppStartEvent = forge.applicationStartedEvent()

        // When
        testedScope.handleEvent(fakeAppStartEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
    }

    // endregion

    // region Session Ended

    @Test
    fun `M set isActive to false W handleEvent { StopSession }`() {
        // Given
        testedScope.applicationDisplayed = true

        // When
        val result =
            testedScope.handleEvent(RumRawEvent.StopSession(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(result).isNull()
        assertThat(testedScope.isActive()).isFalse()
    }

    @Test
    fun `M return scope from handleEvent W stopped { with active child scopes }`() {
        // Given
        testedScope.applicationDisplayed = true
        testedScope.childrenScopes.add(mockChildScope)
        whenever(
            mockChildScope.handleEvent(
                any(),
                eq(fakeDatadogContext),
                eq(mockEventWriteScope),
                eq(mockWriter)
            )
        ) doReturn mockChildScope

        // When
        val result =
            testedScope.handleEvent(RumRawEvent.StopSession(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M return null from handleEvent W stopped { completed child scopes }`() {
        // Given
        testedScope.applicationDisplayed = true
        testedScope.childrenScopes.add(mockChildScope)
        val stopEvent = RumRawEvent.StopSession()
        val fakeEvent: RumRawEvent = mock()
        whenever(
            mockChildScope.handleEvent(
                stopEvent,
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        ) doReturn mockChildScope
        whenever(
            mockChildScope.handleEvent(
                fakeEvent,
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        ) doReturn null

        // When
        val firstResult = testedScope.handleEvent(stopEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val secondResult = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(firstResult).isSameAs(testedScope)
        assertThat(secondResult).isNull()
    }

    @Test
    fun `M not display application W stopped`() {
        // When
        val result =
            testedScope.handleEvent(RumRawEvent.StopSession(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        assertThat(result).isNull()
        assertThat(testedScope.applicationDisplayed).isFalse
        assertThat(testedScope.childrenScopes).isEmpty()
    }

    @Test
    fun `M not start a new view W stopped { application not displayed }`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(RumRawEvent.StopSession(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val fakeEvent = forge.startViewEvent()

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).isEmpty()
    }

    @Test
    fun `M not start a new view W stopped { application displayed }`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = true
        testedScope.handleEvent(RumRawEvent.StopSession(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val fakeEvent = forge.startViewEvent()

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).isEmpty()
    }

    // endregion

    // region AddViewLoadingTime

    @Test
    fun `M send a warning log and api usage telemetry W handleEvent { AddViewLoadingTime, no view }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.addViewLoadingTimeEvent()
        testedScope.applicationDisplayed = true

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        mockInternalLogger.verifyApiUsage(
            InternalTelemetryEvent.ApiUsage.AddViewLoadingTime(
                overwrite = fakeEvent.overwrite,
                noView = true,
                noActiveView = false
            ),
            15f
        )
    }

    @Test
    fun `M send a warning log and api usage telemetry W handleEvent { AddViewLoadingTime, no active view }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.addViewLoadingTimeEvent()
        whenever(mockChildScope.isActive()) doReturn false
        testedScope.applicationDisplayed = true
        testedScope.childrenScopes.add(mockChildScope)

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        mockInternalLogger.verifyApiUsage(
            InternalTelemetryEvent.ApiUsage.AddViewLoadingTime(
                overwrite = fakeEvent.overwrite,
                noView = false,
                noActiveView = true
            ),
            15f
        )
    }

    // endregion

    @Test
    fun `M renew all the child scopes W renewViewScopes`(forge: Forge) {
        // Given
        val eventTime = Time()
        repeat(forge.aSmallInt()) {
            testedScope.handleEvent(
                forge.startViewEvent(eventTime),
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        }

        // When
        val oldScopes = testedScope.childrenScopes.toList()
        testedScope.renewViewScopes(eventTime)

        // Then
        assertThat(testedScope.childrenScopes).isNotEqualTo(oldScopes)
    }

    // region Active view

    @Test
    fun `M return current active view scope W activeView()`() {
        // Given
        whenever(mockChildScope.isActive()) doReturn true
        testedScope.childrenScopes.add(mockChildScope)

        // When
        val result = testedScope.activeView

        // Then
        assertThat(result).isSameAs(mockChildScope)
    }

    @Test
    fun `M return last known active view scope W activeView() { multiple active view scopes }`(
        forge: Forge
    ) {
        // Given
        val viewScopes = forge.aList {
            mock<RumViewScope>().apply {
                whenever(isActive()) doReturn aBool()
            }
        }
        assumeTrue(viewScopes.count { it.isActive() } > 1)
        testedScope.childrenScopes.addAll(viewScopes)

        // When
        val result = testedScope.activeView

        // Then
        assertThat(result).isSameAs(viewScopes.last { it.isActive() })
    }

    @Test
    fun `M log error W activeView() { multiple active view scopes }`(
        forge: Forge
    ) {
        // Given
        val viewScopes = forge.aList {
            mock<RumViewScope>().apply {
                whenever(isActive()) doReturn aBool()
            }
        }
        assumeTrue(viewScopes.count { it.isActive() } > 1)
        testedScope.childrenScopes.addAll(viewScopes)

        // When
        testedScope.activeView

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            RumViewManagerScope.MULTIPLE_ACTIVE_VIEWS_ERROR
        )
    }

    @Test
    fun `M return null W activeView() { no active view scopes }`() {
        // Given
        whenever(mockChildScope.isActive()) doReturn false
        testedScope.childrenScopes.add(mockChildScope)

        // When
        val result = testedScope.activeView

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W activeView() { not active scope }`() {
        // Given
        whenever(mockChildScope.isActive()) doReturn true
        testedScope.childrenScopes.add(mockChildScope)
        testedScope.stopped = true

        // When
        val result = testedScope.activeView

        // Then
        assertThat(result).isNull()
    }

    // endregion

    private fun resolveExpectedTimestamp(timestamp: Long): Long {
        return timestamp + fakeTime.serverTimeOffsetMs
    }
}
