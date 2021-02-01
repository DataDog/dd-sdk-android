/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.os.Build
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.data.NoOpWriter
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.android.utils.mockCoreFeature
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
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
    ExtendWith(ApiLevelExtension::class)
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
    lateinit var mockWriter: Writer<RumEvent>

    @Mock
    lateinit var mockDetector: FirstPartyHostDetector

    lateinit var mockDevLogHandler: LogHandler

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @FloatForgery(min = 0f, max = 100f)
    var fakeSamplingRate: Float = 0f

    @BeforeEach
    fun `set up`() {
        mockDevLogHandler = mockDevLogHandler()

        mockCoreFeature()
        whenever(CoreFeature.userInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        testedScope = RumSessionScope(
            mockParentScope,
            100f,
            mockDetector,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS
        )

        assertThat(GlobalRum.getRumContext()).isEqualTo(testedScope.getRumContext())
    }

    @AfterEach
    fun `tear down`() {
        CoreFeature.stop()
    }

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
            mockDetector,
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
            mockDetector,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS
        )
        var sessions = 0
        var sessionsKept = 0

        repeat(512) {
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
            mockDetector,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS
        )
        testedScope.activeChildrenScopes.add(mockChildScope)

        val result = testedScope.handleEvent(mockEvent, mockWriter)

        argumentCaptor<Writer<RumEvent>> {
            verify(mockChildScope).handleEvent(same(mockEvent), capture())

            assertThat(firstValue)
                .isNotSameAs(mockWriter)
                .isInstanceOf(NoOpWriter::class.java)
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

        testedScope.onApplicationDisplayed(startViewEvent, childView, mockWriter)

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

        testedScope.onApplicationDisplayed(startViewEvent, childView, mockWriter)

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

        testedScope.onApplicationDisplayed(startViewEvent, childView, mockWriter)
        testedScope.onApplicationDisplayed(startViewEvent, childView, mockWriter)

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

        testedScope.onApplicationDisplayed(startViewEvent, childView, mockWriter)
        val resetNanos = System.nanoTime()
        testedScope.handleEvent(RumRawEvent.ResetSession(), mockWriter)
        testedScope.onApplicationDisplayed(startViewEvent, childView, mockWriter)

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
            mockDetector,
            TEST_INACTIVITY_NS,
            TEST_MAX_DURATION_NS
        )
        val startViewEvent = RumRawEvent.StartView(key, name, emptyMap())

        val result = testedScope.handleEvent(startViewEvent, mockWriter)

        assertThat(testedScope.activeChildrenScopes).isNotEmpty()
        assertThat(result).isSameAs(testedScope)
        verifyZeroInteractions(mockWriter)
    }

    companion object {

        private const val TEST_SLEEP_MS = 200L
        private const val TEST_INACTIVITY_MS = TEST_SLEEP_MS * 3
        private const val TEST_MAX_DURATION_MS = TEST_SLEEP_MS * 10

        private val TEST_INACTIVITY_NS = TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS)
        private val TEST_MAX_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(TEST_MAX_DURATION_MS)
    }
}
