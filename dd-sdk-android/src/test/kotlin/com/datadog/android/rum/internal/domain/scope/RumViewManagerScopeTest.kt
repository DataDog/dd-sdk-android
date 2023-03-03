/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.app.ActivityManager.RunningAppProcessInfo
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.internal.AppStartTimeProvider
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.anr.ANRDetectorRunnable
import com.datadog.android.rum.internal.anr.ANRException
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.api.context.TimeInfo
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
    lateinit var mockContextProvider: ContextProvider

    @Mock
    lateinit var mockAppStartTimeProvider: AppStartTimeProvider

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeTime: TimeInfo

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    @BeforeEach
    fun `set up`() {
        whenever(mockContextProvider.context) doReturn fakeDatadogContext
        whenever(mockSdkCore.time) doReturn fakeTime

        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        whenever(mockChildScope.isActive()) doReturn true
        whenever(mockAppStartTimeProvider.appStartTimeNs) doReturn fakeTime.deviceTimeNs

        testedScope = RumViewManagerScope(
            mockParentScope,
            mockSdkCore,
            true,
            fakeTrackFrustrations,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockAppStartTimeProvider,
            mockContextProvider
        )
    }

    @AfterEach
    fun `tear down`() {
        // whatever happens
        assertThat(testedScope.isActive()).isTrue()
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
        verifyZeroInteractions(mockWriter, logger.mockInternalLogger)
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
        verifyZeroInteractions(mockWriter)
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
        verifyZeroInteractions(mockWriter)
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
                assertThat(it.keyRef.get()).isEqualTo(fakeEvent.key)
                assertThat(it.name).isEqualTo(fakeEvent.name)
                assertThat(it.type).isEqualTo(RumViewScope.RumViewType.FOREGROUND)
                assertThat(it.attributes).containsAllEntriesOf(fakeEvent.attributes)
                assertThat(it.firstPartyHostHeaderTypeResolver).isSameAs(mockResolver)
            }
        assertThat(testedScope.applicationDisplayed).isTrue()
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
                assertThat(it.keyRef.get()).isEqualTo(fakeEvent.key)
                assertThat(it.name).isEqualTo(fakeEvent.name)
                assertThat(it.type).isEqualTo(RumViewScope.RumViewType.FOREGROUND)
                assertThat(it.attributes).containsAllEntriesOf(fakeEvent.attributes)
                assertThat(it.firstPartyHostHeaderTypeResolver).isSameAs(mockResolver)
            }
        assertThat(testedScope.applicationDisplayed).isTrue()
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
                assertThat(it.keyRef.get()).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_URL)
                assertThat(it.name).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_NAME)
                assertThat(it.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
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
                assertThat(it.keyRef.get()).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_URL)
                assertThat(it.name).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_NAME)
                assertThat(it.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.type).isEqualTo(RumViewScope.RumViewType.BACKGROUND)
            }
    }

    @Test
    fun `𝕄 start a bg ViewScope 𝕎 handleEvent { event is relevant, not foreground }`(
        forge: Forge
    ) {
        // Given
        CoreFeature.processImportance = forge.anElementFrom(
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
                assertThat(it.keyRef.get()).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_URL)
                assertThat(it.name).isEqualTo(RumViewManagerScope.RUM_BACKGROUND_VIEW_NAME)
                assertThat(it.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.type).isEqualTo(RumViewScope.RumViewType.BACKGROUND)
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
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            contextProvider = mockContextProvider
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
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            appStartTimeProvider = mockAppStartTimeProvider,
            contextProvider = mockContextProvider
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
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            appStartTimeProvider = mockAppStartTimeProvider,
            contextProvider = mockContextProvider
        )
        testedScope.applicationDisplayed = true
        val fakeEvent = forge.validBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                RumViewManagerScope.MESSAGE_MISSING_VIEW
            )
    }

    // endregion

    // region AppLaunch View

    @Test
    fun `𝕄 start an AppLaunch ViewScope 𝕎 handleEvent { app not displayed, any event }`(
        forge: Forge
    ) {
        // Given
        CoreFeature.processImportance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        testedScope.applicationDisplayed = false
        val fakeEvent = forge.anyRumEvent()
        val appStartTimeNs = forge.aLong(min = 0, max = fakeEvent.eventTime.nanoTime)
        whenever(mockAppStartTimeProvider.appStartTimeNs) doReturn appStartTimeNs

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val timestampNs = (
            TimeUnit.MILLISECONDS.toNanos(fakeEvent.eventTime.timestamp) -
                fakeEvent.eventTime.nanoTime
            ) +
            appStartTimeNs
        val timestampMs = TimeUnit.NANOSECONDS.toMillis((timestampNs))
        val scopeCount = if (fakeEvent is RumRawEvent.StartView) 2 else 1
        assertThat(testedScope.childrenScopes).hasSize(scopeCount)
        assertThat(testedScope.childrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp)
                    .isEqualTo(resolveExpectedTimestamp(timestampMs))
                assertThat(it.keyRef.get()).isEqualTo(RumViewManagerScope.RUM_APP_LAUNCH_VIEW_URL)
                assertThat(it.name).isEqualTo(RumViewManagerScope.RUM_APP_LAUNCH_VIEW_NAME)
                assertThat(it.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
                assertThat(it.type).isEqualTo(RumViewScope.RumViewType.APPLICATION_LAUNCH)
            }
    }

    @Test
    fun `𝕄 not start an AppLaunch ViewScope 𝕎 handleEvent { event is relevant, not foreground }`(
        forge: Forge
    ) {
        // Given
        CoreFeature.processImportance = forge.anElementFrom(
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
        testedScope = RumViewManagerScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            contextProvider = mockContextProvider
        )
        testedScope.applicationDisplayed = false
        // Start view still creates a child scope
        val fakeEvent = forge.anyRumEvent(excluding = listOf(RumRawEvent.StartView::class.java))

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(0)
    }

    @Test
    fun `𝕄 not start an AppLaunch ViewScope 𝕎 handleEvent { app displ, evt relev, active view}`(
        forge: Forge
    ) {
        // Given
        testedScope = RumViewManagerScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            appStartTimeProvider = mockAppStartTimeProvider,
            contextProvider = mockContextProvider
        )
        testedScope.childrenScopes.add(mockChildScope)
        whenever(mockChildScope.isActive()) doReturn true
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        // Start view still overide the current scope
        val fakeEvent = forge.anyRumEvent(excluding = listOf(RumRawEvent.StartView::class.java))

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0]).isSameAs(mockChildScope)
    }

    @Test
    fun `𝕄 not send warn dev log 𝕎 handleEvent { app not displayed, app launch event silent}`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = false
        val fakeEvent = forge.silentOrphanEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(logger.mockInternalLogger)
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
        whenever(mockSdkCore.getFeature(RumFeature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }
        val fakeEvent = forge.anyRumEvent()

        val appStartTimeNs = forge.aLong(min = 0, max = fakeEvent.eventTime.nanoTime)
        whenever(mockAppStartTimeProvider.appStartTimeNs) doReturn appStartTimeNs
        CoreFeature.processImportance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val appStartTimestamp = TimeUnit.NANOSECONDS.toMillis(
            (
                TimeUnit.MILLISECONDS.toNanos(fakeEvent.eventTime.timestamp) -
                    fakeEvent.eventTime.nanoTime
                ) +
                appStartTimeNs
        )
        argumentCaptor<ActionEvent> {
            verify(mockWriter, atLeastOnce()).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue.action.type).isEqualTo(ActionEvent.ActionEventActionType.APPLICATION_START)
            // Application start event occurse at the start time
            assertThat(firstValue.date).isEqualTo(resolveExpectedTimestamp(appStartTimestamp))

            // Duration lasts until the first event is sent to RUM (whatever that is)
            val loadingTime = fakeEvent.eventTime.nanoTime - appStartTimeNs
            assertThat(firstValue.action.loadingTime).isEqualTo(loadingTime)
        }
    }

    @Test
    fun `𝕄 not send ApplicationStarted event 𝕎 onViewDisplayed() {app already started}`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = true
        val childView: RumViewScope = mock()
        val startViewEvent = forge.startViewEvent()

        // When
        testedScope.handleEvent(startViewEvent, mockWriter)

        // Then
        verifyZeroInteractions(childView, mockWriter)
    }

    @Test
    fun `𝕄 not send ApplicationStarted event 𝕎 onViewDisplayed() {not foreground process}`(
        forge: Forge
    ) {
        // Given
        CoreFeature.processImportance = forge.anElementFrom(
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
        val childView: RumViewScope = mock()
        val startViewEvent = forge.startViewEvent()

        // When
        testedScope.handleEvent(startViewEvent, mockWriter)

        // Then
        verifyZeroInteractions(childView, mockWriter)
    }

    // endregion

    private fun resolveExpectedTimestamp(timestamp: Long): Long {
        return timestamp + fakeTime.serverTimeOffsetMs
    }

    companion object {

        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
