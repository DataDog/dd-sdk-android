/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.app.ActivityManager.RunningAppProcessInfo
import android.os.Build
import android.util.Log
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
    lateinit var mockDetector: FirstPartyHostDetector

    @Mock
    lateinit var mockCpuVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockMemoryVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockFrameRateVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockRumEventSourceProvider: RumEventSourceProvider

    @Mock
    lateinit var mockContextProvider: ContextProvider

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    @BeforeEach
    fun `set up`() {
        whenever(mockContextProvider.context) doReturn fakeDatadogContext

        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        whenever(mockChildScope.isActive()) doReturn true
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.BASE

        testedScope = RumViewManagerScope(
            mockParentScope,
            true,
            fakeTrackFrustrations,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockRumEventSourceProvider,
            mockBuildSdkVersionProvider,
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
        testedScope.childrenScopes.add(mockChildScope)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        assertThat(result).isSameAs(testedScope)
        verifyZeroInteractions(mockWriter, logger.mockDevLogHandler)
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
                assertThat(it.firstPartyHostDetector).isSameAs(mockDetector)
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
                assertThat(it.firstPartyHostDetector).isSameAs(mockDetector)
            }
        assertThat(testedScope.applicationDisplayed).isTrue()
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
    fun `𝕄 not start a bg ViewScope 𝕎 handleEvent { bg disabled, event is relevant }`(
        forge: Forge
    ) {
        // Given
        testedScope = RumViewManagerScope(
            parentScope = mockParentScope,
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            firstPartyHostDetector = mockDetector,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            rumEventSourceProvider = mockRumEventSourceProvider,
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
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            firstPartyHostDetector = mockDetector,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            rumEventSourceProvider = mockRumEventSourceProvider,
            buildSdkVersionProvider = mockBuildSdkVersionProvider,
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
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            firstPartyHostDetector = mockDetector,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            rumEventSourceProvider = mockRumEventSourceProvider,
            buildSdkVersionProvider = mockBuildSdkVersionProvider,
            contextProvider = mockContextProvider
        )
        testedScope.applicationDisplayed = true
        val fakeEvent = forge.validBackgroundEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(logger.mockDevLogHandler)
            .handleLog(Log.WARN, RumViewManagerScope.MESSAGE_MISSING_VIEW)
    }

    @Test
    fun `𝕄 not send warn dev log 𝕎 handleEvent { app not displayed, bg event silent}`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = false
        val fakeEvent = forge.silentOrphanEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyZeroInteractions(logger.mockDevLogHandler)
    }

    // endregion

    // region AppLaunch View

    @Test
    fun `𝕄 start an AppLaunch ViewScope 𝕎 handleEvent { app not displayed, event is relevant }`(
        forge: Forge
    ) {
        // Given
        CoreFeature.processImportance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        testedScope.applicationDisplayed = false
        val fakeEvent = forge.validAppLaunchEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0])
            .isInstanceOfSatisfying(RumViewScope::class.java) {
                assertThat(it.eventTimestamp)
                    .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
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
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            firstPartyHostDetector = mockDetector,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            rumEventSourceProvider = mockRumEventSourceProvider,
            contextProvider = mockContextProvider
        )
        testedScope.applicationDisplayed = false
        val fakeEvent = forge.validAppLaunchEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(0)
    }

    @Test
    fun `𝕄 not start an AppLaunch ViewScope 𝕎 handleEvent { app not displayed, evt not relevant}`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = false
        val fakeEvent = forge.invalidAppLaunchEvent()

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
            backgroundTrackingEnabled = false,
            trackFrustrations = fakeTrackFrustrations,
            firstPartyHostDetector = mockDetector,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            rumEventSourceProvider = mockRumEventSourceProvider,
            buildSdkVersionProvider = mockBuildSdkVersionProvider,
            contextProvider = mockContextProvider
        )
        testedScope.childrenScopes.add(mockChildScope)
        whenever(mockChildScope.isActive()) doReturn true
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        val fakeEvent = forge.validAppLaunchEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.childrenScopes).hasSize(1)
        assertThat(testedScope.childrenScopes[0]).isSameAs(mockChildScope)
    }

    @Test
    fun `𝕄 send warn dev log 𝕎 handleEvent { app not displayed, app launch event not relevant}`(
        forge: Forge
    ) {
        // Given
        testedScope.applicationDisplayed = false
        val fakeEvent = forge.invalidAppLaunchEvent()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            Log.WARN,
            RumViewManagerScope.MESSAGE_MISSING_VIEW
        )
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
        verifyZeroInteractions(logger.mockDevLogHandler)
    }

    // endregion

    // region ApplicationStarted

    @Test
    fun `𝕄 send ApplicationStarted event 𝕎 onViewDisplayed() {Legacy}`(
        forge: Forge,
        @IntForgery(min = Build.VERSION_CODES.KITKAT, max = Build.VERSION_CODES.N) apiVersion: Int
    ) {
        // Given
        CoreFeature.processImportance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        whenever(mockBuildSdkVersionProvider.version()) doReturn apiVersion
        val childView: RumViewScope = mock()
        val startViewEvent = forge.startViewEvent()

        // When
        testedScope.onViewDisplayed(startViewEvent, childView, mockWriter)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(childView).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.ApplicationStarted
            assertThat(event.applicationStartupNanos).isEqualTo(RumFeature.startupTimeNs)
        }
        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `𝕄 send ApplicationStarted event 𝕎 onViewDisplayed() {Nougat+}`(
        forge: Forge,
        @IntForgery(min = Build.VERSION_CODES.N) apiVersion: Int
    ) {
        // Given
        CoreFeature.processImportance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        whenever(mockBuildSdkVersionProvider.version()) doReturn apiVersion
        val childView: RumViewScope = mock()
        val startViewEvent = forge.startViewEvent()

        // When
        testedScope.onViewDisplayed(startViewEvent, childView, mockWriter)

        // Then
        argumentCaptor<RumRawEvent> {
            verify(childView).handleEvent(capture(), same(mockWriter))

            val event = firstValue as RumRawEvent.ApplicationStarted
            assertThat(event.applicationStartupNanos)
                .isCloseTo(System.nanoTime(), Offset.offset(TimeUnit.MILLISECONDS.toNanos(100)))
        }
        verifyZeroInteractions(mockWriter)
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
        testedScope.onViewDisplayed(startViewEvent, childView, mockWriter)

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
        testedScope.onViewDisplayed(startViewEvent, childView, mockWriter)

        // Then
        verifyZeroInteractions(childView, mockWriter)
    }

    // endregion

    private fun resolveExpectedTimestamp(timestamp: Long): Long {
        return timestamp + fakeDatadogContext.time.serverTimeOffsetMs
    }

    companion object {

        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
