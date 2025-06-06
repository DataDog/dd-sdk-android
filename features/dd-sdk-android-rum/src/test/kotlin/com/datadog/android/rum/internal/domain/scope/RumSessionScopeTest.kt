/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.NoOpDataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.utils.forge.Configurator
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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumSessionScopeTest {

    lateinit var testedScope: RumScope

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
    lateinit var mockSessionListener: RumSessionListener

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockSessionEndedMetricDispatcher: SessionMetricDispatcher

    @Mock
    lateinit var mockViewChangedListener: RumViewChangedListener

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

    private lateinit var fakeInitialViewEvent: RumRawEvent

    @Mock
    lateinit var mockSessionReplayFeatureScope: FeatureScope

    @Mock
    lateinit var mockNetworkSettledResourceIdentifier: InitialResourceIdentifier

    @Mock
    lateinit var mockLastInteractionIdentifier: LastInteractionIdentifier

    @Mock
    lateinit var mockSlowFramesListener: SlowFramesListener

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeInitialViewEvent = forge.startViewEvent()

        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        whenever(mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)) doReturn
            mockSessionReplayFeatureScope
        whenever(mockSdkCore.time) doReturn (fakeTimeInfo)
        whenever(mockSdkCore.internalLogger) doReturn mock()

        initializeTestedScope()
    }

    // region RUM Feature Context

    @Test
    fun `M update RUM feature context W init()`() {
        // Given
        val expectedContext = testedScope.getRumContext()

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(mockSdkCore).updateFeatureContext(eq(Feature.RUM_FEATURE_NAME), capture())

            val rumContext = mutableMapOf<String, Any?>()
            lastValue.invoke(rumContext)

            assertThat(rumContext["application_id"]).isEqualTo(expectedContext.applicationId)
            assertThat(rumContext["session_id"]).isEqualTo(expectedContext.sessionId)
            assertThat(rumContext["session_state"]).isEqualTo(expectedContext.sessionState.asString)
        }
    }

    // endregion

    // region childScope

    @Test
    fun `M have a ViewManager child scope W init() { with same sample rate }`() {
        // Given
        initializeTestedScope(fakeSampleRate, false)

        // When
        val childScope = (testedScope as RumSessionScope).childScope

        // Then
        assertThat(childScope).isInstanceOf(RumViewManagerScope::class.java)
        assertThat(childScope?.sampleRate).isCloseTo(fakeSampleRate, offset(0.001f))
    }

    @Test
    fun `M delegate events to child scope W handleViewEvent() {TRACKED}`(
        forge: Forge
    ) {
        // Given
        (testedScope as RumSessionScope).sessionState = RumSessionScope.State.TRACKED
        val event = forge.interactiveRumRawEvent()

        // When
        val result = testedScope.handleEvent(event, mockWriter)

        // Then
        assertThat(result).isSameAs(testedScope)
        verify(mockChildScope).handleEvent(event, mockWriter)
    }

    @Test
    fun `M delegate events to child scope W handleViewEvent() {NOT TRACKED}`() {
        // Given
        (testedScope as RumSessionScope).sessionState = RumSessionScope.State.NOT_TRACKED
        val mockEvent: RumRawEvent = mock()

        // When
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        assertThat(result).isSameAs(testedScope)
        verify(mockChildScope).handleEvent(same(mockEvent), isA<NoOpDataWriter<Any>>())
    }

    @Test
    fun `M delegate events to child scope W handleViewEvent() {EXPIRED}`() {
        // Given
        (testedScope as RumSessionScope).sessionState = RumSessionScope.State.EXPIRED
        val mockEvent: RumRawEvent = mock()

        // When
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        assertThat(result).isSameAs(testedScope)
        verify(mockChildScope).handleEvent(same(mockEvent), isA<NoOpDataWriter<Any>>())
    }

    @Test
    fun `M not send any event downstream W handleEvent(SdkInit)`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.sdkInitEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).renewViewScopes(any())
        verifyNoMoreInteractions(mockChildScope)
    }

    // endregion

    // region Stopping Sessions

    @Test
    fun `M set session active to false W handleEvent { StopSession }`() {
        // Given
        whenever(mockChildScope.handleEvent(any(), any())) doReturn null

        // When
        val result = testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // Then
        assertThat(result).isNull()
        assertThat(testedScope.isActive()).isFalse
    }

    @Test
    fun `M update context W handleEvent { StopSession }`() {
        // When
        val initialContext = testedScope.getRumContext()
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // Then
        val context = testedScope.getRumContext()
        assertThat(context.applicationId).isEqualTo(initialContext.applicationId)
        assertThat(context.isSessionActive).isFalse
    }

    fun `M return scope from handleEvent W stopped { with active child scopes }`() {
        // Given
        whenever(mockChildScope.handleEvent(any(), mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.isActive()).isFalse
    }

    @Test
    fun `M return null from handleEvent W stopped { completed child scopes }`() {
        // Given
        val stopEvent = RumRawEvent.StopSession()
        val fakeEvent: RumRawEvent = mock()
        whenever(mockChildScope.handleEvent(eq(stopEvent), any())) doReturn mockChildScope
        whenever(mockChildScope.handleEvent(eq(fakeEvent), any())) doReturn null

        // When
        val firstResult = testedScope.handleEvent(stopEvent, mockWriter)
        val secondResult = testedScope.handleEvent(fakeEvent, mockWriter)

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
        val result = testedScope.handleEvent(forge.startViewEvent(), mockWriter)
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
        val result = testedScope.handleEvent(forge.startViewEvent(), mockWriter)
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
            testedScope.handleEvent(forge.startViewEvent(), mockWriter)
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
            .handleEvent(forge.sdkInitEvent().copy(isAppInForeground = true), mockWriter)
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
            .handleEvent(forge.sdkInitEvent().copy(isAppInForeground = false), mockWriter)
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
            .handleEvent(forge.sdkInitEvent().copy(isAppInForeground = false), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        val result = testedScope.handleEvent(mock(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        val result = testedScope.handleEvent(forge.startActionEvent(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        val result = testedScope.handleEvent(forge.startViewEvent(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(mock(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(mock(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(forge.startActionEvent(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(forge.startViewEvent(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(forge.startResourceEvent(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(forge.addErrorEvent(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(forge.startResourceEvent(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(forge.addErrorEvent(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()
        val repeatCount = (TEST_MAX_DURATION_MS / TEST_SLEEP_MS) + 1
        repeat(repeatCount.toInt()) {
            Thread.sleep(TEST_SLEEP_MS)
            testedScope.handleEvent(forge.startActionEvent(false), mockWriter)
        }

        // When
        Thread.sleep(TEST_SLEEP_MS)
        val result = testedScope.handleEvent(mock(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()
        val repeatCount = (TEST_MAX_DURATION_MS / TEST_SLEEP_MS) + 1
        repeat(repeatCount.toInt()) {
            Thread.sleep(TEST_SLEEP_MS)
            testedScope.handleEvent(forge.startActionEvent(), mockWriter)
        }

        // When
        Thread.sleep(TEST_SLEEP_MS)
        val result = testedScope.handleEvent(forge.startActionEvent(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()
        val repeatCount = (TEST_MAX_DURATION_MS / TEST_SLEEP_MS) + 1
        repeat(repeatCount.toInt()) {
            Thread.sleep(TEST_SLEEP_MS)
            testedScope.handleEvent(forge.startActionEvent(), mockWriter)
        }

        // When
        Thread.sleep(TEST_SLEEP_MS)
        val result = testedScope.handleEvent(forge.startViewEvent(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        val result = testedScope.handleEvent(RumRawEvent.ResetSession(), mockWriter)
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

    // region Session Listener

    @Test
    fun `M notify listener W session is updated {tracked, timed out}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()
        val repeatCount = (TEST_MAX_DURATION_MS / TEST_SLEEP_MS) + 1
        repeat(repeatCount.toInt()) {
            Thread.sleep(TEST_SLEEP_MS)
            testedScope.handleEvent(forge.startActionEvent(false), mockWriter)
        }

        // When
        Thread.sleep(TEST_MAX_DURATION_MS)
        val newEvent = forge.startViewEvent()
        val result = testedScope.handleEvent(newEvent, mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val newEvent = forge.startViewEvent()
        val result = testedScope.handleEvent(newEvent, mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        val resetEvent = RumRawEvent.ResetSession()
        val result = testedScope.handleEvent(resetEvent, mockWriter)
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()
        val repeatCount = (TEST_MAX_DURATION_MS / TEST_SLEEP_MS) + 1
        repeat(repeatCount.toInt()) {
            Thread.sleep(TEST_SLEEP_MS)
            testedScope.handleEvent(forge.startActionEvent(false), mockWriter)
        }

        // When
        Thread.sleep(TEST_MAX_DURATION_MS)
        val newEvent = forge.startViewEvent()
        val result = testedScope.handleEvent(newEvent, mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val newEvent = forge.startViewEvent()
        val result = testedScope.handleEvent(newEvent, mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        val result = testedScope.handleEvent(RumRawEvent.ResetSession(), mockWriter)
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
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
        testedScope.handleEvent(fakeInteractionEvent1, mockWriter)
        testedScope.handleEvent(fakeInteractionEvent2, mockWriter)

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
        testedScope.handleEvent(fakeNonInteractionEvent1, mockWriter)
        testedScope.handleEvent(fakeNonInteractionEvent2, mockWriter)

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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val firstSessionId = testedScope.getRumContext().sessionId

        // When
        Thread.sleep(TEST_MAX_DURATION_MS)
        val newEvent = forge.startViewEvent()
        testedScope.handleEvent(newEvent, mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val firstSessionId = testedScope.getRumContext().sessionId

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val newEvent = forge.startViewEvent()
        testedScope.handleEvent(newEvent, mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val firstSessionId = testedScope.getRumContext().sessionId

        // When
        val resetEvent = RumRawEvent.ResetSession()
        testedScope.handleEvent(resetEvent, mockWriter)

        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val firstSessionId = testedScope.getRumContext().sessionId

        // When
        Thread.sleep(TEST_MAX_DURATION_MS)
        val newEvent = forge.startViewEvent()
        testedScope.handleEvent(newEvent, mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val firstSessionId = testedScope.getRumContext().sessionId

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val newEvent = forge.startViewEvent()
        testedScope.handleEvent(newEvent, mockWriter)
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
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val firstSessionId = testedScope.getRumContext().sessionId

        // When
        val resetEvent = RumRawEvent.ResetSession()
        testedScope.handleEvent(resetEvent, mockWriter)
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
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

    // endregion

    // region Internal

    private fun initializeTestedScope(
        sampleRate: Float = 100f,
        withMockChildScope: Boolean = true,
        backgroundTrackingEnabled: Boolean? = null
    ) {
        testedScope = RumSessionScope(
            mockParentScope,
            mockSdkCore,
            mockSessionEndedMetricDispatcher,
            sampleRate,
            backgroundTrackingEnabled ?: fakeBackgroundTrackingEnabled,
            fakeTrackFrustrations,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockSessionListener,
            applicationDisplayed = false,
            networkSettledResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS
        )

        if (withMockChildScope) {
            (testedScope as RumSessionScope).childScope = mockChildScope
        }
    }

    // endregion

    companion object {

        private const val TEST_SLEEP_MS = 50L
        private const val TEST_INACTIVITY_MS = TEST_SLEEP_MS * 3
        private const val TEST_MAX_DURATION_MS = TEST_SLEEP_MS * 10

        private val TEST_INACTIVITY_NS = TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS)
        private val TEST_MAX_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(TEST_MAX_DURATION_MS)
    }
}
