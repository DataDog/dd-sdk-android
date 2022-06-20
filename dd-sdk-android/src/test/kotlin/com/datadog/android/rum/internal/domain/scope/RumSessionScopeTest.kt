/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.content.Context
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.NoOpDataWriter
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.offset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

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
    lateinit var mockDetector: FirstPartyHostDetector

    @Mock
    lateinit var mockCpuVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockMemoryVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockFrameRateVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockSessionListener: RumSessionListener

    @Mock
    lateinit var mockRumEventSourceProvider: RumEventSourceProvider

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeAndroidInfoProvider: AndroidInfoProvider

    @FloatForgery(min = 0f, max = 100f)
    var fakeSamplingRate: Float = 0f

    @BoolForgery
    var fakeBackgroundTrackingEnabled: Boolean = false

    lateinit var fakeInitialViewEvent: RumRawEvent

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeInitialViewEvent = forge.startViewEvent()

        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope

        initializeTestedScope()
    }

    @AfterEach
    fun `tear down`() {
        // whatever happens
        assertThat(testedScope.isActive()).isTrue()
    }

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
        for (i in 0..repeatCount) {
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
        for (i in 0..repeatCount) {
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
        for (i in 0..repeatCount) {
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
        for (i in 0..repeatCount) {
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
        for (i in 0..repeatCount) {
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

    // region Internal

    private fun initializeTestedScope(
        samplingRate: Float = 100f,
        withMockChildScope: Boolean = true,
        backgroundTrackingEnabled: Boolean? = null
    ) {
        testedScope = RumSessionScope(
            mockParentScope,
            samplingRate,
            backgroundTrackingEnabled ?: fakeBackgroundTrackingEnabled,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener,
            mockRumEventSourceProvider,
            mockBuildSdkVersionProvider,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS,
            fakeAndroidInfoProvider
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

        val appContext = ApplicationContextTestConfiguration(Context::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, appContext, coreFeature)
        }
    }
}
