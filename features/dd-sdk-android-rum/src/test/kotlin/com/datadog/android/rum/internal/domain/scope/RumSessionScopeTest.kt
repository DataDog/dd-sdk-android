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
import com.datadog.android.internal.profiling.ProfilerStopEvent
import com.datadog.android.internal.tests.stub.StubTimeProvider
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.accessibility.AccessibilitySnapshotManager
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.startup.RumAppStartupTelemetryReporter
import com.datadog.android.rum.internal.startup.RumSessionScopeStartupManager
import com.datadog.android.rum.internal.startup.RumStartupScenario
import com.datadog.android.rum.internal.startup.RumTTIDInfo
import com.datadog.android.rum.internal.startup.testRumStartupScenarios
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.model.RumVitalAppLaunchEvent
import com.datadog.android.rum.model.ViewEvent
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
    lateinit var mockAccessibilitySnapshotManager: AccessibilitySnapshotManager

    @Mock
    lateinit var mockBatteryInfoProvider: InfoProvider<BatteryInfo>

    @Mock
    lateinit var mockDisplayInfoProvider: InfoProvider<DisplayInfo>

    @Mock
    lateinit var mockRumAppStartupTelemetryReporter: RumAppStartupTelemetryReporter

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

    private var fakeVitalSource: RumVitalAppLaunchEvent.RumVitalAppLaunchEventSource? = null

    @Mock
    private lateinit var mockRumSessionScopeStartupManager: RumSessionScopeStartupManager

    private lateinit var stubTimeProvider: StubTimeProvider

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubTimeProvider = StubTimeProvider(elapsedTimeNs = TEST_INACTIVITY_NS + 1)
        fakeInitialViewEvent = forge.startViewEvent()

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

        fakeParentAttributes = forge.exhaustiveAttributes()
        whenever(mockParentScope.getCustomAttributes()) doReturn fakeParentAttributes

        val isValidSource = forge.aBool()

        val fakeSource = if (isValidSource) {
            forge.anElementFrom(
                ViewEvent.ViewEventSource.values().map { it.toJson().asString }
            )
        } else {
            forge.anAlphabeticalString()
        }

        fakeDatadogContext = fakeDatadogContext.copy(
            source = fakeSource
        )

        fakeVitalSource = if (isValidSource) {
            RumVitalAppLaunchEvent.RumVitalAppLaunchEventSource.fromJson(fakeSource)
        } else {
            null
        }

        initializeTestedScope()
    }

    // region childScope

    @Test
    fun `M have a ViewManager child scope W init() { with same sample rate }`() {
        // Given
        initializeTestedScope(fakeSampleRate, false)

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
        val mockEvent: RumRawEvent = mock()

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
        val mockEvent = mock<RumRawEvent>()

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
            testedScope.handleEvent(RumRawEvent.StopSession(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(result).isNull()
        assertThat(testedScope.isActive()).isFalse
    }

    @Test
    fun `M update context W handleEvent { StopSession }`() {
        // When
        val initialContext = testedScope.getRumContext()
        testedScope.handleEvent(RumRawEvent.StopSession(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        val context = testedScope.getRumContext()
        assertThat(context.applicationId).isEqualTo(initialContext.applicationId)
        assertThat(context.isSessionActive).isFalse
    }

    fun `M return scope from handleEvent W stopped { with active child scopes }`() {
        // Given
        whenever(
            mockChildScope.handleEvent(
                any(),
                fakeDatadogContext,
                mockEventWriteScope,
                mockWriter
            )
        ) doReturn mockChildScope

        // When
        val result =
            testedScope.handleEvent(RumRawEvent.StopSession(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.isActive()).isFalse
    }

    @Test
    fun `M return null from handleEvent W stopped { completed child scopes }`() {
        // Given
        val stopEvent = RumRawEvent.StopSession()
        val fakeEvent: RumRawEvent = mock()
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
        initializeTestedScope(100f)

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
        initializeTestedScope(0f)

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
    fun `M create new context W handleEvent(view)+getRumContext() {sampling = x}`(
        forge: Forge
    ) {
        var tracked = 0
        var untracked = 0
        var other = 0
        val knownSessionId = mutableSetOf<String>()

        repeat(1000) {
            // Given
            initializeTestedScope(fakeSampleRate)

            // When
            testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
            val context = testedScope.getRumContext()

            // Then
            assertThat(context.sessionId).isNotEqualTo(RumContext.NULL_UUID)
            assertThat(knownSessionId).doesNotContain(context.sessionId)
            knownSessionId.add(context.sessionId)
            when (context.sessionState) {
                RumSessionScope.State.NOT_TRACKED -> untracked++
                RumSessionScope.State.TRACKED -> tracked++
                RumSessionScope.State.EXPIRED -> other++
            }
        }

        assertThat(other).isZero()
        val sampledRate = tracked.toFloat() * 100f / (tracked + untracked).toFloat()
        // because sampling is random based we can't guarantee exactly 75%
        assertThat(sampledRate).isCloseTo(fakeSampleRate, offset(5f))
    }

    @Test
    fun `M create new session context W handleEvent(SdkInit)+getRumContext() {sampling = 100, foreground}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(100f)

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
        initializeTestedScope(100f, backgroundTrackingEnabled = true)

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
        initializeTestedScope(100f, backgroundTrackingEnabled = false)

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
        val result = testedScope.handleEvent(mock(), fakeDatadogContext, mockEventWriteScope, mockWriter)
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
        val result = testedScope.handleEvent(mock(), fakeDatadogContext, mockEventWriteScope, mockWriter)
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
        val result = testedScope.handleEvent(mock(), fakeDatadogContext, mockEventWriteScope, mockWriter)
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
        val result = testedScope.handleEvent(mock(), fakeDatadogContext, mockEventWriteScope, mockWriter)
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
    fun `M create new context W handleEvent(ResetSession)+getRumContext()`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        val result =
            testedScope.handleEvent(RumRawEvent.ResetSession(), fakeDatadogContext, mockEventWriteScope, mockWriter)
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
        val resetEvent = RumRawEvent.ResetSession()
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
        initializeTestedScope(0f)
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
        initializeTestedScope(0f)
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
        initializeTestedScope(0f)
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        val result =
            testedScope.handleEvent(RumRawEvent.ResetSession(), fakeDatadogContext, mockEventWriteScope, mockWriter)
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
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    testedScope.getRumContext().sessionId
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    testedScope.getRumContext().sessionId
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
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    testedScope.getRumContext().sessionId
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    testedScope.getRumContext().sessionId
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
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to firstSessionId
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to secondSessionId
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
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    firstSessionId
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    secondSessionId
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
        val resetEvent = RumRawEvent.ResetSession()
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
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    firstSessionId
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    secondSessionId
            )
        )
        assertThat(argumentCaptor.thirdValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to
                    secondSessionId
            )
        )
    }

    @Test
    fun `M notify Session Replay feature W session is updated {not tracked, timed out}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(0f)
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
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to firstSessionId
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to secondSessionId

            )
        )
    }

    @Test
    fun `M notify Session Replay feature W session is updated {not tracked, expired}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(0f)
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
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to firstSessionId
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to secondSessionId
            )
        )
    }

    @Test
    fun `M notify Session Replay feature W session is updated {not tracked, manual reset}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(0f)
        testedScope.handleEvent(forge.startViewEvent(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        val firstSessionId = testedScope.getRumContext().sessionId

        // When
        val resetEvent = RumRawEvent.ResetSession()
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
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to firstSessionId
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to secondSessionId
            )
        )
        assertThat(argumentCaptor.thirdValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false,
                RumSessionScope.RUM_SESSION_ID_BUS_MESSAGE_KEY to secondSessionId
            )
        )
    }

    @Test
    fun `M do nothing W session is updated {no SessionReplay feature registered}`(
        forge: Forge
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME))
            .thenReturn(null)

        // When
        initializeTestedScope(forge.aFloat())

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
            scenario = scenario
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

        val event = RumRawEvent.AppStartTTIDEvent(info = info)

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
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        verifyNoMoreInteractions(mockRumSessionScopeStartupManager)
    }

    @Test
    fun `M stop profiler W handleEvent { AppStartTTIDEvent, session not tracked }`(
        forge: Forge
    ) {
        // Given
        val mockProfilingFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)) doReturn mockProfilingFeatureScope
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
        testedScope.handleEvent(
            event = event,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter
        )

        // Then
        verify(mockProfilingFeatureScope).sendEvent(ProfilerStopEvent.TTID())
    }

    @Test
    fun `M call onTTFDEvent W handleEvent { AppStartTTFDEvent }`() {
        // Given
        val event = RumRawEvent.AppStartTTFDEvent()

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

    // endregion

    // region Internal

    private fun advanceTimeByMs(ms: Long) {
        stubTimeProvider.elapsedTimeNs += TimeUnit.MILLISECONDS.toNanos(ms)
    }

    private fun currentFakeTime(): Time {
        return Time(
            timestamp = stubTimeProvider.deviceTimestampMs,
            nanoTime = stubTimeProvider.elapsedTimeNs
        )
    }

    private fun initializeTestedScope(
        sampleRate: Float = 100f,
        withMockChildScope: Boolean = true,
        backgroundTrackingEnabled: Boolean? = null
    ) {
        testedScope = RumSessionScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            sampleRate = sampleRate,
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
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider,
            rumSessionScopeStartupManagerFactory = { mockRumSessionScopeStartupManager },
            insightsCollector = mockInsightsCollector
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
