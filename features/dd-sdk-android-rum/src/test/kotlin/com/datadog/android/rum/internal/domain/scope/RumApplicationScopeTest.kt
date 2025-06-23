/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.app.ActivityManager
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.state.ViewUIPerformanceReport
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.forge.exhaustiveAttributes
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockDispatcher: SessionMetricDispatcher

    @StringForgery(regex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    lateinit var fakeApplicationId: String

    @FloatForgery(min = 0f, max = 100f)
    var fakeSampleRate: Float = 0f

    @BoolForgery
    var fakeBackgroundTrackingEnabled: Boolean = false

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    @Forgery
    lateinit var fakeTimeInfoAtScopeStart: TimeInfo

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Mock
    lateinit var mockNetworkSettledResourceIdentifier: InitialResourceIdentifier

    @Mock
    lateinit var mockLastInteractionIdentifier: LastInteractionIdentifier

    @Forgery
    lateinit var viewUIPerformanceReport: ViewUIPerformanceReport

    @Mock
    lateinit var mockSlowFramesListener: SlowFramesListener

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockSdkCore.time) doReturn fakeTimeInfoAtScopeStart
        whenever(mockSdkCore.internalLogger) doReturn mock()
        whenever(mockSlowFramesListener.resolveReport(any(), any(), any())) doReturn viewUIPerformanceReport

        testedScope = RumApplicationScope(
            fakeApplicationId,
            mockSdkCore,
            fakeSampleRate,
            fakeBackgroundTrackingEnabled,
            fakeTrackFrustrations,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockDispatcher,
            mockSessionListener,
            mockNetworkSettledResourceIdentifier,
            mockLastInteractionIdentifier,
            mockSlowFramesListener
        )
    }

    @Test
    fun `create child session scope with sample rate`() {
        val childScopes = testedScope.childScopes

        assertThat(childScopes).hasSize(1)
        val childScope = childScopes.firstOrNull()
        check(childScope is RumSessionScope)
        assertThat(childScope.sampleRate).isEqualTo(fakeSampleRate)
        assertThat(childScope.backgroundTrackingEnabled).isEqualTo(fakeBackgroundTrackingEnabled)
        assertThat(childScope.firstPartyHostHeaderTypeResolver).isSameAs(mockResolver)
    }

    @Test
    fun `M always return the same applicationId W getRumContext()`() {
        val context = testedScope.getRumContext()

        assertThat(context.applicationId).isEqualTo(fakeApplicationId)
    }

    @Test
    fun `M return null synthetics info W getRumContext()`() {
        val context = testedScope.getRumContext()

        assertThat(context.syntheticsTestId).isNull()
        assertThat(context.syntheticsResultId).isNull()
    }

    @Test
    fun `M return synthetics test attributes W handleEvent() + getRumContext()`(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String
    ) {
        // Given
        val event = RumRawEvent.SetSyntheticsTestAttribute(fakeTestId, fakeResultId)

        // When
        val result = testedScope.handleEvent(event, mockWriter)
        val context = testedScope.getRumContext()

        // Then
        assertThat(result).isSameAs(testedScope)
        assertThat(context.syntheticsTestId).isEqualTo(fakeTestId)
        assertThat(context.syntheticsResultId).isEqualTo(fakeResultId)
        verifyNoInteractions(mockWriter)
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
        verifyNoInteractions(mockWriter)
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
        whenever(mockSession.handleEvent(any(), eq(mockWriter))) doReturn mockSession

        // When
        testedScope.handleEvent(stopEvent, mockWriter)

        // Then
        assertThat(testedScope.childScopes).isNotEmpty
        assertThat(testedScope.childScopes.first()).isEqualTo(mockSession)
    }

    @Test
    fun `M create a new session W handleEvent { no active sessions, start view } `(
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // Given
        val initialSession = testedScope.childScopes.first()
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)
        testedScope.handleEvent(
            RumRawEvent.StartView(
                key = RumScopeKey.from(viewKey, viewName),
                attributes = mapOf()
            ),
            mockWriter
        )

        // Then
        assertThat(testedScope.childScopes).hasSize(1)
        val newSession = testedScope.childScopes.first()
        check(newSession is RumSessionScope)
        assertThat(newSession).isNotSameAs(initialSession)
        assertThat(newSession.sampleRate).isEqualTo(fakeSampleRate)
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
        val fakeKey = RumScopeKey.from(viewKey, viewName)
        testedScope.handleEvent(
            RumRawEvent.StartView(
                key = fakeKey,
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
        assertThat(viewScope.key).isEqualTo(fakeKey)
        assertThat(viewScope.eventAttributes).isEqualTo(mockAttributes)
    }

    @Test
    fun `M update feature context with no session W handleEvent { stop session }`(
        forge: Forge
    ) {
        // Given - Make sure a session has already started
        testedScope.handleEvent(
            RumRawEvent.StartView(
                key = RumScopeKey.from(forge.aString()),
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
            verify(mockSdkCore, atLeastOnce())
                .updateFeatureContext(eq(Feature.RUM_FEATURE_NAME), capture())

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
                key = RumScopeKey.from(forge.aString()),
                attributes = mapOf()
            ),
            mockWriter
        )
        val oldSession = (testedScope.activeSession as RumSessionScope).sessionId
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // When
        testedScope.handleEvent(
            RumRawEvent.StartView(
                key = RumScopeKey.from(forge.aString()),
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
            verify(mockSdkCore, atLeastOnce())
                .updateFeatureContext(eq(Feature.RUM_FEATURE_NAME), capture())

            val rumContext = mutableMapOf<String, Any?>()
            lastValue.invoke(rumContext)

            assertThat(rumContext["application_id"]).isEqualTo(fakeApplicationId)
            assertThat(rumContext["session_id"]).isEqualTo(newSessionId)
            assertThat(rumContext["view_id"]).isNotNull
        }
    }

    @Test
    fun `M send ApplicationStarted event once W handleEvent { app is in foreground }`(
        forge: Forge
    ) {
        // Given
        val fakeEvents = forge.aList {
            forge.anyRumEvent(
                excluding = listOf(
                    RumRawEvent.ApplicationStarted::class,
                    RumRawEvent.SdkInit::class
                )
            )
        }
        val firstEvent = fakeEvents.first()
        val appStartTimeNs = forge.aLong(min = 0, max = fakeEvents.first().eventTime.nanoTime)
        whenever(mockSdkCore.appStartTimeNs) doReturn appStartTimeNs
        DdRumContentProvider.processImportance =
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        val mockSessionScope = mock<RumScope>()
        testedScope.childScopes.clear()
        testedScope.childScopes += mockSessionScope

        val expectedEventTimestamp =
            TimeUnit.NANOSECONDS.toMillis(
                TimeUnit.MILLISECONDS.toNanos(firstEvent.eventTime.timestamp) -
                    firstEvent.eventTime.nanoTime + appStartTimeNs
            )

        // When
        fakeEvents.forEach {
            testedScope.handleEvent(it, mockWriter)
        }

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockSessionScope).handleEvent(capture(), eq(mockWriter))
            assertThat(firstValue).isInstanceOf(RumRawEvent.ApplicationStarted::class.java)
            val appStartEventTime = (firstValue as RumRawEvent.ApplicationStarted).eventTime
            assertThat(appStartEventTime.timestamp).isEqualTo(expectedEventTimestamp)
            assertThat(appStartEventTime.nanoTime).isEqualTo(appStartTimeNs)

            val processStartTimeNs =
                (firstValue as RumRawEvent.ApplicationStarted).applicationStartupNanos
            assertThat(processStartTimeNs).isEqualTo(firstEvent.eventTime.nanoTime - appStartTimeNs)

            assertThat(allValues.filterIsInstance<RumRawEvent.ApplicationStarted>()).hasSize(1)
        }
    }

    @Test
    fun `M not send ApplicationStarted event W handleEvent { app is not in foreground }`(
        forge: Forge
    ) {
        // Given
        val fakeEvents = forge.aList {
            forge.anyRumEvent(
                excluding = listOf(
                    RumRawEvent.ApplicationStarted::class,
                    RumRawEvent.SdkInit::class
                )
            )
        }
        val appStartTimeNs = forge.aLong(min = 0, max = fakeEvents.first().eventTime.nanoTime)
        whenever(mockSdkCore.appStartTimeNs) doReturn appStartTimeNs
        DdRumContentProvider.processImportance = forge.anElementFrom(
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING,
            @Suppress("DEPRECATION")
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING_PRE_28,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE_PRE_26,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE
        )
        val mockSessionScope = mock<RumScope>()
        testedScope.childScopes.clear()
        testedScope.childScopes += mockSessionScope

        // When
        fakeEvents.forEach {
            testedScope.handleEvent(it, mockWriter)
        }

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockSessionScope).handleEvent(capture(), eq(mockWriter))
            assertThat(allValues).doesNotHaveSameClassAs(RumRawEvent.ApplicationStarted::class.java)
        }
    }

    @Test
    fun `M not send ApplicationStarted event W handleEvent { SdkInit event }`(
        forge: Forge
    ) {
        // Given
        val fakeSdkInitEvent = forge.sdkInitEvent()
        val appStartTimeNs = forge.aLong(min = 0, max = fakeSdkInitEvent.eventTime.nanoTime)
        whenever(mockSdkCore.appStartTimeNs) doReturn appStartTimeNs
        DdRumContentProvider.processImportance = forge.anElementFrom(
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING,
            @Suppress("DEPRECATION")
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING_PRE_28,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE_PRE_26,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE
        )
        val mockSessionScope = mock<RumScope>()
        testedScope.childScopes.clear()
        testedScope.childScopes += mockSessionScope

        // When
        testedScope.handleEvent(fakeSdkInitEvent, mockWriter)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockSessionScope).handleEvent(capture(), eq(mockWriter))
            assertThat(allValues).doesNotHaveSameClassAs(RumRawEvent.ApplicationStarted::class.java)
        }
    }
}
