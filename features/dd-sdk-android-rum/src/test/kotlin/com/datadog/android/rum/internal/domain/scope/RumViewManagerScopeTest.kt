/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.app.ActivityManager.RunningAppProcessInfo
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.internal.anr.ANRDetectorRunnable
import com.datadog.android.rum.internal.anr.ANRException
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumViewManagerScopeTest {

    lateinit var testedScope: RumViewManagerScope

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
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockViewChangedListener: RumViewChangedListener

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeTime: TimeInfo

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    private var fakeSampleRate: Float = 0.0f

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeSampleRate = forge.aFloat(min = 0.0f, max = 100.0f)

        whenever(mockSdkCore.time) doReturn fakeTime

        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        whenever(mockChildScope.isActive()) doReturn true
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        testedScope = RumViewManagerScope(
            mockParentScope,
            mockSdkCore,
            true,
            fakeTrackFrustrations,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            applicationDisplayed = false,
            sampleRate = fakeSampleRate
        )
    }

    @AfterEach
    fun `tear down`() {
        // whatever happens
        assertThat(testedScope.getRumContext()).isEqualTo(fakeParentContext)
    }

    // region Children scopes

    @Test
    fun `𝕄 delegate to child scope 𝕎 handleEvent()`() {
        // Given
        val fakeEvent: RumRawEvent = mock()
        whenever(fakeEvent.eventTime) doReturn Time()
        testedScope.childrenScopes.add(mockChildScope)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        assertThat(result).isSameAs(testedScope)
        verifyNoInteractions(mockWriter)
    }

    @Test
    fun `𝕄 keep children scope 𝕎 handleEvent child returns non null`() {
        // Given
        val fakeEvent: RumRawEvent = mock()
        testedScope.childrenScopes.add(mockChildScope)
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).containsExactly(mockChildScope)
        assertThat(result).isSameAs(testedScope)
        verifyNoInteractions(mockWriter)
    }

    @Test
    fun `𝕄 remove children scope 𝕎 handleEvent child returns null`() {
        // Given
        val fakeEvent: RumRawEvent = mock()
        testedScope.childrenScopes.add(mockChildScope)
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn null

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).isEmpty()
        assertThat(result).isSameAs(testedScope)
        verifyNoInteractions(mockWriter)
    }

    // endregion

    // region Foreground View

    @Test
    fun `𝕄 start a foreground ViewScope 𝕎 handleEvent(StartView) { app displayed }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.startViewEvent()
        testedScope.applicationDisplayed = true

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp)
                    .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                assertThat(it.key).isEqualTo(fakeEvent.key)
                assertThat(it.type).isEqualTo(RumViewScope.RumViewType.FOREGROUND)
                assertThat(it.attributes).containsAllEntriesOf(fakeEvent.attributes)
                assertThat(it.firstPartyHostHeaderTypeResolver).isSameAs(mockResolver)
                assertThat(it.version).isEqualTo(2)
            }
        assertThat(testedScope.applicationDisplayed).isTrue()
        assertThat(testedScope.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
    }

    @Test
    fun `𝕄 start a foreground ViewScope 𝕎 handleEvent(StartView) { app not displayed }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.startViewEvent()
        testedScope.applicationDisplayed = false

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp)
                    .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                assertThat(it.key).isEqualTo(fakeEvent.key)
                assertThat(it.type).isEqualTo(RumViewScope.RumViewType.FOREGROUND)
                assertThat(it.attributes).containsAllEntriesOf(fakeEvent.attributes)
                assertThat(it.firstPartyHostHeaderTypeResolver).isSameAs(mockResolver)
                assertThat(it.version).isEqualTo(2)
            }
        assertThat(testedScope.applicationDisplayed).isTrue()
        assertThat(testedScope.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
    }

    @Test
    fun `𝕄 reset feature flags 𝕎 handleEvent(StartView) { view already active }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.startViewEvent()
        testedScope.applicationDisplayed = true
        testedScope.handleEvent(fakeEvent, mockWriter)
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                forge.anAlphabeticalString(),
                forge.anAlphaNumericalString()
            ),
            mockWriter
        )
        val secondViewEvent = forge.startViewEvent()

        // When
        testedScope.handleEvent(secondViewEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.featureFlags).isEmpty()
            }
    }

    @Test
    fun `𝕄 send gap message 𝕎 handleEvent(StopView) + handleEvent(StartView)`(
        forge: Forge
    ) {
        // Given
        val firstViewEvent = forge.startViewEvent()
        testedScope.applicationDisplayed = true
        testedScope.handleEvent(firstViewEvent, mockWriter)
        val stopFirstViewEvent = RumRawEvent.StopView(firstViewEvent.key, emptyMap())
        testedScope.handleEvent(stopFirstViewEvent, mockWriter)

        // When
        Thread.sleep(15)
        val secondViewEvent = forge.startViewEvent()
        testedScope.handleEvent(secondViewEvent, mockWriter)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            listOf(InternalLogger.Target.TELEMETRY, InternalLogger.Target.MAINTAINER),
            { it.matches(Regex("Gap between views was \\d+ nanoseconds")) }
        )
    }

    @Test
    fun `𝕄 not send gap message 𝕎 handleEvent(StartView) + handleEvent(StartView)`(
        forge: Forge
    ) {
        // Given
        val firstViewEvent = forge.startViewEvent()
        testedScope.applicationDisplayed = true
        testedScope.handleEvent(firstViewEvent, mockWriter)

        // When
        Thread.sleep(15)
        val secondViewEvent = forge.startViewEvent()
        testedScope.handleEvent(secondViewEvent, mockWriter)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            listOf(InternalLogger.Target.TELEMETRY, InternalLogger.Target.MAINTAINER),
            { it.matches(Regex("Gap between views was \\d+ nanoseconds")) },
            mode = never()
        )
    }

    // endregion

    // region Background View

    @Test
    fun `𝕄 start a bg ViewScope 𝕎 handleEvent { app displayed, event is relevant }`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = true
        val fakeEvent = forge.validBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp)
                    .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                assertThat(it.key.id).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_ID)
                assertThat(it.key.url).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_URL)
                assertThat(it.key.name).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_NAME)
                assertThat(it.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
            }
    }

    @Test
    fun `𝕄 start a bg ViewScope 𝕎 handleEvent { stopped view, event is relevant }`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = true
        testedScope.childrenScopes.add(mockChildScope)
        whenever(mockChildScope.isActive()) doReturn false
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        val fakeEvent = forge.validBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(2)
        assertThat(testedScope.childrenScopes[0]).isSameAs(mockChildScope)
        assertThat(testedScope.childrenScopes[1])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp)
                    .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                assertThat(it.key.id).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_ID)
                assertThat(it.key.url).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_URL)
                assertThat(it.key.name).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_NAME)
                assertThat(it.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.type).isEqualTo(RumViewScope.RumViewType.BACKGROUND)
                assertThat(it.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
            }
    }

    @Test
    fun `𝕄 start a bg ViewScope 𝕎 handleEvent { event is relevant, not foreground }`(
        forge: Forge
    ) {
        // Given
        DdRumContentProvider.processImportance = forge.anElementFrom(
            RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
            RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING,
            @Suppress("DEPRECATION")
            RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING_PRE_28,
            RunningAppProcessInfo.IMPORTANCE_VISIBLE,
            RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE,
            RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE_PRE_26,
            RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE,
            RunningAppProcessInfo.IMPORTANCE_SERVICE,
            RunningAppProcessInfo.IMPORTANCE_CACHED,
            RunningAppProcessInfo.IMPORTANCE_GONE
        )

        testedScope.applicationDisplayed = false
        val fakeEvent = forge.validBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp)
                    .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                assertThat(it.key.id).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_ID)
                assertThat(it.key.url).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_URL)
                assertThat(it.key.name).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_NAME)
                assertThat(it.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.type).isEqualTo(RumViewScope.RumViewType.BACKGROUND)
                assertThat(it.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
            }
    }

    @Test
    fun `𝕄 not start a bg ViewScope 𝕎 handleEvent { app displayed, event not relevant}`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = true
        val fakeEvent = forge.invalidBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(0)
    }

    @Test
    fun `𝕄 not start a bg ViewScope 𝕎 handleEvent { app displayed, event is background ANR}`() {
        // Given
        testedScope.applicationDisplayed = true
        val fakeEvent = RumRawEvent.AddError(
            message = ANRDetectorRunnable.ANR_MESSAGE,
            source = RumErrorSource.SOURCE,
            stacktrace = null,
            throwable = ANRException(Thread.currentThread()),
            isFatal = false,
            threads = emptyList(),
            attributes = emptyMap()
        )

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(0)
    }

    @Test
    fun `𝕄 not start a bg ViewScope 𝕎 handleEvent { bg disabled, event is relevant }`(
        forge: Forge
    ) {
        // Given
        testedScope = RumViewManagerScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            viewChangedListener = mockViewChangedListener,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            applicationDisplayed = false,
            sampleRate = fakeSampleRate
        )
        testedScope.applicationDisplayed = true
        val fakeEvent = forge.validBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(0)
    }

    @Test
    fun `𝕄 not start a bg ViewScope 𝕎 handleEvent { app displayed, event relevant, active view }`(
        forge: Forge
    ) {
        // Given
        testedScope = RumViewManagerScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            viewChangedListener = mockViewChangedListener,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            applicationDisplayed = false,
            sampleRate = fakeSampleRate
        )
        testedScope.childrenScopes.add(mockChildScope)
        whenever(mockChildScope.isActive()) doReturn true
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        val fakeEvent = forge.validBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0]).isSameAs(mockChildScope)
    }

    @Test
    fun `𝕄 send warn dev log 𝕎 handleEvent { app displayed, event is relevant, bg disabled }`(
        forge: Forge
    ) {
        // Given
        testedScope = RumViewManagerScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            viewChangedListener = mockViewChangedListener,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            applicationDisplayed = false,
            sampleRate = fakeSampleRate
        )
        testedScope.applicationDisplayed = true
        val fakeEvent = forge.validBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumViewManagerScope.MESSAGE_MISSING_VIEW
        )
    }

    // endregion

    // region AppLaunch View

    @Test
    fun `𝕄 start an AppLaunch ViewScope 𝕎 handleEvent { app not displayed }`(
        forge: Forge
    ) {
        // Given
        DdRumContentProvider.processImportance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        testedScope.applicationDisplayed = false
        val fakeAppStartEvent = forge.applicationStartedEvent()

        // When
        testedScope.handleEvent(fakeAppStartEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp)
                    .isEqualTo(resolveExpectedTimestamp(fakeAppStartEvent.eventTime.timestamp))
                assertThat(it.key.id).isEqualTo(RumViewManagerScope.RUM_APP_LAUNCH_VIEW_ID)
                assertThat(it.key.url).isEqualTo(RumViewManagerScope.RUM_APP_LAUNCH_VIEW_URL)
                assertThat(it.key.name).isEqualTo(RumViewManagerScope.RUM_APP_LAUNCH_VIEW_NAME)
                assertThat(it.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.type).isEqualTo(RumViewScope.RumViewType.APPLICATION_LAUNCH)
            }
    }

    @Test
    fun `𝕄 not start an AppLaunch ViewScope 𝕎 handleEvent { app displayed }`(
        forge: Forge
    ) {
        // Given
        testedScope = RumViewManagerScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            viewChangedListener = mockViewChangedListener,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            applicationDisplayed = true,
            sampleRate = fakeSampleRate
        )
        testedScope.childrenScopes.add(mockChildScope)
        whenever(mockChildScope.isActive()) doReturn true
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope

        val fakeEvent = forge.applicationStartedEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0]).isSameAs(mockChildScope)
    }

    @Test
    fun `𝕄 not start an AppLaunch ViewScope 𝕎 handleEvent { session stopped }`(
        forge: Forge
    ) {
        // Given
        testedScope = RumViewManagerScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            viewChangedListener = mockViewChangedListener,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            applicationDisplayed = false,
            sampleRate = fakeSampleRate
        )
        testedScope.stopped = true
        val fakeEvent = forge.applicationStartedEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
    }

    // endregion

    // region ApplicationStarted

    @Test
    fun `𝕄 send ApplicationStarted event 𝕎 handleEvent ()`(
        forge: Forge
    ) {
        // Given
        // Because we have to test that the `application_started` action is written, we need to set
        // up the feature scope properly
        val mockRumFeatureScope: FeatureScope = mock()
        val mockEventBatchWriter: EventBatchWriter = mock()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }
        val fakeAppStartEvent = forge.applicationStartedEvent()

        // When
        testedScope.handleEvent(fakeAppStartEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter, atLeastOnce()).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue.action.type)
                .isEqualTo(ActionEvent.ActionEventActionType.APPLICATION_START)
            // Application start event occurs at the start time
            assertThat(firstValue.date)
                .isEqualTo(resolveExpectedTimestamp(fakeAppStartEvent.eventTime.timestamp))

            // Duration lasts until the first event is sent to RUM (whatever that is)
            val loadingTime = fakeAppStartEvent.applicationStartupNanos
            assertThat(firstValue.action.loadingTime).isEqualTo(loadingTime)
        }
    }

    @Test
    fun `𝕄 not send ApplicationStarted event 𝕎 onViewDisplayed() {app already started}`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = true
        val fakeAppStartEvent = forge.applicationStartedEvent()

        // When
        testedScope.handleEvent(fakeAppStartEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
    }

    // endregion

    // region Session Ended

    @Test
    fun `M set isActive to false W handleEvent { StopSession }`() {
        // Given
        testedScope.applicationDisplayed = true

        // When
        val result = testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // Then
        assertThat(result).isNull()
        assertThat(testedScope.isActive()).isFalse()
    }

    @Test
    fun `M return scope from handleEvent W stopped { with active child scopes }`() {
        // Given
        testedScope.applicationDisplayed = true
        testedScope.childrenScopes.add(mockChildScope)
        whenever(mockChildScope.handleEvent(any(), eq(mockWriter))) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        // Then
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `M return null from handleEvent W stopped { completed child scopes }`() {
        // Given
        testedScope.applicationDisplayed = true
        testedScope.childrenScopes.add(mockChildScope)
        val stopEvent = RumRawEvent.StopSession()
        val fakeEvent: RumRawEvent = mock()
        whenever(mockChildScope.handleEvent(stopEvent, mockWriter)) doReturn mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn null

        // When
        val firstResult = testedScope.handleEvent(stopEvent, mockWriter)
        val secondResult = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(firstResult).isSameAs(testedScope)
        assertThat(secondResult).isNull()
    }

    @Test
    fun `M not display application W stopped`() {
        // When
        val result = testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)

        assertThat(result).isNull()
        assertThat(testedScope.applicationDisplayed).isFalse
        assertThat(testedScope.childrenScopes).isEmpty()
    }

    @Test
    fun `M not start a new view W stopped { application not displayed }`(
        forge: Forge
    ) {
        // Given
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)
        val fakeEvent = forge.startViewEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).isEmpty()
    }

    @Test
    fun `M not start a new view W stopped { application displayed }`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = true
        testedScope.handleEvent(RumRawEvent.StopSession(), mockWriter)
        val fakeEvent = forge.startViewEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).isEmpty()
    }

    // endregion

    private fun resolveExpectedTimestamp(timestamp: Long): Long {
        return timestamp + fakeTime.serverTimeOffsetMs
    }
}
