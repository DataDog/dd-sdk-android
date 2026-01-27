/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.app.ActivityManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.accessibility.AccessibilitySnapshotManager
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.domain.state.ViewUIPerformanceReport
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
    lateinit var mockChildScope: RumSessionScope

    @Mock
    lateinit var mockEvent: RumRawEvent

    @Mock
    lateinit var mockAccessibilitySnapshotManager: AccessibilitySnapshotManager

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockResolver: FirstPartyHostHeaderTypeResolver

    @Mock
    lateinit var mockCpuVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockMemoryVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockBatteryInfoProvider: InfoProvider<BatteryInfo>

    @Mock
    lateinit var mockDisplayInfoProvider: InfoProvider<DisplayInfo>

    @Mock
    private lateinit var mockInsightsCollector: InsightsCollector

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

    @Mock
    lateinit var mockNetworkSettledResourceIdentifier: InitialResourceIdentifier

    @Mock
    lateinit var mockLastInteractionIdentifier: LastInteractionIdentifier

    @Mock
    lateinit var mockSlowFramesListener: SlowFramesListener

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockInternalLogger: InternalLogger

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

    @Forgery
    lateinit var viewUIPerformanceReport: ViewUIPerformanceReport

    private var fakeRumSessionType: RumSessionType? = null

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockSdkCore.time) doReturn fakeTimeInfoAtScopeStart
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mock()
        whenever(mockSlowFramesListener.resolveReport(any(), any(), any())) doReturn viewUIPerformanceReport
        whenever(mockAccessibilitySnapshotManager.getIfChanged()) doReturn mock()

        fakeRumSessionType = forge.aNullable { aValueFrom(RumSessionType::class.java) }

        testedScope = RumApplicationScope(
            applicationId = fakeApplicationId,
            sdkCore = mockSdkCore,
            sampleRate = fakeSampleRate,
            backgroundTrackingEnabled = fakeBackgroundTrackingEnabled,
            trackFrustrations = fakeTrackFrustrations,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            sessionEndedMetricDispatcher = mockDispatcher,
            sessionListener = mockSessionListener,
            initialResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider,
            rumSessionScopeStartupManagerFactory = mock(),
            insightsCollector = mockInsightsCollector
        )
    }

    @Test
    fun `create child session scope with sample rate`() {
        val childScopes = testedScope.childScopes

        assertThat(childScopes).hasSize(1)
        val childScope = childScopes.firstOrNull()
        checkNotNull(childScope)
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
        val result = testedScope.handleEvent(event, fakeDatadogContext, mockEventWriteScope, mockWriter)
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

        testedScope.handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        verify(mockChildScope).handleEvent(mockEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)
        verifyNoInteractions(mockWriter)
    }

    @Test
    fun `M return active session W activeSession`() {
        // When
        val activeSession = testedScope.activeSession

        // Then
        assertThat(activeSession).isNotNull
        assertThat(activeSession).isSameAs(testedScope.childScopes.first())
    }

    @Test
    fun `M return last known active session W activeSession { multiple active sessions }`(
        forge: Forge
    ) {
        // Given
        val mockSessions = forge.aList {
            mock<RumSessionScope>().apply {
                whenever(isActive()) doReturn aBool()
            }
        }
        testedScope.childScopes += mockSessions
        assumeTrue(mockSessions.count { it.isActive() } > 1)

        // When
        val activeSession = testedScope.activeSession

        // Then
        assertThat(activeSession).isNotNull
        assertThat(activeSession).isSameAs(mockSessions.last { it.isActive() })
    }

    @Test
    fun `M log error W activeSession { multiple active sessions }`(
        forge: Forge
    ) {
        // Given
        val mockSessions = forge.aList {
            mock<RumSessionScope>().apply {
                whenever(isActive()) doReturn aBool()
            }
        }
        testedScope.childScopes += mockSessions
        assumeTrue(mockSessions.count { it.isActive() } > 1)

        // When
        testedScope.activeSession

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            RumApplicationScope.MULTIPLE_ACTIVE_SESSIONS_ERROR
        )
    }

    @Test
    fun `M have no active session W stopping current session`() {
        // When
        testedScope.handleEvent(RumRawEvent.StopSession(), fakeDatadogContext, mockEventWriteScope, mockWriter)

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
        whenever(
            mockSession.handleEvent(
                any(),
                eq(fakeDatadogContext),
                eq(mockEventWriteScope),
                eq(mockWriter)
            )
        ) doReturn mockSession

        // When
        testedScope.handleEvent(stopEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        assertThat(testedScope.childScopes).isNotEmpty
        assertThat(testedScope.childScopes.first()).isEqualTo(mockSession)
    }

    @Test
    fun `M create a new session W handleEvent { no active sessions, start view } `(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @Forgery eventTime: Time
    ) {
        // Given
        val initialSession = testedScope.childScopes.first()
        testedScope.handleEvent(RumRawEvent.StopSession(), fakeDatadogContext, mockEventWriteScope, mockWriter)
        testedScope.handleEvent(
            RumRawEvent.StartView(
                key = RumScopeKey.from(viewKey, viewName),
                attributes = mapOf(),
                eventTime = eventTime
            ),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        assertThat(testedScope.childScopes).hasSize(1)
        val newSession = testedScope.childScopes.first()
        assertThat(newSession).isNotSameAs(initialSession)
        assertThat(newSession.sampleRate).isEqualTo(fakeSampleRate)
        assertThat(newSession.backgroundTrackingEnabled).isEqualTo(fakeBackgroundTrackingEnabled)
        assertThat(newSession.firstPartyHostHeaderTypeResolver).isSameAs(mockResolver)
    }

    @Test
    fun `M create a new session with last known view W handleEvent { no active sessions, start action } `(
        forge: Forge,
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @Forgery eventTime: Time
    ) {
        // Given
        val mockAttributes = forge.exhaustiveAttributes()
        val fakeKey = RumScopeKey.from(viewKey, viewName)
        testedScope.handleEvent(
            RumRawEvent.StartView(
                key = fakeKey,
                attributes = mockAttributes,
                eventTime = eventTime
            ),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )
        testedScope.handleEvent(RumRawEvent.StopSession(), fakeDatadogContext, mockEventWriteScope, mockWriter)

        // When
        testedScope.handleEvent(
            RumRawEvent.StartAction(
                type = RumActionType.TAP,
                name = "MockAction",
                waitForStop = false,
                attributes = mapOf()
            ),
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter
        )

        // Then
        val newSession = testedScope.activeSession
        checkNotNull(newSession)
        val viewManager = newSession.childScope
        checkNotNull(viewManager)
        assertThat(viewManager.childrenScopes).isNotEmpty
        val viewScope = viewManager.childrenScopes.first()
        assertThat(viewScope.key).isEqualTo(fakeKey)
        assertThat(viewScope.viewAttributes).isEqualTo(mockAttributes)
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
        val mockSessionScope = mock<RumSessionScope>()
        testedScope.childScopes.clear()
        testedScope.childScopes += mockSessionScope

        val expectedEventTimestamp =
            TimeUnit.NANOSECONDS.toMillis(
                TimeUnit.MILLISECONDS.toNanos(firstEvent.eventTime.timestamp) -
                    firstEvent.eventTime.nanoTime + appStartTimeNs
            )

        // When
        fakeEvents.forEach {
            testedScope.handleEvent(it, fakeDatadogContext, mockEventWriteScope, mockWriter)
        }

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockSessionScope).handleEvent(
                capture(),
                eq(fakeDatadogContext),
                eq(mockEventWriteScope),
                eq(mockWriter)
            )
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
        val mockSessionScope = mock<RumSessionScope>()
        testedScope.childScopes.clear()
        testedScope.childScopes += mockSessionScope

        // When
        fakeEvents.forEach {
            testedScope.handleEvent(it, fakeDatadogContext, mockEventWriteScope, mockWriter)
        }

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockSessionScope).handleEvent(
                capture(),
                eq(fakeDatadogContext),
                eq(mockEventWriteScope),
                eq(mockWriter)
            )
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
        val mockSessionScope = mock<RumSessionScope>()
        testedScope.childScopes.clear()
        testedScope.childScopes += mockSessionScope

        // When
        testedScope.handleEvent(fakeSdkInitEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(mockSessionScope).handleEvent(
                capture(),
                eq(fakeDatadogContext),
                eq(mockEventWriteScope),
                eq(mockWriter)
            )
            assertThat(allValues).doesNotHaveSameClassAs(RumRawEvent.ApplicationStarted::class.java)
        }
    }
}
