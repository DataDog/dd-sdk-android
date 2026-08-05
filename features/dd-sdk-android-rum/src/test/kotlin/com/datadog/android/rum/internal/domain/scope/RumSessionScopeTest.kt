/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.app.Activity
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.NoOpDataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.core.sampling.DeterministicSampler
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.internal.sampling.SessionSamplingIdProvider
import com.datadog.android.internal.tests.stub.StubTimeProvider
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.configuration.RumViewEventWriteConfig
import com.datadog.android.rum.event.ViewEventMapper
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.accessibility.AccessibilityInfo
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.startup.RumSessionScopeStartupManager
import com.datadog.android.rum.internal.startup.RumStartupScenario
import com.datadog.android.rum.internal.startup.RumTTIDInfo
import com.datadog.android.rum.internal.startup.testRumStartupScenarios
import com.datadog.android.rum.internal.timeseries.NoOpTimeseriesCollectorFactory
import com.datadog.android.rum.internal.timeseries.TimeseriesCollector
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.model.VitalAppLaunchEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumSessionScopeTest {

    lateinit var testedScope: RumSessionScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockChildScope: RumViewManagerScope

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
    lateinit var mockAccessibilityInfoProvider: InfoProvider<AccessibilityInfo>

    @Mock
    lateinit var mockViewEventMapper: ViewEventMapper

    @Mock
    lateinit var mockBatteryInfoProvider: InfoProvider<BatteryInfo>

    @Mock
    lateinit var mockDisplayInfoProvider: InfoProvider<DisplayInfo>

    @Mock
    private lateinit var mockInsightsCollector: InsightsCollector

    @Mock
    lateinit var mockSessionListener: RumSessionListener

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockSessionEndedMetricDispatcher: SessionMetricDispatcher

    @Mock
    lateinit var mockViewChangedListener: RumViewChangedListener

    @Mock
    lateinit var mockSessionReplayFeatureScope: FeatureScope

    @Mock
    lateinit var mockNetworkSettledResourceIdentifier: InitialResourceIdentifier

    @Mock
    lateinit var mockLastInteractionIdentifier: LastInteractionIdentifier

    @Mock
    lateinit var mockSlowFramesListener: SlowFramesListener

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockSessionSampler: Sampler<String>

    @Forgery
    lateinit var fakeParentContext: RumContext

    @FloatForgery(min = 0f, max = 100f)
    var fakeSampleRate: Float = 0f

    @BoolForgery
    var fakeBackgroundTrackingEnabled: Boolean = false

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    @Forgery
    lateinit var fakeTimeInfo: TimeInfo

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    private lateinit var fakeInitialViewEvent: RumRawEvent

    private var fakeRumSessionType: RumSessionType? = null

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    lateinit var fakeParentAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeBatteryInfo: BatteryInfo

    @Forgery
    lateinit var fakeDisplayInfo: DisplayInfo

    @Forgery
    lateinit var fakeAccessibilityInfo: AccessibilityInfo

    private var fakeVitalSource: VitalAppLaunchEvent.VitalAppLaunchEventSource? = null

    @Mock
    private lateinit var mockRumSessionScopeStartupManager: RumSessionScopeStartupManager

    @Mock
    private lateinit var mockTimeseriesCollectorFactory: TimeseriesCollector.Factory

    @Mock
    private lateinit var mockTimeseriesCollector: TimeseriesCollector

    private lateinit var stubTimeProvider: StubTimeProvider

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubTimeProvider = StubTimeProvider(
            elapsedTimeNs = TEST_INACTIVITY_NS + 1,
            elapsedRealtimeNs = TEST_INACTIVITY_NS + 1
        )
        fakeInitialViewEvent = forge.startViewEvent()
        fakeParentContext = fakeParentContext.copy(viewType = RumViewType.NONE)

        whenever(mockParentScope.getRumContext()).doAnswer { fakeParentContext }
        whenever(mockChildScope.handleEvent(any(), any(), any(), any())) doReturn mockChildScope
        whenever(mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)) doReturn
            mockSessionReplayFeatureScope
        whenever(mockSdkCore.time) doReturn (fakeTimeInfo)
        whenever(mockSdkCore.internalLogger) doReturn mock()
        whenever(mockSdkCore.timeProvider) doReturn stubTimeProvider

        fakeRumSessionType = forge.aNullable { aValueFrom(RumSessionType::class.java) }

        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockWriter.write(eq(mockEventBatchWriter), any(), eq(EventType.DEFAULT))) doReturn true

        whenever(mockBatteryInfoProvider.getState()) doReturn fakeBatteryInfo
        whenever(mockDisplayInfoProvider.getState()) doReturn fakeDisplayInfo
        whenever(mockAccessibilityInfoProvider.getState()) doReturn fakeAccessibilityInfo
        whenever(mockSessionSampler.sample(any())).thenReturn(true)
        whenever(mockSessionSampler.getSampleRate()).thenReturn(100f)
        whenever(mockTimeseriesCollectorFactory.create(any(), any())) doReturn mockTimeseriesCollector

        fakeParentAttributes = forge.exhaustiveAttributes()
        whenever(mockParentScope.getCustomAttributes()) doReturn fakeParentAttributes

        val isValidSource = forge.aBool()

        val fakeSource = if (isValidSource) {
            forge.anElementFrom(
                ViewEvent.ViewEventSource.entries.map { it.toJson().asString }
            )
        } else {
            forge.anAlphabeticalString()
        }

        fakeDatadogContext = fakeDatadogContext.copy(
            source = fakeSource
        )

        fakeVitalSource = if (isValidSource) {
            VitalAppLaunchEvent.VitalAppLaunchEventSource.fromJson(fakeSource)
        } else {
            null
        }

        initializeTestedScope()
    }

    // region childScope

    @Test
    fun `M have a ViewManager child scope W init() { with same sample rate }`() {
        // Given
        whenever(mockSessionSampler.getSampleRate()).thenReturn(fakeSampleRate)
        initializeTestedScope(withMockChildScope = false)

        // When
        val childScope = testedScope.childScope

        // Then
        assertThat(childScope).isInstanceOf(RumViewManagerScope::class.java)
        assertThat(childScope?.sampleRate).isCloseTo(fakeSampleRate, offset(0.001f))
    }

    @Test
    fun `M delegate events to child scope W handleViewEvent() {TRACKED}`(
        forge: Forge
    ) {
        // Given
        testedScope.sessionState = RumSessionScope.State.TRACKED
        val event = forge.interactiveRumRawEvent()

        // When
        val result = testedScope.handleEvent(event, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(result).isSameAs(testedScope)
        verify(mockChildScope).handleEvent(event, fakeDatadogContext, mockEventWriteScope, mockWriter)
    }

    @Test
    fun `M delegate events to child scope W handleViewEvent() {NOT TRACKED}`() {
        // Given
        testedScope.sessionState = RumSessionScope.State.NOT_TRACKED
        val mockEvent: RumRawEvent = mock<RumRawEvent.WebViewEvent>()

        // When
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(result).isSameAs(testedScope)
        verify(mockChildScope).handleEvent(
            same(mockEvent),
            same(fakeDatadogContext),
            same(mockEventWriteScope),
            isA<NoOpDataWriter<Any>>()
        )
    }

    @Test
    fun `M delegate events to child scope W handleViewEvent() {EXPIRED}`() {
        // Given
        testedScope.sessionState = RumSessionScope.State.EXPIRED
        val mockEvent = RumRawEvent.WebViewEvent(eventTime = currentFakeTime())

        // When
        val result = testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(result).isSameAs(testedScope)
        verify(mockChildScope).handleEvent(
            same(mockEvent),
            same(fakeDatadogContext),
            same(mockEventWriteScope),
            isA<NoOpDataWriter<Any>>()
        )
    }

    @Test
    fun `M not send any event downstream W handleEvent(SdkInit)`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.sdkInitEvent()

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        verify(mockChildScope, never()).handleEvent(any(), any(), any(), any())
    }

    // endregion

    // region Stopping Sessions

    @Test
    fun `M set session active to false W handleEvent { StopSession }`() {
        // Given
        whenever(mockChildScope.handleEvent(any(), any(), any(), any())) doReturn null

        // When
        val result =
            testedScope.handleEvent(
                RumRawEvent.StopSession(eventTime = currentFakeTime()),
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )

        // Then
        assertThat(result).isNull()
        assertThat(testedScope.isActive()).isFalse
    }

    @Test
    fun `M update context W handleEvent { StopSession }`() {
        // When
        val initialContext = testedScope.getRumContext()
        testedScope.handleEvent(
            RumRawEvent.StopSession(eventTime = currentFakeTime()),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        val context = testedScope.getRumContext()
        assertThat(context.applicationId).isEqualTo(initialContext.applicationId)
        assertThat(context.isSessionActive).isFalse
    }

    @Test
    fun `M return session scope from handleEvent W stopped { with active child scopes }`() {
        // Given
        whenever(
            mockChildScope.handleEvent(
                any(),
                eq(fakeDatadogContext),
                eq(mockEventWriteScope),
                eq(mockWriter)
            )
        ) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopSession(eventTime = currentFakeTime()),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.isActive()).isFalse
    }

    @Test
    fun `M return null from handleEvent W stopped { completed child scopes }`() {
        // Given
        val stopEvent = RumRawEvent.StopSession(eventTime = currentFakeTime())
        val fakeEvent: RumRawEvent = RumRawEvent.WebViewEvent(eventTime = currentFakeTime())
        whenever(
            mockChildScope.handleEvent(
                eq(stopEvent),
                eq(fakeDatadogContext),
                eq(mockEventWriteScope),
                any()
            )
        ) doReturn mockChildScope
        whenever(
            mockChildScope.handleEvent(
                eq(fakeEvent),
                eq(fakeDatadogContext),
                eq(mockEventWriteScope),
                any()
            )
        ) doReturn null

        // When
        val firstResult = testedScope.handleEvent(stopEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val secondResult = testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(firstResult).isSameAs(testedScope)
        assertThat(secondResult).isNull()
    }

    // endregion

    // region getRumContext()

    @Test
    fun `M have empty session context W init()+getRumContext()`() {
        // Given

        // When
        val context = testedScope.getRumContext()

        // Then
        assertThat(context.sessionId).isEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.NOT_TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.USER_APP_LAUNCH)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `M create new session context W handleEvent(view)+getRumContext() {sampling = 100}`(
        forge: Forge
    ) {
        // Given
        whenever(mockSessionSampler.sample(any())).thenReturn(true)
        initializeTestedScope()

        // When
        val result =
            testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId).isNotEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.USER_APP_LAUNCH)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `M create new untracked context W handleEvent(view)+getRumContext() {sampling = 0}`(
        forge: Forge
    ) {
        // Given
        whenever(mockSessionSampler.sample(any())).thenReturn(false)
        initializeTestedScope()

        // When
        val result =
            testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId).isNotEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.NOT_TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.USER_APP_LAUNCH)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `M set TRACKED W renewSession() {sampler returns true}`(forge: Forge) {
        // Given
        whenever(mockSessionSampler.sample(any())).thenReturn(true)
        initializeTestedScope()

        // When
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
    }

    @Test
    fun `M set NOT_TRACKED W renewSession() {sampler returns false}`(forge: Forge) {
        // Given
        whenever(mockSessionSampler.sample(any())).thenReturn(false)
        initializeTestedScope()

        // When
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.sessionState).isEqualTo(RumSessionScope.State.NOT_TRACKED)
    }

    @Test
    fun `M set TRACKED W renewSession() {DeterministicSampler + SessionSamplingIdProvider, sampleRate=100}`(
        forge: Forge
    ) {
        // Given: real DeterministicSampler wired with SessionSamplingIdProvider at 100% rate
        val realSampler = DeterministicSampler(
            idConverter = SessionSamplingIdProvider::provideId,
            sampleRate = 100f
        )
        initializeTestedScope(sessionSampler = realSampler)

        // When
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then: any UUID-shaped session id is always kept at 100%
        assertThat(testedScope.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
    }

    @Test
    fun `M set NOT_TRACKED W renewSession() {DeterministicSampler + SessionSamplingIdProvider, sampleRate=0}`(
        forge: Forge
    ) {
        // Given: real DeterministicSampler wired with SessionSamplingIdProvider at 0% rate
        val realSampler = DeterministicSampler(
            idConverter = SessionSamplingIdProvider::provideId,
            sampleRate = 0f
        )
        initializeTestedScope(sessionSampler = realSampler)

        // When
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then: any UUID-shaped session id is always dropped at 0%
        assertThat(testedScope.sessionState).isEqualTo(RumSessionScope.State.NOT_TRACKED)
    }

    @Test
    fun `M create new session context W handleEvent(SdkInit)+getRumContext() {sampling = 100, foreground}`(
        forge: Forge
    ) {
        // Given
        whenever(mockSessionSampler.sample(any())).thenReturn(true)
        initializeTestedScope()

        // When
        val result = testedScope
            .handleEvent(
                forge.sdkInitEvent().copy(isAppInForeground = true),
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId).isNotEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.USER_APP_LAUNCH)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `M create new session context W handleEvent(SdkInit)+getRumContext(){sampling=100,background+enabled}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(backgroundTrackingEnabled = true)

        // When
        val result = testedScope
            .handleEvent(
                forge.sdkInitEvent().copy(isAppInForeground = false),
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId).isNotEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.BACKGROUND_LAUNCH)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `M not create new session context W handleEvent(SdkInit)+getRumContext(){sampling=100,background+disabled}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(backgroundTrackingEnabled = false)

        // When
        val result = testedScope
            .handleEvent(
                forge.sdkInitEvent().copy(isAppInForeground = false),
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId).isEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.EXPIRED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.USER_APP_LAUNCH)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `M keep session context W handleEvent(non interactive) {before expiration}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        val result = testedScope.handleEvent(
            mock<RumRawEvent.WebViewEvent>(),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId).isEqualTo(initialContext.sessionId)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.USER_APP_LAUNCH)
    }

    @Test
    fun `M keep session context W handleEvent(action) {before expiration}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        val result =
            testedScope.handleEvent(forge.startActionEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId).isEqualTo(initialContext.sessionId)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.USER_APP_LAUNCH)
    }

    @Test
    fun `M keep session context W handleEvent(view) {before expiration}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        val result =
            testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId).isEqualTo(initialContext.sessionId)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.USER_APP_LAUNCH)
    }

    @Test
    fun `M update context W handleEvent(non interactive) {after expiration, background enabled}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(backgroundTrackingEnabled = true)
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        advanceTimeByMs(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(
            mock<RumRawEvent.WebViewEvent>(),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId).isEqualTo(initialContext.sessionId)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.EXPIRED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.USER_APP_LAUNCH)
    }

    @Test
    fun `M update context W handleEvent(non interactive) {after expiration, background disabled}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(backgroundTrackingEnabled = false)
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        advanceTimeByMs(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(
            mock<RumRawEvent.WebViewEvent>(),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId).isEqualTo(initialContext.sessionId)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.EXPIRED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.USER_APP_LAUNCH)
    }

    @Test
    fun `M update context W handleEvent(action) {after expiration}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        advanceTimeByMs(TEST_INACTIVITY_MS)
        val result =
            testedScope.handleEvent(forge.startActionEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(initialContext.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.INACTIVITY_TIMEOUT)
    }

    @Test
    fun `M update context W handleEvent(view) {after expiration}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        advanceTimeByMs(TEST_INACTIVITY_MS)
        val result =
            testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(initialContext.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.INACTIVITY_TIMEOUT)
    }

    @Test
    fun `M update context W handleEvent(resource) {after expiration, background enabled}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(backgroundTrackingEnabled = true)
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        advanceTimeByMs(TEST_INACTIVITY_MS)
        val result =
            testedScope.handleEvent(forge.startResourceEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(initialContext.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.BACKGROUND_LAUNCH)
    }

    @Test
    fun `M update context W handleEvent(error) {after expiration, background enabled}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(backgroundTrackingEnabled = true)
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        advanceTimeByMs(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(forge.addErrorEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(initialContext.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.BACKGROUND_LAUNCH)
    }

    @Test
    fun `M update context W handleEvent(resource) {after expiration, background disabled}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(backgroundTrackingEnabled = false)
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        advanceTimeByMs(TEST_INACTIVITY_MS)
        val result =
            testedScope.handleEvent(forge.startResourceEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId).isEqualTo(initialContext.sessionId)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.EXPIRED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.USER_APP_LAUNCH)
    }

    @Test
    fun `M update context W handleEvent(error) {after expiration, background disabled}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(backgroundTrackingEnabled = false)
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        advanceTimeByMs(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(forge.addErrorEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId).isEqualTo(initialContext.sessionId)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.EXPIRED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.USER_APP_LAUNCH)
    }

    @Test
    fun `M update context W handleEvent(non interactive) {after timeout threshold}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(
            forge.startViewEvent(eventTime = currentFakeTime()),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val initialContext = testedScope.getRumContext()
        val repeatCount = (TEST_MAX_DURATION_MS / TEST_SLEEP_MS) + 1
        repeat(repeatCount.toInt()) {
            advanceTimeByMs(TEST_SLEEP_MS)
            testedScope.handleEvent(
                forge.startActionEvent(continuous = false, eventTime = currentFakeTime()),
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        }

        // When
        advanceTimeByMs(TEST_SLEEP_MS)
        val result = testedScope.handleEvent(
            mock<RumRawEvent.WebViewEvent>(),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(initialContext.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.MAX_DURATION)
    }

    @Test
    fun `M update context W handleEvent(action) {after timeout threshold}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(
            forge.startViewEvent(eventTime = currentFakeTime()),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val initialContext = testedScope.getRumContext()
        val repeatCount = (TEST_MAX_DURATION_MS / TEST_SLEEP_MS) + 1
        repeat(repeatCount.toInt()) {
            advanceTimeByMs(TEST_SLEEP_MS)
            testedScope.handleEvent(
                forge.startActionEvent(eventTime = currentFakeTime()),
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        }

        // When
        advanceTimeByMs(TEST_SLEEP_MS)
        val result = testedScope.handleEvent(
            forge.startActionEvent(eventTime = currentFakeTime()),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(initialContext.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.MAX_DURATION)
    }

    @Test
    fun `M update context W handleEvent(view) {after timeout threshold}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(
            forge.startViewEvent(eventTime = currentFakeTime()),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val initialContext = testedScope.getRumContext()
        val repeatCount = (TEST_MAX_DURATION_MS / TEST_SLEEP_MS) + 1
        repeat(repeatCount.toInt()) {
            advanceTimeByMs(TEST_SLEEP_MS)
            testedScope.handleEvent(
                forge.startActionEvent(eventTime = currentFakeTime()),
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        }

        // When
        advanceTimeByMs(TEST_SLEEP_MS)
        val result = testedScope.handleEvent(
            forge.startViewEvent(eventTime = currentFakeTime()),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(initialContext.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.MAX_DURATION)
    }

    @Test
    fun `M start new session W max duration exceeded in wall-clock but monotonic frozen by deep sleep`(
        forge: Forge
    ) {
        // Given — a tracked session is started
        testedScope.handleEvent(
            forge.startViewEvent(eventTime = currentFakeTime()),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val initialContext = testedScope.getRumContext()

        // The session runs awake for almost the full max duration, with frequent
        // interactions so that the inactivity threshold is NOT crossed during the
        // awake period.
        val awakeSteps = (TEST_MAX_DURATION_MS / TEST_SLEEP_MS).toInt() - 1
        repeat(awakeSteps) {
            advanceTimeByMs(TEST_SLEEP_MS)
            testedScope.handleEvent(
                forge.startActionEvent(continuous = false, eventTime = currentFakeTime()),
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        }

        // When — the device then deep-sleeps past the max duration in wall-clock time,
        //        but the monotonic clock the SDK previously read (getDeviceElapsedTimeNanos /
        //        System.nanoTime / CLOCK_MONOTONIC) is frozen during deep sleep. The sleep is
        //        short enough that the inactivity threshold is still NOT crossed (so the only
        //        condition that should fire is the max-duration timeout).
        //        See RUMS-6221: before the fix the SDK reused the old session id because the
        //        frozen monotonic clock never crossed the 4h threshold.
        simulateDeepSleepByMs(TEST_SLEEP_MS * 2)
        val result = testedScope.handleEvent(
            forge.startViewEvent(eventTime = currentFakeTime()),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val context = testedScope.getRumContext()

        // Then — a new session must be started with the MAX_DURATION reason, because the
        //         wall-clock (sleep-including) time exceeded the max duration while the
        //         inactivity threshold was not crossed.
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(initialContext.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.MAX_DURATION)
    }

    @Test
    fun `M start new session W inactivity exceeded in wall-clock but monotonic frozen by deep sleep`(
        forge: Forge
    ) {
        // Given — a tracked session is started
        testedScope.handleEvent(
            forge.startViewEvent(eventTime = currentFakeTime()),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val initialContext = testedScope.getRumContext()

        // When — the device sleeps for longer than the inactivity threshold in wall-clock
        //        time, but the monotonic clock the SDK reads is frozen during deep sleep.
        simulateDeepSleepByMs(TEST_INACTIVITY_MS + TEST_SLEEP_MS)
        val result = testedScope.handleEvent(
            forge.startViewEvent(eventTime = currentFakeTime()),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        val context = testedScope.getRumContext()

        // Then — a new session must be started after the wall-clock inactivity exceeded
        //         the inactivity threshold, regardless of the frozen monotonic clock.
        //         See RUMS-6221: currently this assertion FAILS because the SDK reuses the
        //         old session id (the defect under investigation).
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(initialContext.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.INACTIVITY_TIMEOUT)
    }

    @Test
    fun `M create new context W handleEvent(ResetSession)+getRumContext()`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        val result =
            testedScope.handleEvent(
                RumRawEvent.ResetSession(eventTime = currentFakeTime()),
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(initialContext.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.TRACKED)
        assertThat(context.sessionStartReason).isEqualTo(RumSessionScope.StartReason.EXPLICIT_STOP)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    // endregion

    // region Active View

    @Test
    fun `M return active view W activeView`() {
        // Given
        val mockViewScope = mock<RumViewScope>()
        whenever(mockChildScope.activeView) doReturn mockViewScope

        // When
        val result = testedScope.activeView

        // Then
        assertThat(result).isSameAs(mockViewScope)
    }

    @Test
    fun `M return null W activeView { no active view }`() {
        // Given
        whenever(mockChildScope.activeView) doReturn null

        // When
        val result = testedScope.activeView

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W activeView { child scope is null}`() {
        // Given
        testedScope.childScope = null

        // When
        val result = testedScope.activeView

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W activeView { session scope is not active }`() {
        // Given
        val mockViewScope = mock<RumViewScope>()
        whenever(mockChildScope.activeView) doReturn mockViewScope
        testedScope.isActive = false

        // When
        val result = testedScope.activeView

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region Session Listener

    @Test
    fun `M notify listener W session is updated {tracked, timed out}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()
        val repeatCount = (TEST_MAX_DURATION_MS / TEST_SLEEP_MS) + 1
        repeat(repeatCount.toInt()) {
            advanceTimeByMs(TEST_SLEEP_MS)
            testedScope.handleEvent(forge.startActionEvent(false), fakeDatadogContext, mockEventWriteScope, mockWriter)
        }

        // When
        advanceTimeByMs(TEST_MAX_DURATION_MS)
        val newEvent = forge.startViewEvent()
        val result = testedScope.handleEvent(newEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
            .isNotEqualTo(initialContext.sessionId)
        verify(mockChildScope).renewViewScopes(eventTime = newEvent.eventTime)
        verify(mockSessionListener).onSessionStarted(initialContext.sessionId, false)
        verify(mockSessionListener).onSessionStarted(context.sessionId, false)
    }

    @Test
    fun `M notify listener W session is updated {tracked, expired}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        advanceTimeByMs(TEST_INACTIVITY_MS)
        val newEvent = forge.startViewEvent()
        val result = testedScope.handleEvent(newEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
            .isNotEqualTo(initialContext.sessionId)
        verify(mockChildScope).renewViewScopes(eventTime = newEvent.eventTime)
        verify(mockSessionListener).onSessionStarted(initialContext.sessionId, false)
        verify(mockSessionListener).onSessionStarted(context.sessionId, false)
    }

    @Test
    fun `M notify listener W session is updated {tracked, manual reset}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        val resetEvent = RumRawEvent.ResetSession(eventTime = currentFakeTime())
        val result = testedScope.handleEvent(resetEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
            .isNotEqualTo(initialContext.sessionId)
        verify(mockChildScope).renewViewScopes(eventTime = resetEvent.eventTime)
        verify(mockSessionListener).onSessionStarted(initialContext.sessionId, false)
        verify(mockSessionListener).onSessionStarted(context.sessionId, false)
    }

    @Test
    fun `M notify listener W session is updated {not tracked, timed out}`(
        forge: Forge
    ) {
        // Given
        whenever(mockSessionSampler.sample(any())).thenReturn(false)
        initializeTestedScope()
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()
        val repeatCount = (TEST_MAX_DURATION_MS / TEST_SLEEP_MS) + 1
        repeat(repeatCount.toInt()) {
            advanceTimeByMs(TEST_SLEEP_MS)
            testedScope.handleEvent(forge.startActionEvent(false), fakeDatadogContext, mockEventWriteScope, mockWriter)
        }

        // When
        advanceTimeByMs(TEST_MAX_DURATION_MS)
        val newEvent = forge.startViewEvent()
        val result = testedScope.handleEvent(newEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
            .isNotEqualTo(initialContext.sessionId)
        verify(mockChildScope).renewViewScopes(eventTime = newEvent.eventTime)
        verify(mockSessionListener).onSessionStarted(initialContext.sessionId, true)
        verify(mockSessionListener).onSessionStarted(context.sessionId, true)
    }

    @Test
    fun `M notify listener W session is updated {not tracked, expired}`(
        forge: Forge
    ) {
        // Given
        whenever(mockSessionSampler.sample(any())).thenReturn(false)
        initializeTestedScope()
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        advanceTimeByMs(TEST_INACTIVITY_MS)
        val newEvent = forge.startViewEvent()
        val result = testedScope.handleEvent(newEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
            .isNotEqualTo(initialContext.sessionId)
        verify(mockChildScope).renewViewScopes(eventTime = newEvent.eventTime)
        verify(mockSessionListener).onSessionStarted(initialContext.sessionId, true)
        verify(mockSessionListener).onSessionStarted(context.sessionId, true)
    }

    @Test
    fun `M notify listener W session is updated {not tracked, manual reset}`(
        forge: Forge
    ) {
        // Given
        whenever(mockSessionSampler.sample(any())).thenReturn(false)
        initializeTestedScope()
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        val result =
            testedScope.handleEvent(
                RumRawEvent.ResetSession(eventTime = currentFakeTime()),
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
            .isNotEqualTo(initialContext.sessionId)
        verify(mockSessionListener).onSessionStarted(initialContext.sessionId, true)
        verify(mockSessionListener).onSessionStarted(context.sessionId, true)
    }

    // endregion

    // region Session Replay Event Bus

    @Test
    fun `M notify Session Replay feature W new interaction event received`(
        forge: Forge
    ) {
        // Given
        val fakeInteractionEvent1 = forge.interactiveRumRawEvent()
        val fakeInteractionEvent2 = forge.interactiveRumRawEvent()
        testedScope.handleEvent(fakeInteractionEvent1, fakeDatadogContext, mockEventWriteScope, mockWriter)
        testedScope.handleEvent(fakeInteractionEvent2, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        val argumentCaptor = argumentCaptor<Any>()
        verify(mockSessionReplayFeatureScope, times(2))
            .sendEvent(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    testedScope.getRumContext().sessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    testedScope.getRumContext().sessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
    }

    @Test
    fun `M notify Session Replay feature W new non-interaction event received`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(backgroundTrackingEnabled = false)
        val fakeNonInteractionEvent1 = forge.anyRumEvent(
            excluding = listOf(
                RumRawEvent.StartView::class,
                RumRawEvent.StartAction::class
            )
        )
        val fakeNonInteractionEvent2 = forge.anyRumEvent(
            excluding = listOf(
                RumRawEvent.StartView::class,
                RumRawEvent.StartAction::class
            )
        )
        testedScope.handleEvent(fakeNonInteractionEvent1, fakeDatadogContext, mockEventWriteScope, mockWriter)
        testedScope.handleEvent(fakeNonInteractionEvent2, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        val argumentCaptor = argumentCaptor<Any>()
        verify(mockSessionReplayFeatureScope, times(2))
            .sendEvent(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    testedScope.getRumContext().sessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    testedScope.getRumContext().sessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
    }

    @Test
    fun `M notify Session Replay feature W session is updated {tracked, timed out}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val firstSessionId = testedScope.getRumContext().sessionId

        // When
        advanceTimeByMs(TEST_MAX_DURATION_MS)
        val newEvent = forge.startViewEvent()
        testedScope.handleEvent(newEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val secondSessionId = testedScope.getRumContext().sessionId

        // Then
        val argumentCaptor = argumentCaptor<Any>()
        verify(mockSessionReplayFeatureScope, times(2))
            .sendEvent(argumentCaptor.capture())
        verify(mockChildScope).renewViewScopes(eventTime = newEvent.eventTime)
        assertThat(argumentCaptor.firstValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to firstSessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to secondSessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
    }

    @Test
    fun `M notify Session Replay feature W session is updated {tracked, expired}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val firstSessionId = testedScope.getRumContext().sessionId

        // When
        advanceTimeByMs(TEST_INACTIVITY_MS)
        val newEvent = forge.startViewEvent()
        testedScope.handleEvent(newEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val secondSessionId = testedScope.getRumContext().sessionId

        // Then
        val argumentCaptor = argumentCaptor<Any>()
        verify(mockSessionReplayFeatureScope, times(2))
            .sendEvent(argumentCaptor.capture())
        verify(mockChildScope).renewViewScopes(newEvent.eventTime)
        assertThat(argumentCaptor.firstValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    firstSessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    secondSessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
    }

    @Test
    fun `M notify Session Replay feature W session is updated {tracked, manual reset}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val firstSessionId = testedScope.getRumContext().sessionId

        // When
        val resetEvent = RumRawEvent.ResetSession(eventTime = currentFakeTime())
        testedScope.handleEvent(resetEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val secondSessionId = testedScope.getRumContext().sessionId

        // Then
        val argumentCaptor = argumentCaptor<Any>()
        verify(mockSessionReplayFeatureScope, times(3))
            .sendEvent(argumentCaptor.capture())
        verify(mockChildScope).renewViewScopes(resetEvent.eventTime)
        assertThat(argumentCaptor.firstValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    firstSessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    secondSessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
        assertThat(argumentCaptor.thirdValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    secondSessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
    }

    @Test
    fun `M notify Session Replay feature W session is updated {not tracked, timed out}`(
        forge: Forge
    ) {
        // Given
        whenever(mockSessionSampler.sample(any())).thenReturn(false)
        initializeTestedScope()
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val firstSessionId = testedScope.getRumContext().sessionId

        // When
        advanceTimeByMs(TEST_MAX_DURATION_MS)
        val newEvent = forge.startViewEvent()
        testedScope.handleEvent(newEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val secondSessionId = testedScope.getRumContext().sessionId

        // Then
        val argumentCaptor = argumentCaptor<Any>()
        verify(mockChildScope).renewViewScopes(newEvent.eventTime)
        verify(mockSessionReplayFeatureScope, times(2))
            .sendEvent(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to firstSessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to secondSessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
    }

    @Test
    fun `M notify Session Replay feature W session is updated {not tracked, expired}`(
        forge: Forge
    ) {
        // Given
        whenever(mockSessionSampler.sample(any())).thenReturn(false)
        initializeTestedScope()
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val firstSessionId = testedScope.getRumContext().sessionId

        // When
        advanceTimeByMs(TEST_INACTIVITY_MS)
        val newEvent = forge.startViewEvent()
        testedScope.handleEvent(newEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        val secondSessionId = testedScope.getRumContext().sessionId

        // Then
        val argumentCaptor = argumentCaptor<Any>()
        verify(mockSessionReplayFeatureScope, times(2))
            .sendEvent(argumentCaptor.capture())
        verify(mockChildScope).renewViewScopes(eventTime = newEvent.eventTime)
        assertThat(argumentCaptor.firstValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to firstSessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to secondSessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
    }

    @Test
    fun `M notify Session Replay feature W session is updated {not tracked, manual reset}`(
        forge: Forge
    ) {
        // Given
        whenever(mockSessionSampler.sample(any())).thenReturn(false)
        initializeTestedScope()
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val firstSessionId = testedScope.getRumContext().sessionId

        // When
        val resetEvent = RumRawEvent.ResetSession(eventTime = currentFakeTime())
        testedScope.handleEvent(resetEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val secondSessionId = testedScope.getRumContext().sessionId

        // Then
        val argumentCaptor = argumentCaptor<Any>()
        verify(mockSessionReplayFeatureScope, times(3))
            .sendEvent(argumentCaptor.capture())

        verify(mockChildScope).renewViewScopes(eventTime = resetEvent.eventTime)
        assertThat(argumentCaptor.firstValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to firstSessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to secondSessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
        assertThat(argumentCaptor.thirdValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to secondSessionId,
                RumSessionScope.RUM_SESSION_SAMPLE_RATE_BUS_MESSAGE_KEY to testedScope.sessionSampleRate,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                    (testedScope.sessionState == RumSessionScope.State.TRACKED)
            )
        )
    }

    @Test
    fun `M do nothing W session is updated {no SessionReplay feature registered}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME))
            .thenReturn(null)

        // When
        initializeTestedScope()

        // Then
        verifyNoInteractions(mockSessionReplayFeatureScope)
    }

    @ParameterizedTest
    @MethodSource("testScenarios")
    fun `M call onAppStartEvent W handleEvent { AppStartEvent }`(
        scenario: RumStartupScenario
    ) {
        // Given
        val event = RumRawEvent.AppStartEvent(
            scenario = scenario,
            eventTime = currentFakeTime()
        )

        testedScope.handleEvent(
            event = fakeInitialViewEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter
        )

        // When
        testedScope.handleEvent(
            event = event,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter
        )

        // Then
        verify(mockRumSessionScopeStartupManager).onAppStartEvent(event = eq(event))

        verifyNoMoreInteractions(mockRumSessionScopeStartupManager)
    }

    @ParameterizedTest
    @MethodSource("testScenarios")
    fun `M call onTTIDEvent W handleEvent { AppStartTTIDEvent }`(
        scenario: RumStartupScenario,
        forge: Forge
    ) {
        // Given
        val info = RumTTIDInfo(
            scenario = scenario,
            durationNs = forge.aLong(min = 0, max = 10000)
        )

        val event = RumRawEvent.AppStartTTIDEvent(info = info, eventTime = currentFakeTime())

        testedScope.handleEvent(
            event = fakeInitialViewEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter
        )

        // When
        val result = testedScope.handleEvent(
            event = event,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter
        )

        val rumContext = checkNotNull(result).getRumContext()

        // Then
        verify(mockRumSessionScopeStartupManager).onTTIDEvent(
            event = event,
            isSessionTracked = testedScope.sessionState == RumSessionScope.State.TRACKED,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        verifyNoMoreInteractions(mockRumSessionScopeStartupManager)
    }

    @Test
    fun `M call onTTIDEvent W handleEvent { AppStartTTIDEvent, session not tracked }`(
        forge: Forge
    ) {
        // Given
        val event = mock<RumRawEvent.AppStartTTIDEvent>()

        testedScope.handleEvent(
            event = fakeInitialViewEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter
        )
        testedScope.sessionState =
            forge.aValueFrom(RumSessionScope.State::class.java, exclude = listOf(RumSessionScope.State.TRACKED))

        // When
        val result = testedScope.handleEvent(
            event = event,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter
        )

        val rumContext = checkNotNull(result).getRumContext()

        // Then
        verify(mockRumSessionScopeStartupManager).onTTIDEvent(
            event = eq(event),
            isSessionTracked = eq(false),
            datadogContext = eq(fakeDatadogContext),
            writeScope = eq(mockEventWriteScope),
            writer = isA<NoOpDataWriter<Any>>(),
            rumContext = eq(rumContext),
            customAttributes = eq(fakeParentAttributes)
        )
        verifyNoMoreInteractions(mockRumSessionScopeStartupManager)
    }

    @Test
    fun `M call onTTFDEvent W handleEvent { AppStartTTFDEvent }`() {
        // Given
        val event = RumRawEvent.AppStartTTFDEvent(eventTime = currentFakeTime())

        testedScope.handleEvent(
            event = fakeInitialViewEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter
        )

        // When
        val result = testedScope.handleEvent(
            event = event,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter
        )

        val rumContext = checkNotNull(result).getRumContext()

        // Then
        verify(mockRumSessionScopeStartupManager).onTTFDEvent(
            event = event,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        verifyNoMoreInteractions(mockRumSessionScopeStartupManager)
    }

    @ParameterizedTest
    @MethodSource("testScenarios")
    fun `M record TTID and TTFD W handleEvent { session previously expired }`(
        scenario: RumStartupScenario,
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(
            event = fakeInitialViewEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter
        )
        advanceTimeByMs(TEST_INACTIVITY_MS)

        val appStartEvent = RumRawEvent.AppStartEvent(scenario = scenario, currentFakeTime())
        testedScope.handleEvent(
            event = appStartEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter
        )

        // When
        val info = RumTTIDInfo(scenario = scenario, durationNs = forge.aLong(min = 0, max = 10000))
        val ttidEvent = RumRawEvent.AppStartTTIDEvent(info = info, eventTime = currentFakeTime())
        testedScope.handleEvent(
            event = ttidEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter
        )

        val ttfdEvent = RumRawEvent.AppStartTTFDEvent(eventTime = currentFakeTime())
        val result = testedScope.handleEvent(
            event = ttfdEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter
        )
        val rumContext = checkNotNull(result).getRumContext()

        // Then
        verify(mockRumSessionScopeStartupManager).onAppStartEvent(event = eq(appStartEvent))
        verify(mockRumSessionScopeStartupManager).onTTIDEvent(
            event = ttidEvent,
            isSessionTracked = true,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )
        verify(mockRumSessionScopeStartupManager).onTTFDEvent(
            event = ttfdEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )
        verifyNoMoreInteractions(mockRumSessionScopeStartupManager)
    }

    // endregion

    // region Timeseries

    @Test
    fun `M start timeseries with session context W renewSession { keepSession = true, no active view }`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(timeseriesCollectorFactory = mockTimeseriesCollectorFactory)

        // When
        testedScope.handleEvent(
            forge.startViewEvent(),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        val rumContextCaptor = argumentCaptor<RumContext>()
        verify(mockTimeseriesCollectorFactory).create(any(), rumContextCaptor.capture())
        assertThat(rumContextCaptor.firstValue).isEqualTo(testedScope.getRumContext())
        verify(mockTimeseriesCollector).onSessionStart()
    }

    @Test
    fun `M pass active view context to factory W renewSession { foreground view active }`(forge: Forge) {
        // Given
        val mockViewScope = mock<RumViewScope>()
        val fakeViewContext = forge.getForgery<RumContext>().copy(viewType = RumViewType.FOREGROUND)
        whenever(mockChildScope.activeView) doReturn mockViewScope
        whenever(mockViewScope.getRumContext()) doReturn fakeViewContext
        initializeTestedScope(timeseriesCollectorFactory = mockTimeseriesCollectorFactory)

        // When
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then — the view context, not the session scope one (which has viewType NONE)
        val rumContextCaptor = argumentCaptor<RumContext>()
        verify(mockTimeseriesCollectorFactory).create(any(), rumContextCaptor.capture())
        assertThat(rumContextCaptor.firstValue).isEqualTo(fakeViewContext)
        assertThat(rumContextCaptor.firstValue).isNotEqualTo(testedScope.getRumContext())
    }

    @ParameterizedTest
    @MethodSource("sessionTypeResolutions")
    fun `M pass resolved session type to factory W renewSession`(
        fakeSyntheticsTestId: String?,
        fakeSyntheticsResultId: String?,
        fakeSessionTypeOverride: RumSessionType?,
        expectedSessionType: RumSessionType,
        forge: Forge
    ) {
        // Given
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeSyntheticsTestId,
            syntheticsResultId = fakeSyntheticsResultId
        )
        initializeTestedScope(
            timeseriesCollectorFactory = mockTimeseriesCollectorFactory,
            rumSessionTypeOverride = fakeSessionTypeOverride
        )

        // When
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        verify(mockTimeseriesCollectorFactory).create(eq(expectedSessionType), any())
    }

    @Test
    fun `M not start timeseries W renewSession { keepSession = false }`(forge: Forge) {
        // Given
        whenever(mockSessionSampler.sample(any())).thenReturn(false)
        initializeTestedScope(timeseriesCollectorFactory = mockTimeseriesCollectorFactory)

        // When
        testedScope.handleEvent(
            forge.startViewEvent(),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        verify(mockTimeseriesCollectorFactory, never()).create(any(), any())
        verify(mockTimeseriesCollector, never()).onSessionStart()
    }

    @Test
    fun `M stop previous and feed fresh timeseries W renewSession { another tracked session }`(forge: Forge) {
        // Given
        val mockViewScope = mock<RumViewScope>()
        val fakeViewContext = forge.getForgery<RumContext>().copy(viewType = RumViewType.FOREGROUND)
        whenever(mockChildScope.activeView) doReturn mockViewScope
        whenever(mockViewScope.getRumContext()) doReturn fakeViewContext
        initializeTestedScope(timeseriesCollectorFactory = mockTimeseriesCollectorFactory)
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val firstTimeseriesCollector = mockTimeseriesCollector
        val secondTimeseriesCollector: TimeseriesCollector = mock()
        whenever(mockTimeseriesCollectorFactory.create(any(), any())) doReturn secondTimeseriesCollector

        // When — second session via inactivity expiration
        advanceTimeByMs(TEST_INACTIVITY_MS)
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then — one timeseries per tracked session, updates go to the live one only
        verify(mockTimeseriesCollectorFactory, times(2)).create(any(), any())
        verify(firstTimeseriesCollector).onSessionStop()
        verify(firstTimeseriesCollector, times(1)).onRumContextUpdate(fakeViewContext)
        verify(secondTimeseriesCollector).onSessionStart()
        verify(secondTimeseriesCollector).onRumContextUpdate(fakeViewContext)
    }

    @Test
    fun `M stop timeseries W handleEvent { session expires without renewal }`(forge: Forge) {
        // Given
        initializeTestedScope(
            backgroundTrackingEnabled = false,
            timeseriesCollectorFactory = mockTimeseriesCollectorFactory
        )
        testedScope.handleEvent(
            forge.startViewEvent(),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // When
        advanceTimeByMs(TEST_INACTIVITY_MS)
        // A concrete subclass, not the sealed base: at jvmTarget 17 Kotlin emits RumRawEvent as a
        // real JVM sealed class, which Mockito cannot mock. WebViewEvent does not renew the
        // session, so the scope still expires, and eventTime is only read on renewal paths.
        testedScope.handleEvent(
            mock<RumRawEvent.WebViewEvent>(),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        assertThat(testedScope.sessionState).isEqualTo(RumSessionScope.State.EXPIRED)
        verify(mockTimeseriesCollector).onSessionStop()
    }

    @Test
    fun `M stop timeseries W handleEvent { StopSession }`(forge: Forge) {
        // Given
        initializeTestedScope(timeseriesCollectorFactory = mockTimeseriesCollectorFactory)
        testedScope.handleEvent(
            forge.startViewEvent(),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // When
        testedScope.handleEvent(
            RumRawEvent.StopSession(eventTime = currentFakeTime()),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        verify(mockTimeseriesCollector).onSessionStop()
    }

    @Test
    fun `M pass session context W onRumContextUpdate() { no active view }`(forge: Forge) {
        // Given — activeView is null by default (mockChildScope.activeView not stubbed)
        initializeTestedScope(timeseriesCollectorFactory = mockTimeseriesCollectorFactory)
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // When
        testedScope.handleEvent(forge.addErrorEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then — the session scope context, once per handled event
        val rumContextCaptor = argumentCaptor<RumContext>()
        verify(mockTimeseriesCollector, times(2)).onRumContextUpdate(rumContextCaptor.capture())
        assertThat(rumContextCaptor.allValues).containsOnly(testedScope.getRumContext())
    }

    @Test
    fun `M pass active view context W onRumContextUpdate() { StartView creates a foreground view }`(forge: Forge) {
        // Given — a real view manager child scope, so the context comes from an actual RumViewScope
        initializeTestedScope(
            withMockChildScope = false,
            timeseriesCollectorFactory = mockTimeseriesCollectorFactory
        )
        val fakeStartViewEvent = forge.startViewEvent()

        // When
        testedScope.handleEvent(
            fakeStartViewEvent,
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        val rumContextCaptor = argumentCaptor<RumContext>()
        verify(mockTimeseriesCollector).onRumContextUpdate(rumContextCaptor.capture())
        assertThat(rumContextCaptor.firstValue.viewType).isEqualTo(RumViewType.FOREGROUND)
        assertThat(rumContextCaptor.firstValue.viewName).isEqualTo(fakeStartViewEvent.key.name)
        assertThat(rumContextCaptor.firstValue.viewId).isNotNull
        assertThat(rumContextCaptor.firstValue.sessionId).isEqualTo(testedScope.sessionId)
    }

    @Test
    fun `M stop previous timeseries W handleEvent { ResetSession }`(forge: Forge) {
        // Given
        initializeTestedScope(timeseriesCollectorFactory = mockTimeseriesCollectorFactory)
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val firstTimeseriesCollector = mockTimeseriesCollector
        val secondTimeseriesCollector: TimeseriesCollector = mock()
        whenever(mockTimeseriesCollectorFactory.create(any(), any())) doReturn secondTimeseriesCollector

        // When
        testedScope.handleEvent(
            RumRawEvent.ResetSession(eventTime = currentFakeTime()),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        verify(firstTimeseriesCollector).onSessionStop()
        verify(secondTimeseriesCollector).onSessionStart()
    }

    @Test
    fun `M not create timeseries W handleEvent { StopSession, no tracked session }`() {
        // Given
        initializeTestedScope(timeseriesCollectorFactory = mockTimeseriesCollectorFactory)

        // When
        testedScope.handleEvent(
            RumRawEvent.StopSession(eventTime = currentFakeTime()),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        verify(mockTimeseriesCollectorFactory, never()).create(any(), any())
        verifyNoInteractions(mockTimeseriesCollector)
    }

    // endregion

    // region Internal

    private fun advanceTimeByMs(ms: Long) {
        stubTimeProvider.elapsedTimeNs += TimeUnit.MILLISECONDS.toNanos(ms)
        stubTimeProvider.elapsedRealtimeNs += TimeUnit.MILLISECONDS.toNanos(ms)
    }

    /**
     * Simulates device deep sleep: the wall-clock (elapsedRealtime / device timestamp)
     * advances by [ms], but the monotonic clock (System.nanoTime / CLOCK_MONOTONIC,
     * exposed via [TimeProvider.getDeviceElapsedTimeNanos]) is frozen — exactly as it is
     * on a real Android device in deep sleep.
     *
     * This is the scenario reported in RUMS-6221: the app stays inactive for several
     * hours, mostly in deep sleep, so the monotonic clock the SDK currently uses for
     * session timeout/inactivity never crosses the 4h / 15min thresholds even though
     * wall-clock time has long passed them.
     */
    private fun simulateDeepSleepByMs(ms: Long) {
        stubTimeProvider.elapsedRealtimeMs += ms
        stubTimeProvider.elapsedRealtimeNs += TimeUnit.MILLISECONDS.toNanos(ms)
        stubTimeProvider.deviceTimestampMs += ms
        // elapsedTimeNs intentionally NOT advanced — mirrors CLOCK_MONOTONIC during sleep
    }

    private fun currentFakeTime(): Time {
        return Time(
            timestamp = stubTimeProvider.deviceTimestampMs,
            nanoTime = stubTimeProvider.elapsedTimeNs
        )
    }

    private fun initializeTestedScope(
        sessionSampler: Sampler<String> = mockSessionSampler,
        withMockChildScope: Boolean = true,
        backgroundTrackingEnabled: Boolean? = null,
        timeseriesCollectorFactory: TimeseriesCollector.Factory = NoOpTimeseriesCollectorFactory(),
        rumSessionTypeOverride: RumSessionType? = fakeRumSessionType
    ) {
        testedScope = RumSessionScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            sessionSampler = sessionSampler,
            backgroundTrackingEnabled = backgroundTrackingEnabled ?: fakeBackgroundTrackingEnabled,
            trackFrustrations = fakeTrackFrustrations,
            viewChangedListener = mockViewChangedListener,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            sessionListener = mockSessionListener,
            applicationDisplayed = false,
            networkSettledResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            sessionInactivityNanos = TEST_INACTIVITY_NS,
            sessionMaxDurationNanos = TEST_MAX_DURATION_NS,
            rumSessionTypeOverride = rumSessionTypeOverride,
            accessibilityInfoProvider = mockAccessibilityInfoProvider,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider,
            rumSessionScopeStartupManagerFactory = { mockRumSessionScopeStartupManager },
            insightsCollector = mockInsightsCollector,
            viewEventMapper = mockViewEventMapper,
            rumViewEventWriteConfig = RumViewEventWriteConfig.FullViewOnlyAtStart,
            heatmapIdentifierRegistry = null,
            timeseriesCollectorFactory = timeseriesCollectorFactory
        )

        if (withMockChildScope) {
            testedScope.childScope = mockChildScope
        }
    }

    // endregion

    companion object {

        private const val TEST_SLEEP_MS = 50L
        private const val TEST_INACTIVITY_MS = TEST_SLEEP_MS * 3
        private const val TEST_MAX_DURATION_MS = TEST_SLEEP_MS * 10

        private val TEST_INACTIVITY_NS = TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS)
        private val TEST_MAX_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(TEST_MAX_DURATION_MS)

        private const val FAKE_SYNTHETICS_TEST_ID = "fake-synthetics-test-id"
        private const val FAKE_SYNTHETICS_RESULT_ID = "fake-synthetics-result-id"

        // syntheticsTestId, syntheticsResultId, rumSessionTypeOverride, expected session type
        @JvmStatic
        fun sessionTypeResolutions(): List<Arguments> = listOf(
            Arguments.of(null, null, null, RumSessionType.USER),
            Arguments.of(FAKE_SYNTHETICS_TEST_ID, FAKE_SYNTHETICS_RESULT_ID, null, RumSessionType.SYNTHETICS),
            Arguments.of(null, FAKE_SYNTHETICS_RESULT_ID, null, RumSessionType.USER),
            Arguments.of(FAKE_SYNTHETICS_TEST_ID, null, null, RumSessionType.USER),
            Arguments.of("  ", FAKE_SYNTHETICS_RESULT_ID, null, RumSessionType.USER),
            Arguments.of(FAKE_SYNTHETICS_TEST_ID, "  ", null, RumSessionType.USER),
            Arguments.of(
                FAKE_SYNTHETICS_TEST_ID,
                FAKE_SYNTHETICS_RESULT_ID,
                RumSessionType.USER,
                RumSessionType.USER
            ),
            Arguments.of(null, null, RumSessionType.SYNTHETICS, RumSessionType.SYNTHETICS)
        )

        @JvmStatic
        fun testScenarios(): List<RumStartupScenario> {
            val forge = Forge().apply {
                Configurator().configure(this)
            }

            val weakActivity = WeakReference(Mockito.mock<Activity>())

            return forge.testRumStartupScenarios(weakActivity)
        }
    }
}
