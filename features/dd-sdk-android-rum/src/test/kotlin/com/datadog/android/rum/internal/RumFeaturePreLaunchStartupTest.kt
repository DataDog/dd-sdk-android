/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.Activity
import android.app.Application
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.startup.PreLaunchRumAppStartupDetector
import com.datadog.android.rum.internal.startup.RumAppStartupDetector
import com.datadog.android.rum.internal.startup.RumStartupScenario
import com.datadog.android.rum.startup.AppStartupActivityPredicate
import com.datadog.android.rum.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.rum.utils.config.MainLooperTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.setFieldValue
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * Covers the hand-off between the `dd-sdk-android-rum-prelaunch` module's
 * [PreLaunchRumAppStartupDetector] and [RumFeature].
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumFeaturePreLaunchStartupTest {

    private lateinit var testedFeature: RumFeature

    @Forgery
    lateinit var fakeApplicationId: UUID

    @Forgery
    lateinit var fakeConfiguration: RumFeature.Configuration

    private lateinit var fakeScenario: RumStartupScenario

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockRumMonitor: AdvancedRumMonitor

    @Mock
    lateinit var mockSdkCore2: InternalSdkCore

    @Mock
    lateinit var mockRumMonitor2: AdvancedRumMonitor

    @Mock
    lateinit var mockAppStartupActivityPredicate2: AppStartupActivityPredicate

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockLateCrashReporter: LateCrashReporter

    @Mock
    lateinit var mockAppStartupActivityPredicate: AppStartupActivityPredicate

    @Mock
    lateinit var mockActivity: Activity

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mock()
        whenever(mockSdkCore.createScheduledExecutorService(any())) doReturn mock()

        whenever(appContext.mockInstance.contentResolver) doReturn mock()
        doNothing().whenever(appContext.mockInstance).registerComponentCallbacks(any())
        doNothing().whenever(appContext.mockInstance).unregisterComponentCallbacks(any())

        val mockResources = mock<android.content.res.Resources>()
        whenever(appContext.mockInstance.resources) doReturn mockResources
        whenever(mockResources.configuration) doReturn mock()

        whenever(mockAppStartupActivityPredicate.shouldTrackStartup(any())) doReturn true

        fakeScenario = RumStartupScenario.Cold(
            hasSavedInstanceStateBundle = false,
            activity = WeakReference(mockActivity),
            appStartActivityOnCreateGapNs = 0L,
            initialTime = Time(0L, 0L)
        )

        resetPreLaunchDetector()
        createFeature()
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor.clear()
        resetPreLaunchDetector()
    }

    // region initRumAppStartupDetector

    @Test
    fun `M create its own detector W onInitialize() {pre-launch module absent}`() {
        // Given — nothing installed the pre-launch detector
        assertThat(PreLaunchRumAppStartupDetector.isInstalled).isFalse()

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.usePreLaunchDetector).isFalse()
        assertThat(testedFeature.rumAppStartupDetector).isNotNull()
    }

    @Test
    fun `M reuse the pre-launch detector W onInitialize() {pre-launch module installed}`() {
        // Given
        installPreLaunchDetector()

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then — no second detector is created, the pre-launch one is reused
        assertThat(testedFeature.usePreLaunchDetector).isTrue()
        assertThat(testedFeature.rumAppStartupDetector).isNull()
    }

    @Test
    fun `M reuse the pre-launch detector W onInitialize() {captured activity excluded}`() {
        // Given — the pre-launch detector accepts every Activity, but the configured predicate
        // excludes the one it happened to capture. Reusing it is still correct: attach() applies
        // this core's predicate to the buffer and to everything observed afterwards.
        installPreLaunchDetector()
        emitPreLaunchAppStartup()
        whenever(mockAppStartupActivityPredicate.shouldTrackStartup(mockActivity)) doReturn false

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.usePreLaunchDetector).isTrue()
        assertThat(testedFeature.rumAppStartupDetector).isNull()
    }

    // endregion

    // region attachPreLaunchRumAppStartupDetector

    @Test
    fun `M forward buffered events to the monitor W attachPreLaunchRumAppStartupDetector()`() {
        // Given
        installPreLaunchDetector()
        emitPreLaunchAppStartup()
        emitPreLaunchTTID()
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.attachPreLaunchRumAppStartupDetector()

        // Then
        verify(mockRumMonitor, times(1)).sendAppStartEvent(fakeScenario)
        verify(mockRumMonitor, times(1)).sendTTIDEvent(any())
    }

    @Test
    fun `M do nothing W attachPreLaunchRumAppStartupDetector() {pre-launch detector not used}`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.attachPreLaunchRumAppStartupDetector()

        // Then
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
        verify(mockRumMonitor, never()).sendTTIDEvent(any())
    }

    @Test
    fun `M drop buffered events W attach() {scenario activity excluded}`() {
        // Given — the launch was captured with the permissive pre-launch predicate, but the
        // configured one excludes its Activity.
        val excludedActivity = mock<Activity>()
        val excludedScenario = coldScenarioFor(excludedActivity)
        whenever(mockAppStartupActivityPredicate.shouldTrackStartup(excludedActivity)) doReturn false

        installPreLaunchDetector()
        PreLaunchRumAppStartupDetector.onAppStartupDetected(excludedScenario)
        PreLaunchRumAppStartupDetector.onTTIDComputed(excludedScenario, TTID_DURATION_NS, false, null)
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.attachPreLaunchRumAppStartupDetector()

        // Then — nothing from the excluded launch reaches the monitor
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
        verify(mockRumMonitor, never()).sendTTIDEvent(any())
    }

    @Test
    fun `M replay only the most recent launch W attach() {two launches buffered}`() {
        // Given — two launches happened before the SDK was enabled
        val earlierScenario = coldScenarioFor(mock())

        installPreLaunchDetector()
        PreLaunchRumAppStartupDetector.onAppStartupDetected(earlierScenario)
        PreLaunchRumAppStartupDetector.onTTIDComputed(earlierScenario, TTID_DURATION_NS, false, null)
        emitPreLaunchAppStartup()
        emitPreLaunchTTID()
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.attachPreLaunchRumAppStartupDetector()

        // Then — a new launch supersedes the previous one, so only the latest is replayed
        verify(mockRumMonitor, never()).sendAppStartEvent(earlierScenario)
        verify(mockRumMonitor, times(1)).sendAppStartEvent(fakeScenario)
        verify(mockRumMonitor, times(1)).sendTTIDEvent(any())
    }

    @Test
    fun `M replay the in-flight AppStart W attach() {joins mid-launch}`() {
        // Given — one core is already attached and hearing this launch live, and a second core
        // enables RUM after the AppStart but before the first frame is drawn.
        installPreLaunchDetector()
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.attachPreLaunchRumAppStartupDetector()
        emitPreLaunchAppStartup()

        val secondFeature = createSecondFeature()
        secondFeature.onInitialize(appContext.mockInstance)

        // When
        secondFeature.attachPreLaunchRumAppStartupDetector()
        emitPreLaunchTTID()

        // Then — the joiner gets the whole launch, not a TTID with no AppStart to index against
        verify(mockRumMonitor2, times(1)).sendAppStartEvent(fakeScenario)
        verify(mockRumMonitor2, times(1)).sendTTIDEvent(any())
    }

    @Test
    fun `M drop the TTID W attach() {joins after the AppStart was superseded}`() {
        // Given — a launch whose AppStart this core never saw, because it attached after the
        // buffer had already been replaced.
        installPreLaunchDetector()
        emitPreLaunchAppStartup()
        PreLaunchRumAppStartupDetector
            .getFieldValue<MutableList<*>, PreLaunchRumAppStartupDetector>("pendingEvents")
            .clear()
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.attachPreLaunchRumAppStartupDetector()

        // When
        emitPreLaunchTTID()

        // Then — a TTID with no matching AppStart would land on a negative index in the session
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
        verify(mockRumMonitor, never()).sendTTIDEvent(any())
    }

    @Test
    fun `M drop buffered TTID W attach() {forwarding activity excluded}`() {
        // Given — the scenario's Activity is accepted, but the Activity that actually drew
        // (subscribed to while the pre-launch predicate accepted everything) is not.
        val forwardingActivity = mock<Activity>()
        whenever(mockAppStartupActivityPredicate.shouldTrackStartup(forwardingActivity)) doReturn false

        installPreLaunchDetector()
        emitPreLaunchAppStartup()
        PreLaunchRumAppStartupDetector.onTTIDComputed(
            fakeScenario,
            TTID_DURATION_NS,
            true,
            WeakReference(forwardingActivity)
        )
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.attachPreLaunchRumAppStartupDetector()

        // Then — the AppStart survives, the TTID measured on the excluded Activity does not
        verify(mockRumMonitor, times(1)).sendAppStartEvent(fakeScenario)
        verify(mockRumMonitor, never()).sendTTIDEvent(any())
    }

    @Test
    fun `M forward buffered events W attach() {scenario activity already collected}`() {
        // Given — the launch Activity was garbage collected before Rum.enable() ran, so the
        // predicate cannot be applied to it. The capture is still forwarded.
        val collectedScenario = RumStartupScenario.Cold(
            hasSavedInstanceStateBundle = false,
            activity = WeakReference<Activity>(null),
            appStartActivityOnCreateGapNs = 0L,
            initialTime = Time(0L, 0L)
        )
        whenever(mockAppStartupActivityPredicate.shouldTrackStartup(any())) doReturn false

        installPreLaunchDetector()
        PreLaunchRumAppStartupDetector.onAppStartupDetected(collectedScenario)
        PreLaunchRumAppStartupDetector.onTTIDComputed(collectedScenario, TTID_DURATION_NS, false, null)
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.attachPreLaunchRumAppStartupDetector()

        // Then
        verify(mockRumMonitor, times(1)).sendAppStartEvent(collectedScenario)
        verify(mockRumMonitor, times(1)).sendTTIDEvent(any())
    }

    // endregion

    // region multiple SDK cores

    @Test
    fun `M replay buffered events to every core W two cores attach`() {
        // Given
        installPreLaunchDetector()
        emitPreLaunchAppStartup()
        emitPreLaunchTTID()
        val secondFeature = createSecondFeature()

        // When — both cores enable RUM, as they would back to back in Application onCreate
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.attachPreLaunchRumAppStartupDetector()
        secondFeature.onInitialize(appContext.mockInstance)
        secondFeature.attachPreLaunchRumAppStartupDetector()

        // Then — the second core's listener does not evict the first one's, and the buffer is
        // replayed rather than consumed
        assertThat(PreLaunchRumAppStartupDetector.attachedListenerCount).isEqualTo(2)
        verify(mockRumMonitor, times(1)).sendAppStartEvent(fakeScenario)
        verify(mockRumMonitor, times(1)).sendTTIDEvent(any())
        verify(mockRumMonitor2, times(1)).sendAppStartEvent(fakeScenario)
        verify(mockRumMonitor2, times(1)).sendTTIDEvent(any())
    }

    @Test
    fun `M forward live events per core predicate W two cores attached`() {
        // Given — the two cores disagree about whether this Activity is a startup Activity
        whenever(mockAppStartupActivityPredicate.shouldTrackStartup(mockActivity)) doReturn false
        whenever(mockAppStartupActivityPredicate2.shouldTrackStartup(mockActivity)) doReturn true
        installPreLaunchDetector()
        val secondFeature = createSecondFeature()
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.attachPreLaunchRumAppStartupDetector()
        secondFeature.onInitialize(appContext.mockInstance)
        secondFeature.attachPreLaunchRumAppStartupDetector()

        // When
        emitPreLaunchAppStartup()

        // Then — one core's narrower predicate does not decide what the other sees
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
        verify(mockRumMonitor2, times(1)).sendAppStartEvent(fakeScenario)
    }

    @Test
    fun `M keep the other core's listener W onStop() {two cores attached}`() {
        // Given
        installPreLaunchDetector()
        val secondFeature = createSecondFeature()
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.attachPreLaunchRumAppStartupDetector()
        secondFeature.onInitialize(appContext.mockInstance)
        secondFeature.attachPreLaunchRumAppStartupDetector()

        // When — only the first core stops
        testedFeature.onStop()
        emitPreLaunchAppStartup()

        // Then — the second core keeps receiving events
        assertThat(PreLaunchRumAppStartupDetector.attachedListenerCount).isEqualTo(1)
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
        verify(mockRumMonitor2, times(1)).sendAppStartEvent(fakeScenario)
    }

    // endregion

    // region Helpers

    private fun coldScenarioFor(activity: Activity): RumStartupScenario = RumStartupScenario.Cold(
        hasSavedInstanceStateBundle = false,
        activity = WeakReference(activity),
        appStartActivityOnCreateGapNs = 0L,
        initialTime = Time(0L, 0L)
    )

    private fun createFeature() {
        testedFeature = RumFeature(
            mockSdkCore,
            fakeApplicationId.toString(),
            fakeConfiguration.copy(
                appStartupActivityPredicate = mockAppStartupActivityPredicate,
                viewTrackingStrategy = null
            ),
            lateCrashReporterFactory = { mockLateCrashReporter }
        )
    }

    /**
     * A second SDK core enabling RUM in the same process, with its own monitor and its own startup
     * Activity predicate.
     */
    private fun createSecondFeature(): RumFeature {
        whenever(mockSdkCore2.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore2.timeProvider) doReturn mock()
        whenever(mockSdkCore2.createScheduledExecutorService(any())) doReturn mock()
        whenever(mockAppStartupActivityPredicate2.shouldTrackStartup(any())) doReturn true
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor2, mockSdkCore2)

        return RumFeature(
            mockSdkCore2,
            fakeApplicationId.toString(),
            fakeConfiguration.copy(
                appStartupActivityPredicate = mockAppStartupActivityPredicate2,
                viewTrackingStrategy = null
            ),
            lateCrashReporterFactory = { mockLateCrashReporter }
        )
    }

    /**
     * [PreLaunchRumAppStartupDetector] is a process-scoped singleton, so its state has to be
     * cleared between tests. Its backing fields are private, hence the reflection.
     */
    private fun resetPreLaunchDetector() {
        PreLaunchRumAppStartupDetector.setFieldValue("detectorImpl", null)
        PreLaunchRumAppStartupDetector
            .getFieldValue<MutableList<*>, PreLaunchRumAppStartupDetector>("registrations")
            .clear()
        PreLaunchRumAppStartupDetector
            .getFieldValue<MutableList<*>, PreLaunchRumAppStartupDetector>("pendingEvents")
            .clear()
    }

    /**
     * Marks the singleton as installed without actually registering lifecycle callbacks — this
     * test drives the listener callbacks by hand instead.
     */
    private fun installPreLaunchDetector(): RumAppStartupDetector {
        val mockDetector = mock<RumAppStartupDetector>()
        PreLaunchRumAppStartupDetector.setFieldValue("detectorImpl", mockDetector)
        return mockDetector
    }

    private fun emitPreLaunchAppStartup() {
        PreLaunchRumAppStartupDetector.onAppStartupDetected(fakeScenario)
    }

    private fun emitPreLaunchTTID() {
        PreLaunchRumAppStartupDetector.onTTIDComputed(
            scenario = fakeScenario,
            durationNs = TTID_DURATION_NS,
            wasForwarded = false
        )
    }

    // endregion

    companion object {
        private const val TTID_DURATION_NS = 123_456_789L

        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, mainLooper)
        }
    }
}
