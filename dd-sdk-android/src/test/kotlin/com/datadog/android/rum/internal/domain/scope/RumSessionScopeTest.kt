/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.content.Context
import android.os.Build
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.NoOpDataWriter
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
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
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumSessionScopeTest {

    lateinit var testedScope: RumSessionScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockChildScope: RumScope

    @Mock
    lateinit var mockEvent: RumRawEvent

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

    lateinit var mockDevLogHandler: LogHandler

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    @FloatForgery(min = 0f, max = 100f)
    var fakeSamplingRate: Float = 0f

    @BoolForgery
    var fakeBackgroundTrackingEnabled: Boolean = false

    @BeforeEach
    fun `set up`() {
        mockDevLogHandler = mockDevLogHandler()

        whenever(coreFeature.mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(coreFeature.mockNetworkInfoProvider.getLatestNetworkInfo())
            .thenReturn(fakeNetworkInfo)
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        testedScope = RumSessionScope(
            mockParentScope,
            100f,
            fakeBackgroundTrackingEnabled,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS
        )

        assertThat(GlobalRum.getRumContext()).isEqualTo(testedScope.getRumContext())
    }

    // region Session management

    @Test
    fun `updates sessionId if first call`() {
        val context = testedScope.getRumContext()

        assertThat(context.sessionId)
            .isNotEqualTo(RumContext.NULL_UUID)
            .isEqualTo(testedScope.sessionId)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `returns context with null IDs if session not kept`() {
        testedScope = RumSessionScope(
            mockParentScope,
            0f,
            fakeBackgroundTrackingEnabled,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS
        )
        val context = testedScope.getRumContext()

        assertThat(context.sessionId).isEqualTo(RumContext.NULL_UUID)
        assertThat(context.applicationId).isEqualTo(RumContext.NULL_UUID)
    }

    @Test
    fun `updates sessionId if last interaction too old`() {
        val firstSessionId = testedScope.getRumContext().sessionId

        Thread.sleep(TEST_INACTIVITY_MS)
        val context = testedScope.getRumContext()

        assertThat(context.sessionId)
            .isNotEqualTo(UUID(0, 0))
            .isNotEqualTo(firstSessionId)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `updates sessionId if duration is too long`() {
        val firstSessionId = testedScope.getRumContext().sessionId

        Thread.sleep(TEST_MAX_DURATION_MS)
        val context = testedScope.getRumContext()

        assertThat(context.sessionId)
            .isNotEqualTo(UUID(0, 0))
            .isNotEqualTo(firstSessionId)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `updates sessionId if duration is too long with updates`() {
        val repeatCount = (TEST_MAX_DURATION_MS / TEST_SLEEP_MS) + 1
        val firstSessionId = testedScope.getRumContext().sessionId

        for (i in 0..repeatCount) {
            Thread.sleep(TEST_SLEEP_MS)
            testedScope.handleEvent(mockEvent, mockWriter)
        }
        Thread.sleep(TEST_SLEEP_MS)
        val context = testedScope.getRumContext()

        assertThat(context.sessionId)
            .isNotEqualTo(UUID(0, 0))
            .isNotEqualTo(firstSessionId)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `updates sessionId on ResetSession event `() {
        val firstSessionId = testedScope.getRumContext().sessionId
        mockEvent = RumRawEvent.ResetSession()

        testedScope.handleEvent(mockEvent, mockWriter)
        val context = testedScope.getRumContext()

        assertThat(context.sessionId)
            .isNotEqualTo(UUID(0, 0))
            .isNotEqualTo(firstSessionId)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `keeps sessionId if last interaction is recent`() {
        val repeatCount = (TEST_INACTIVITY_MS / TEST_SLEEP_MS) + 1
        val firstSessionId = testedScope.getRumContext().sessionId

        for (i in 0..repeatCount) {
            Thread.sleep(TEST_SLEEP_MS)
            testedScope.handleEvent(mockEvent, mockWriter)
        }
        val context = testedScope.getRumContext()

        assertThat(context.sessionId).isEqualTo(firstSessionId)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.viewId).isEqualTo(fakeParentContext.viewId)
    }

    @Test
    fun `updates keepSession state based on sampling rate`() {
        testedScope = RumSessionScope(
            mockParentScope,
            fakeSamplingRate,
            fakeBackgroundTrackingEnabled,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS
        )
        var sessions = 0
        var sessionsKept = 0

        repeat(1024) {
            testedScope.handleEvent(RumRawEvent.ResetSession(), mockWriter)
            val context = testedScope.getRumContext()
            if (testedScope.keepSession) {
                assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
                assertThat(context.sessionId).isEqualTo(testedScope.sessionId)
            } else {
                assertThat(context.applicationId).isEqualTo(RumContext.NULL_UUID)
                assertThat(context.sessionId).isEqualTo(RumContext.NULL_UUID)
            }
            sessions++
            if (testedScope.keepSession) sessionsKept++
        }

        val actualRate = (sessionsKept.toFloat() * 100f) / sessions
        assertThat(actualRate).isCloseTo(fakeSamplingRate, Offset.offset(5f))
    }

    // endregion

    // region Listener

    @Test
    fun `𝕄 notify listener 𝕎 session is updated`() {
        // Given
        val firstSessionId = testedScope.getRumContext().sessionId
        val isFirstSessionDiscarded = !testedScope.keepSession

        // When
        Thread.sleep(TEST_INACTIVITY_MS)
        val context = testedScope.getRumContext()

        // Then
        assertThat(context.sessionId)
            .isNotEqualTo(UUID(0, 0))
            .isNotEqualTo(firstSessionId)
        verify(mockSessionListener).onSessionStarted(firstSessionId, isFirstSessionDiscarded)
        verify(mockSessionListener).onSessionStarted(context.sessionId, !testedScope.keepSession)
    }

    // endregion

    @Test
    fun `M log warning W handleEvent() without child scope`() {
        // Given
        Datadog.setVerbosity(Log.VERBOSE)

        // When
        val result = testedScope.handleEvent(mockEvent, mockWriter)

        // Then
        assertThat(testedScope.activeChildrenScopes).isEmpty()
        assertThat(result).isSameAs(testedScope)
        verify(mockDevLogHandler).handleLog(Log.WARN, RumSessionScope.MESSAGE_MISSING_VIEW)
        verifyNoMoreInteractions(mockDevLogHandler, mockWriter)
    }

    @Test
    fun `M delegate to child scope W handleEvent()`() {
        testedScope.activeChildrenScopes.add(mockChildScope)

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verify(mockChildScope).handleEvent(mockEvent, mockWriter)
        assertThat(testedScope.activeChildrenScopes).containsExactly(mockChildScope)
        assertThat(result).isSameAs(testedScope)
        verifyZeroInteractions(mockWriter, mockDevLogHandler)
    }

    @Test
    fun `M delegate to child scope with noop writer W handleEvent() and session not kept`() {
        testedScope = RumSessionScope(
            mockParentScope,
            0f,
            fakeBackgroundTrackingEnabled,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS
        )
        testedScope.activeChildrenScopes.add(mockChildScope)

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<DataWriter<Any>> {
            verify(mockChildScope).handleEvent(same(mockEvent), capture())

            assertThat(firstValue)
                .isNotSameAs(mockWriter)
                .isInstanceOf(NoOpDataWriter::class.java)
        }
        assertThat(testedScope.activeChildrenScopes).containsExactly(mockChildScope)
        assertThat(result).isSameAs(testedScope)
        verifyZeroInteractions(mockWriter, mockDevLogHandler)
    }

    @Test
    fun `M update child scope W handleEvent(StartView)`(
        @StringForgery key: String,
        @StringForgery name: String,
        forge: Forge
    ) {
        val attributes = forge.exhaustiveAttributes()
        mockEvent = RumRawEvent.StartView(key, name, attributes)

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        assertThat(testedScope.activeChildrenScopes).hasSize(1)
        val viewScope = testedScope.activeChildrenScopes.first() as RumViewScope
        assertThat(viewScope.keyRef.get()).isEqualTo(key)
        assertThat(viewScope.name).isEqualTo(name)
        assertThat(viewScope.attributes).isEqualTo(attributes)
        assertThat(viewScope.firstPartyHostDetector).isSameAs(mockDetector)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M update child scope W handleEvent(StartView) event with existing children`(
        @StringForgery key: String,
        @StringForgery name: String,
        forge: Forge
    ) {
        testedScope.activeChildrenScopes.add(mockChildScope)
        val attributes = forge.exhaustiveAttributes()
        mockEvent = RumRawEvent.StartView(key, name, attributes)

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        verify(mockChildScope).handleEvent(mockEvent, mockWriter)
        assertThat(testedScope.activeChildrenScopes).hasSize(2)
        val viewScope = testedScope.activeChildrenScopes.last() as RumViewScope
        assertThat(viewScope.keyRef.get()).isEqualTo(key)
        assertThat(viewScope.name).isEqualTo(name)
        assertThat(viewScope.attributes).containsAllEntriesOf(attributes)
        assertThat(viewScope.firstPartyHostDetector).isSameAs(mockDetector)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M keep children scope W handleEvent child returns non null`() {
        testedScope.activeChildrenScopes.add(mockChildScope)
        whenever(mockChildScope.handleEvent(mockEvent, mockWriter)) doReturn mockChildScope

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        assertThat(testedScope.activeChildrenScopes).containsExactly(mockChildScope)
        assertThat(result).isSameAs(testedScope)
        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `M remove children scope W handleEvent child returns null`() {
        testedScope.activeChildrenScopes.add(mockChildScope)
        whenever(mockChildScope.handleEvent(mockEvent, mockWriter)) doReturn null

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        assertThat(testedScope.activeChildrenScopes).isEmpty()
        assertThat(result).isSameAs(testedScope)
        verifyZeroInteractions(mockWriter)
    }

    @TestTargetApi(Build.VERSION_CODES.KITKAT)
    @Test
    fun `M send ApplicationStarted event W applicationDisplayed`(
        @StringForgery key: String,
        @StringForgery name: String
    ) {
        val childView: RumViewScope = mock()
        val startViewEvent = RumRawEvent.StartView(key, name, emptyMap())

        testedScope.onViewDisplayed(startViewEvent, childView, mockWriter)

        argumentCaptor<RumRawEvent> {
            verify(childView).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.ApplicationStarted
            assertThat(event.applicationStartupNanos).isEqualTo(Datadog.startupTimeNs)
        }
        verifyZeroInteractions(mockWriter)
    }

    @TestTargetApi(Build.VERSION_CODES.N)
    @Test
    fun `M send ApplicationStarted event W applicationDisplayed {API 24+}`(
        @StringForgery key: String,
        @StringForgery name: String
    ) {
        val childView: RumViewScope = mock()
        val startViewEvent = RumRawEvent.StartView(key, name, emptyMap())

        testedScope.onViewDisplayed(startViewEvent, childView, mockWriter)

        argumentCaptor<RumRawEvent> {
            verify(childView).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.ApplicationStarted
            assertThat(event.applicationStartupNanos)
                .isCloseTo(System.nanoTime(), Offset.offset(TimeUnit.MILLISECONDS.toNanos(100)))
        }
        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `M send ApplicationStarted event only once W applicationDisplayed`(
        @StringForgery key: String,
        @StringForgery name: String
    ) {
        val childView: RumViewScope = mock()
        val startViewEvent = RumRawEvent.StartView(key, name, emptyMap())

        testedScope.onViewDisplayed(startViewEvent, childView, mockWriter)
        testedScope.onViewDisplayed(startViewEvent, childView, mockWriter)

        argumentCaptor<RumRawEvent> {
            verify(childView).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.ApplicationStarted
            assertThat(event.applicationStartupNanos).isEqualTo(Datadog.startupTimeNs)
        }
        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `M send ApplicationStarted event W applicationDisplayed after ResetSession`(
        @StringForgery key: String,
        @StringForgery name: String
    ) {
        val childView: RumViewScope = mock()
        val startViewEvent = RumRawEvent.StartView(key, name, emptyMap())

        testedScope.onViewDisplayed(startViewEvent, childView, mockWriter)
        val resetNanos = System.nanoTime()
        testedScope.handleEvent(RumRawEvent.ResetSession(), mockWriter)
        testedScope.onViewDisplayed(startViewEvent, childView, mockWriter)

        argumentCaptor<RumRawEvent> {
            verify(childView, times(2)).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.ApplicationStarted
            assertThat(event.applicationStartupNanos).isEqualTo(Datadog.startupTimeNs)
            val event2 = lastValue as RumRawEvent.ApplicationStarted
            assertThat(event2.applicationStartupNanos).isGreaterThanOrEqualTo(resetNanos)
        }
        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `M do nothing W applicationDisplayed if session not kept`(
        @StringForgery key: String,
        @StringForgery name: String
    ) {
        testedScope = RumSessionScope(
            mockParentScope,
            0f,
            fakeBackgroundTrackingEnabled,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS
        )
        val startViewEvent = RumRawEvent.StartView(key, name, emptyMap())

        val result = testedScope.handleEvent(startViewEvent, mockWriter)

        assertThat(testedScope.activeChildrenScopes).isNotEmpty()
        assertThat(result).isSameAs(testedScope)
        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `M start a background ViewScope W handleEvent { app displayed, event is relevant }`(
        forge: Forge
    ) {
        // GIVEN
        testedScope = RumSessionScope(
            mockParentScope,
            100f,
            true,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS
        )
        testedScope.applicationDisplayed = true
        val fakeEvent = forge.forgeValidBackgroundEvent()

        // WHEN
        testedScope.handleEvent(fakeEvent, mockWriter)

        // THEN
        assertThat(testedScope.activeChildrenScopes).hasSize(1)
        assertThat(testedScope.activeChildrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp).isEqualTo(fakeEvent.eventTime.timestamp)
                assertThat(it.keyRef.get()).isEqualTo(RumSessionScope.RUM_BACKGROUND_VIEW_URL)
                assertThat(it.name).isEqualTo(RumSessionScope.RUM_BACKGROUND_VIEW_NAME)
                assertThat(it.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
            }
    }

    @Test
    fun `M start an AppLaunch ViewScope W handleEvent { app not displayed, event is relevant }`(
        forge: Forge
    ) {
        // GIVEN
        testedScope = RumSessionScope(
            mockParentScope,
            100f,
            true,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS
        )
        testedScope.applicationDisplayed = false
        val fakeEvent = forge.forgeValidAppLaunchEvent()

        // WHEN
        testedScope.handleEvent(fakeEvent, mockWriter)

        // THEN
        assertThat(testedScope.activeChildrenScopes).hasSize(1)
        assertThat(testedScope.activeChildrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp).isEqualTo(fakeEvent.eventTime.timestamp)
                assertThat(it.keyRef.get()).isEqualTo(RumSessionScope.RUM_APP_LAUNCH_VIEW_URL)
                assertThat(it.name).isEqualTo(RumSessionScope.RUM_APP_LAUNCH_VIEW_NAME)
                assertThat(it.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
            }
    }

    @Test
    fun `M ignore event W handleEvent { app displayed, event is relevant, background disabled }`(
        forge: Forge
    ) {
        // GIVEN
        testedScope = RumSessionScope(
            mockParentScope,
            100f,
            false,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS
        )
        val fakeEvent = forge.forgeInvalidAppLaunchEvent()

        // WHEN
        testedScope.handleEvent(fakeEvent, mockWriter)

        // THEN
        assertThat(testedScope.activeChildrenScopes).hasSize(0)
    }

    @Test
    fun `M send warn dev log W handleEvent { app displayed, event is relevant, bg disabled }`(
        forge: Forge
    ) {
        // GIVEN
        testedScope = RumSessionScope(
            mockParentScope,
            100f,
            false,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockTimeProvider,
            mockSessionListener,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS
        )
        testedScope.applicationDisplayed = true
        val fakeEvent = forge.forgeValidBackgroundEvent()

        // WHEN
        testedScope.handleEvent(fakeEvent, mockWriter)

        // THEN
        verify(mockDevLogHandler).handleLog(Log.WARN, RumSessionScope.MESSAGE_MISSING_VIEW)
    }

    @Test
    fun `M not start a background ViewScope W handleEvent { app displayed, event not relevant}`(
        forge: Forge
    ) {
        // GIVEN
        testedScope.applicationDisplayed = true
        val fakeEvent = forge.forgeInvalidBackgroundEvent()

        // WHEN
        testedScope.handleEvent(fakeEvent, mockWriter)

        // THEN
        assertThat(testedScope.activeChildrenScopes).hasSize(0)
    }

    @Test
    fun `M not start an appLaunch ViewScope W handleEvent { app not displayed, event not relevant}`(
        forge: Forge
    ) {
        // GIVEN
        testedScope.applicationDisplayed = false
        val fakeEvent = forge.forgeInvalidAppLaunchEvent()

        // WHEN
        testedScope.handleEvent(fakeEvent, mockWriter)

        // THEN
        assertThat(testedScope.activeChildrenScopes).hasSize(0)
    }

    @Test
    fun `M send warn dev log W handleEvent { app displayed, bg event not relevant}`(
        forge: Forge
    ) {
        // GIVEN
        testedScope.applicationDisplayed = true
        val fakeEvent = forge.forgeInvalidBackgroundEvent()

        // WHEN
        testedScope.handleEvent(fakeEvent, mockWriter)

        // THEN
        verify(mockDevLogHandler).handleLog(Log.WARN, RumSessionScope.MESSAGE_MISSING_VIEW)
    }

    @Test
    fun `M send warn dev log W handleEvent { app not displayed, app launch event not relevant}`(
        forge: Forge
    ) {
        // GIVEN
        testedScope.applicationDisplayed = false
        val fakeEvent = forge.forgeInvalidAppLaunchEvent()

        // WHEN
        testedScope.handleEvent(fakeEvent, mockWriter)

        // THEN
        verify(mockDevLogHandler).handleLog(Log.WARN, RumSessionScope.MESSAGE_MISSING_VIEW)
    }

    private fun Forge.forgeValidBackgroundEvent(): RumRawEvent {
        val fakeEventTime = Time()
        val fakeName = this.anAlphabeticalString()

        return this.anElementFrom(
            listOf(
                RumRawEvent.StartAction(
                    this.aValueFrom(RumActionType::class.java),
                    fakeName,
                    this.aBool(),
                    emptyMap(),
                    fakeEventTime
                ),
                RumRawEvent.AddError(
                    fakeName,
                    this.aValueFrom(RumErrorSource::class.java),
                    stacktrace = null,
                    throwable = null,
                    isFatal = this.aBool(),
                    attributes = emptyMap(),
                    eventTime = fakeEventTime
                ),
                RumRawEvent.StartResource(
                    fakeName,
                    getForgery<URL>().toString(),
                    anElementFrom("POST", "GET", "PUT", "DELETE", "HEAD"),
                    emptyMap(),
                    fakeEventTime
                )
            )
        )
    }

    private fun Forge.forgeValidAppLaunchEvent(): RumRawEvent {
        val fakeEventTime = Time()
        val fakeName = this.anAlphabeticalString()

        return this.anElementFrom(
            listOf(
                RumRawEvent.StartAction(
                    this.aValueFrom(RumActionType::class.java),
                    fakeName,
                    this.aBool(),
                    emptyMap(),
                    fakeEventTime
                ),
                RumRawEvent.AddLongTask(System.nanoTime(), fakeName, fakeEventTime),
                RumRawEvent.AddError(
                    fakeName,
                    this.aValueFrom(RumErrorSource::class.java),
                    stacktrace = null,
                    throwable = null,
                    isFatal = this.aBool(),
                    attributes = emptyMap(),
                    eventTime = fakeEventTime
                ),
                RumRawEvent.StartResource(
                    fakeName,
                    getForgery<URL>().toString(),
                    anElementFrom("POST", "GET", "PUT", "DELETE", "HEAD"),
                    emptyMap(),
                    fakeEventTime
                )
            )
        )
    }

    private fun Forge.forgeInvalidBackgroundEvent(): RumRawEvent {
        val fakeEventTime = Time()
        val fakeKey = anAlphabeticalString()
        val fakeName = anAlphabeticalString()

        return this.anElementFrom(
            listOf(
                RumRawEvent.AddLongTask(System.nanoTime(), fakeName, fakeEventTime),
                RumRawEvent.StopAction(
                    this.aValueFrom(RumActionType::class.java),
                    fakeKey,
                    emptyMap(),
                    fakeEventTime
                ),
                RumRawEvent.StopResource(
                    fakeKey,
                    statusCode = null,
                    size = null,
                    kind = this.aValueFrom(RumResourceKind::class.java),
                    attributes = emptyMap(),
                    eventTime = fakeEventTime
                ),
                RumRawEvent.StopResourceWithError(
                    fakeKey,
                    message = this.aString(),
                    statusCode = null,
                    source = this.aValueFrom(RumErrorSource::class.java),
                    throwable = this.getForgery(),
                    attributes = emptyMap(),
                    eventTime = fakeEventTime
                ),
                RumRawEvent.StopView(
                    fakeKey,
                    emptyMap(),
                    fakeEventTime
                )
            )
        )
    }

    private fun Forge.forgeInvalidAppLaunchEvent(): RumRawEvent {
        val fakeEventTime = Time()
        val fakeKey = this.anAlphabeticalString()

        return this.anElementFrom(
            listOf(
                RumRawEvent.StopAction(
                    this.aValueFrom(RumActionType::class.java),
                    fakeKey,
                    emptyMap(),
                    fakeEventTime
                ),
                RumRawEvent.StopResource(
                    fakeKey,
                    statusCode = null,
                    size = null,
                    kind = this.aValueFrom(RumResourceKind::class.java),
                    attributes = emptyMap(),
                    eventTime = fakeEventTime
                ),
                RumRawEvent.StopResourceWithError(
                    fakeKey,
                    message = this.aString(),
                    statusCode = null,
                    source = this.aValueFrom(RumErrorSource::class.java),
                    throwable = this.getForgery(),
                    attributes = emptyMap(),
                    eventTime = fakeEventTime
                ),
                RumRawEvent.StopView(
                    fakeKey,
                    emptyMap(),
                    fakeEventTime
                )
            )
        )
    }

    companion object {

        private const val TEST_SLEEP_MS = 50L
        private const val TEST_INACTIVITY_MS = TEST_SLEEP_MS * 3
        private const val TEST_MAX_DURATION_MS = TEST_SLEEP_MS * 10

        private val TEST_INACTIVITY_NS = TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS)
        private val TEST_MAX_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(TEST_MAX_DURATION_MS)

        val appContext = ApplicationContextTestConfiguration(Context::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, coreFeature)
        }
    }
}
