/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.AppStartTimeProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.android.v2.core.internal.storage.NoOpDataWriter
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
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
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumSessionScopeTest {

    lateinit var testedScope: RumScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockChildScope: RumScope

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
    lateinit var mockAppStartTimeProvider: AppStartTimeProvider

    @Mock
    lateinit var mockContextProvider: ContextProvider

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockViewChangedListener: RumViewChangedListener

    @Forgery
    lateinit var fakeParentContext: RumContext

    @FloatForgery(min = 0f, max = 100f)
    var fakeSamplingRate: Float = 0f

    @BoolForgery
    var fakeBackgroundTrackingEnabled: Boolean = false

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    lateinit var fakeInitialViewEvent: RumRawEvent

    @Mock
    lateinit var mockSessionReplayFeatureScope: FeatureScope

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeInitialViewEvent = forge.startViewEvent()

        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        whenever(mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)) doReturn
            mockSessionReplayFeatureScope

        initializeTestedScope()
    }

    // region RUM Feature Context

    @Test
    fun `𝕄 update RUM feature context 𝕎 init()`() {
        // Given
        val expectedContext = testedScope.getRumContext()

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(mockSdkCore).updateFeatureContext(eq(Feature.RUM_FEATURE_NAME), capture())

            val rumContext = mutableMapOf<String, Any?>()
            lastValue.invoke(rumContext)

            assertThat(rumContext["application_id"]).isEqualTo(expectedContext.applicationId)
            assertThat(rumContext["session_id"]).isEqualTo(expectedContext.sessionId)
            assertThat(rumContext["session_state"]).isEqualTo(expectedContext.sessionState)
        }
    }

    // endregion

    // region childScope

    @Test
    fun `𝕄 have a ViewManager child scope 𝕎 init()`() {
        // Given
        initializeTestedScope(fakeSamplingRate, false)

        // When
        val childScope = (testedScope as RumSessionScope).childScope

        // Then
        assertThat(childScope).isInstanceOf(RumViewManagerScope::class.java)
    }

    @Test
    fun `𝕄 delegate events to child scope 𝕎 handleViewEvent() {TRACKED}`(
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
    fun `𝕄 delegate events to child scope 𝕎 handleViewEvent() {NOT TRACKED}`() {
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
    fun `𝕄 delegate events to child scope 𝕎 handleViewEvent() {EXPIRED}`() {
        // Given
        (testedScope as RumSessionScope).sessionState = RumSessionScope.State.EXPIRED
        val mockEvent: RumRawEvent = mock()

        // When
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        assertThat(result).isSameAs(testedScope)
        verify(mockChildScope).handleEvent(same(mockEvent), isA<NoOpDataWriter<Any>>())
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
    fun `𝕄 have empty session context 𝕎 init()+getRumContext()`() {
        // Given

        // When
        val context = testedScope.getRumContext()

        // Then
        assertThat(context.sessionId).isEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionState).isEqualTo(RumSessionScope.State.NOT_TRACKED)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `𝕄 create new session context 𝕎 handleEvent(view)+getRumContext() {sampling = 100}`(
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
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `𝕄 create new untracked context 𝕎 handleEvent(view)+getRumContext() {sampling = 0}`(
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
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `𝕄 create new context 𝕎 handleEvent(view)+getRumContext() {sampling = x}`(
        forge: Forge
    ) {
        var tracked = 0
        var untracked = 0
        var other = 0
        val knownSessionId = mutableSetOf<String>()

        repeat(1000) {
            // Given
            initializeTestedScope(fakeSamplingRate)

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
        assertThat(sampledRate).isCloseTo(fakeSamplingRate, offset(5f))
    }

    @Test
    fun `𝕄 keep session context 𝕎 handleEvent(non interactive) {before expiration}`(
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
    }

    @Test
    fun `𝕄 keep session context 𝕎 handleEvent(action) {before expiration}`(
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
    }

    @Test
    fun `𝕄 keep session context 𝕎 handleEvent(view) {before expiration}`(
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
    }

    @Test
    fun `𝕄 update context 𝕎 handleEvent(non interactive) {after expiration, background enabled}`(
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
    }

    @Test
    fun `𝕄 update context 𝕎 handleEvent(non interactive) {after expiration, background disabled}`(
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
    }

    @Test
    fun `𝕄 update context 𝕎 handleEvent(action) {after expiration}`(
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
    }

    @Test
    fun `𝕄 update context 𝕎 handleEvent(view) {after expiration}`(
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
    }

    @Test
    fun `𝕄 update context 𝕎 handleEvent(resource) {after expiration, background enabled}`(
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
    }

    @Test
    fun `𝕄 update context 𝕎 handleEvent(error) {after expiration, background enabled}`(
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
    }

    @Test
    fun `𝕄 update context 𝕎 handleEvent(resource) {after expiration, background disabled}`(
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
    }

    @Test
    fun `𝕄 update context 𝕎 handleEvent(error) {after expiration, background disabled}`(
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
    }

    @Test
    fun `𝕄 update context 𝕎 handleEvent(non interactive) {after timeout threshold}`(
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
    }

    @Test
    fun `𝕄 update context 𝕎 handleEvent(action) {after timeout threshold}`(
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
    }

    @Test
    fun `𝕄 update context 𝕎 handleEvent(view) {after timeout threshold}`(
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
    }

    @Test
    fun `𝕄 create new context 𝕎 handleEvent(ResetSession)+getRumContext()`(
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
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    // endregion

    // region Session Listener

    @Test
    fun `𝕄 notify listener 𝕎 session is updated {tracked, timed out}`(
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
        val result = testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
            .isNotEqualTo(initialContext.sessionId)
        verify(mockSessionListener).onSessionStarted(initialContext.sessionId, false)
        verify(mockSessionListener).onSessionStarted(context.sessionId, false)
    }

    @Test
    fun `𝕄 notify listener 𝕎 session is updated {tracked, expired}`(
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
            .isNotEqualTo(RumContext.NULL_UUID)
            .isNotEqualTo(initialContext.sessionId)
        verify(mockSessionListener).onSessionStarted(initialContext.sessionId, false)
        verify(mockSessionListener).onSessionStarted(context.sessionId, false)
    }

    @Test
    fun `𝕄 notify listener 𝕎 session is updated {tracked, manual reset}`(
        forge: Forge
    ) {
        // Given
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
        verify(mockSessionListener).onSessionStarted(initialContext.sessionId, false)
        verify(mockSessionListener).onSessionStarted(context.sessionId, false)
    }

    @Test
    fun `𝕄 notify listener 𝕎 session is updated {not tracked, timed out}`(
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
        val result = testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
            .isNotEqualTo(initialContext.sessionId)
        verify(mockSessionListener).onSessionStarted(initialContext.sessionId, true)
        verify(mockSessionListener).onSessionStarted(context.sessionId, true)
    }

    @Test
    fun `𝕄 notify listener 𝕎 session is updated {not tracked, expired}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(0f)
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val initialContext = testedScope.getRumContext()

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val result = testedScope.handleEvent(forge.startViewEvent(), mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
            .isNotEqualTo(initialContext.sessionId)
        verify(mockSessionListener).onSessionStarted(initialContext.sessionId, true)
        verify(mockSessionListener).onSessionStarted(context.sessionId, true)
    }

    @Test
    fun `𝕄 notify listener 𝕎 session is updated {not tracked, manual reset}`(
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
    fun `𝕄 notify Session Replay feature 𝕎 session is updated {tracked, timed out}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)

        // When
        Thread.sleep(TEST_MAX_DURATION_MS)
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)

        // Then
        val argumentCaptor = argumentCaptor<Any>()
        verify(mockSessionReplayFeatureScope, times(2))
            .sendEvent(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true
            )
        )
    }

    @Test
    fun `𝕄 notify Session Replay feature 𝕎 session is updated {tracked, expired}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)

        // Then
        val argumentCaptor = argumentCaptor<Any>()
        verify(mockSessionReplayFeatureScope, times(2))
            .sendEvent(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true
            )
        )
    }

    @Test
    fun `𝕄 notify Session Replay feature 𝕎 session is updated {tracked, manual reset}`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)

        // When
        testedScope.handleEvent(RumRawEvent.ResetSession(), mockWriter)
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)

        // Then
        val argumentCaptor = argumentCaptor<Any>()
        verify(mockSessionReplayFeatureScope, times(2))
            .sendEvent(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to true
            )
        )
    }

    @Test
    fun `𝕄 notify Session Replay feature 𝕎 session is updated {not tracked, timed out}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(0f)
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)

        // When
        Thread.sleep(TEST_MAX_DURATION_MS)
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)

        // Then
        val argumentCaptor = argumentCaptor<Any>()
        verify(mockSessionReplayFeatureScope, times(2))
            .sendEvent(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false
            )
        )
    }

    @Test
    fun `𝕄 notify Session Replay feature 𝕎 session is updated {not tracked, expired}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(0f)
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)

        // Then
        val argumentCaptor = argumentCaptor<Any>()
        verify(mockSessionReplayFeatureScope, times(2))
            .sendEvent(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false
            )
        )
    }

    @Test
    fun `𝕄 notify Session Replay feature 𝕎 session is updated {not tracked, manual reset}`(
        forge: Forge
    ) {
        // Given
        initializeTestedScope(0f)
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)

        // When
        testedScope.handleEvent(RumRawEvent.ResetSession(), mockWriter)
        testedScope.handleEvent(forge.startViewEvent(), mockWriter)

        // Then
        val argumentCaptor = argumentCaptor<Any>()
        verify(mockSessionReplayFeatureScope, times(2))
            .sendEvent(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false
            )
        )
        assertThat(argumentCaptor.secondValue).isEqualTo(
            mapOf(
                RumSessionScope.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                    RumSessionScope.RUM_SESSION_RENEWED_BUS_MESSAGE,
                RumSessionScope.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to false
            )
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 session is updated {no SessionReplay feature registered}`(
        forge: Forge
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME))
            .thenReturn(null)

        // When
        initializeTestedScope(forge.aFloat())

        // Then
        verifyZeroInteractions(mockSessionReplayFeatureScope)
    }

    // endregion

    // region Internal

    private fun initializeTestedScope(
        samplingRate: Float = 100f,
        withMockChildScope: Boolean = true,
        backgroundTrackingEnabled: Boolean? = null
    ) {
        testedScope = RumSessionScope(
            mockParentScope,
            mockSdkCore,
            samplingRate,
            backgroundTrackingEnabled ?: fakeBackgroundTrackingEnabled,
            fakeTrackFrustrations,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockSessionListener,
            mockContextProvider,
            false,
            mockAppStartTimeProvider,
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

        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
