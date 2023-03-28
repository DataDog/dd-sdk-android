/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.api.context.TimeInfo
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumApplicationScopeTest {

    lateinit var testedScope: RumApplicationScope

    @Mock
    lateinit var mockChildScope: RumScope

    @Mock
    lateinit var mockEvent: RumRawEvent

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
    lateinit var mockContextProvider: ContextProvider

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @StringForgery(regex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    lateinit var fakeApplicationId: String

    @FloatForgery(min = 0f, max = 100f)
    var fakeSamplingRate: Float = 0f

    @BoolForgery
    var fakeBackgroundTrackingEnabled: Boolean = false

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    @Forgery
    lateinit var fakeTimeInfoAtScopeStart: TimeInfo

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.getFeature(RumFeature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockSdkCore.time) doReturn fakeTimeInfoAtScopeStart
        whenever(mockContextProvider.context) doReturn fakeDatadogContext

        testedScope = RumApplicationScope(
            fakeApplicationId,
            mockSdkCore,
            fakeSamplingRate,
            fakeBackgroundTrackingEnabled,
            fakeTrackFrustrations,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockSessionListener,
            mockContextProvider
        )
    }

    @Test
    fun `create child session scope with sampling rate`() {
        val childScopes = testedScope.childScopes

        assertThat(childScopes).hasSize(1)
        val childScope = childScopes.firstOrNull()
        check(childScope is RumSessionScope)
        assertThat(childScope.samplingRate).isEqualTo(fakeSamplingRate)
        assertThat(childScope.backgroundTrackingEnabled).isEqualTo(fakeBackgroundTrackingEnabled)
        assertThat(childScope.firstPartyHostHeaderTypeResolver).isSameAs(mockResolver)
    }

    @Test
    fun `always returns the same applicationId`() {
        val context = testedScope.getRumContext()

        assertThat(context.applicationId).isEqualTo(fakeApplicationId)
    }

    @Test
    fun `M return true W isActive()`() {
        val isActive = testedScope.isActive()

        assertThat(isActive).isTrue
    }

    @Test
    fun `delegates all events to child scope`() {
        testedScope.childScopes.clear()
        testedScope.childScopes.add(mockChildScope)

        testedScope.handleEvent(mockEvent, mockWriter)

        verify(mockChildScope).handleEvent(mockEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `M return active session W activeSession`() {
        // Then
        val activeSession = testedScope.activeSession
        assertThat(activeSession).isInstanceOf(RumSessionScope::class.java)
    }

    @Test
    fun `M have no active session W stopping current session`() {
        // When
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // Then
        val activeSession = testedScope.activeSession
        assertThat(activeSession).isNull()
    }

    @Test
    fun `M keep inactiveSession until completed W handleEvent`() {
        // Given
        val mockSession: RumSessionScope = mock()
        testedScope.childScopes.clear()
        testedScope.childScopes.add(mockSession)
        val stopEvent = RumRawEvent.StopSession()
        whenever(mockSession.handleEvent(stopEvent, mockWriter)) doReturn mockSession

        // When
        testedScope.handleEvent(stopEvent, mockWriter)

        // Then
        assertThat(testedScope.childScopes).isNotEmpty
        assertThat(testedScope.childScopes.first()).isEqualTo(mockSession)
    }

    @Test
    fun `M create a new session W handleEvent { no active sessions, user interaction } `(
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // Given
        val initialSession = testedScope.childScopes.first()
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)
        testedScope.handleEvent(
            RumRawEvent.StartView(
                key = viewKey,
                name = viewName,
                attributes = mapOf()
            ),
            mockWriter
        )

        // Then
        assertThat(testedScope.childScopes.count()).isEqualTo(1)
        val newSession = testedScope.childScopes.first()
        check(newSession is RumSessionScope)
        assertThat(newSession).isNotSameAs(initialSession)
        assertThat(newSession.samplingRate).isEqualTo(fakeSamplingRate)
        assertThat(newSession.backgroundTrackingEnabled).isEqualTo(fakeBackgroundTrackingEnabled)
        assertThat(newSession.firstPartyHostHeaderTypeResolver).isSameAs(mockResolver)
    }

    @Test
    fun `M create a new session with last known view W handleEvent { no active sessions, start action } `(
        forge: Forge,
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // Given
        val mockAttributes = forge.exhaustiveAttributes()
        testedScope.handleEvent(
            RumRawEvent.StartView(
                key = viewKey,
                name = viewName,
                attributes = mockAttributes
            ),
            mockWriter
        )
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // When
        testedScope.handleEvent(
            RumRawEvent.StartAction(
                type = RumActionType.TAP,
                name = "MockAction",
                waitForStop = false,
                attributes = mapOf()
            ),
            mockWriter
        )

        // Then
        val newSession = testedScope.activeSession
        check(newSession is RumSessionScope)
        val viewManager = newSession.childScope
        check(viewManager is RumViewManagerScope)
        assertThat(viewManager.childrenScopes).isNotEmpty
        val viewScope = viewManager.childrenScopes.first()
        check(viewScope is RumViewScope)
        assertThat(viewScope.keyRef.get()).isEqualTo(viewKey)
        assertThat(viewScope.name).isEqualTo(viewName)
        assertThat(viewScope.attributes).isEqualTo(mockAttributes)
    }

    @Test
    fun `M update feature context with no session W handleEvent { stop session }`(
        forge: Forge
    ) {
        // Given - Make sure a session has already started
        testedScope.handleEvent(
            RumRawEvent.StartView(
                key = forge.aString(),
                name = forge.aString(),
                attributes = mapOf()
            ),
            mockWriter
        )

        // When
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            // Feature context can be updated as many times as needed, we just want to verify it ends
            // in the right state.
            verify(mockSdkCore, atLeastOnce()).updateFeatureContext(eq(RumFeature.RUM_FEATURE_NAME), capture())

            val rumContext = mutableMapOf<String, Any?>()
            lastValue.invoke(rumContext)

            assertThat(rumContext["application_id"]).isEqualTo(fakeApplicationId)
            assertThat(rumContext["session_id"]).isEqualTo(RumContext.NULL_UUID)
            assertThat(rumContext["view_id"]).isNull()
        }
    }

    @Test
    fun `M update feature context with new session W startView { stopped session }`(
        forge: Forge
    ) {
        // Given - Make sure a session has already started
        testedScope.handleEvent(
            RumRawEvent.StartView(
                key = forge.aString(),
                name = forge.aString(),
                attributes = mapOf()
            ),
            mockWriter
        )
        val oldSession = (testedScope.activeSession as RumSessionScope).sessionId
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // When
        testedScope.handleEvent(
            RumRawEvent.StartView(
                key = forge.aString(),
                name = forge.aString(),
                attributes = mapOf()
            ),
            mockWriter
        )

        // Then
        val newSessionId = (testedScope.activeSession as RumSessionScope).sessionId
        assertThat(newSessionId).isNotEqualTo(RumContext.NULL_UUID)
        assertThat(newSessionId).isNotEqualTo(oldSession)
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            // Feature context can be updated as many times as needed, we just want to verify it ends
            // in the right state.
            verify(mockSdkCore, atLeastOnce()).updateFeatureContext(eq(RumFeature.RUM_FEATURE_NAME), capture())

            val rumContext = mutableMapOf<String, Any?>()
            lastValue.invoke(rumContext)

            assertThat(rumContext["application_id"]).isEqualTo(fakeApplicationId)
            assertThat(rumContext["session_id"]).isEqualTo(newSessionId)
            assertThat(rumContext["view_id"]).isNotNull
        }
    }
}
