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
import com.datadog.android.rum.AppLaunchPreInitCollector
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.startup.RumAppStartupDetector
import com.datadog.android.rum.internal.startup.RumStartupScenario
import com.datadog.android.rum.internal.startup.RumTTIDInfo
import com.datadog.android.rum.startup.AppStartupActivityPredicate
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.NavigationViewTrackingStrategy
import com.datadog.android.rum.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.rum.utils.config.MainLooperTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.getFieldValue
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumFeaturePreInitStartupTest {

    private lateinit var testedFeature: RumFeature

    @Forgery
    lateinit var fakeApplicationId: UUID

    @Forgery
    lateinit var fakeConfiguration: RumFeature.Configuration

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockRumMonitor: AdvancedRumMonitor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockLateCrashReporter: LateCrashReporter

    @Mock
    lateinit var mockAppStartupActivityPredicate: AppStartupActivityPredicate

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mock()
        whenever(mockSdkCore.createScheduledExecutorService(any())) doReturn mock()

        val mockContentResolver = mock<android.content.ContentResolver>()
        whenever(appContext.mockInstance.contentResolver) doReturn mockContentResolver
        doNothing().whenever(appContext.mockInstance).registerComponentCallbacks(any())
        doNothing().whenever(appContext.mockInstance).unregisterComponentCallbacks(any())

        val mockResources = mock<android.content.res.Resources>()
        whenever(appContext.mockInstance.resources) doReturn mockResources
        whenever(mockResources.configuration) doReturn mock()

        whenever(mockAppStartupActivityPredicate.shouldTrackStartup(any())) doReturn true

        testedFeature = RumFeature(
            mockSdkCore,
            fakeApplicationId.toString(),
            fakeConfiguration.copy(appStartupActivityPredicate = mockAppStartupActivityPredicate),
            lateCrashReporterFactory = { mockLateCrashReporter }
        )
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor.clear()

        // Reset AppLaunchPreInitCollector singleton state via reflection
        // (reset() is internal to dd-sdk-android-internal, not accessible from this module)
        val stateField = AppLaunchPreInitCollector::class.java.getDeclaredField("_state")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (stateField.get(AppLaunchPreInitCollector) as AtomicReference<Any>)
            .set(AppLaunchPreInitCollector.State.NOT_INSTALLED)

        // Reset data fields directly (they are public vars)
        AppLaunchPreInitCollector.processStartNs = 0L
        AppLaunchPreInitCollector.activityOnCreateNs = 0L
        AppLaunchPreInitCollector.firstFrameNs = 0L
        AppLaunchPreInitCollector.hasSavedInstanceState = false
        AppLaunchPreInitCollector.isFirstActivityForProcess = true
        AppLaunchPreInitCollector.activity = null

        // Clear firstFrameCallbacks via reflection
        val callbacksField = AppLaunchPreInitCollector::class.java.getDeclaredField("firstFrameCallbacks")
        callbacksField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (callbacksField.get(AppLaunchPreInitCollector) as CopyOnWriteArrayList<*>).clear()

        // Reset registeredApplication via reflection
        val appField = AppLaunchPreInitCollector::class.java.getDeclaredField("registeredApplication")
        appField.isAccessible = true
        appField.set(AppLaunchPreInitCollector, null)
    }

    // region Helpers

    /**
     * Sets AppLaunchPreInitCollector state via reflection on the private _state AtomicReference.
     */
    private fun setCollectorState(state: AppLaunchPreInitCollector.State) {
        val stateField = AppLaunchPreInitCollector::class.java.getDeclaredField("_state")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (stateField.get(AppLaunchPreInitCollector) as AtomicReference<Any>).set(state)
    }

    /**
     * Sets all public data fields on AppLaunchPreInitCollector directly.
     * Fields are public vars so no reflection needed.
     */
    private fun configureCollectorData(
        processStartNs: Long,
        activityOnCreateNs: Long,
        firstFrameNs: Long = 0L,
        hasSavedInstanceState: Boolean = false,
        isFirstActivityForProcess: Boolean = true,
        activity: Activity? = null
    ) {
        AppLaunchPreInitCollector.processStartNs = processStartNs
        AppLaunchPreInitCollector.activityOnCreateNs = activityOnCreateNs
        AppLaunchPreInitCollector.firstFrameNs = firstFrameNs
        AppLaunchPreInitCollector.hasSavedInstanceState = hasSavedInstanceState
        AppLaunchPreInitCollector.isFirstActivityForProcess = isFirstActivityForProcess
        AppLaunchPreInitCollector.activity = if (activity != null) WeakReference(activity) else null
    }

    /**
     * Reads firstFrameCallbacks from AppLaunchPreInitCollector via reflection.
     */
    @Suppress("UNCHECKED_CAST")
    private fun getFirstFrameCallbacks(): List<(Long) -> Unit> {
        val callbacksField = AppLaunchPreInitCollector::class.java.getDeclaredField("firstFrameCallbacks")
        callbacksField.isAccessible = true
        return (callbacksField.get(AppLaunchPreInitCollector) as CopyOnWriteArrayList<(Long) -> Unit>).toList()
    }

    // endregion

    // region INT-01: 5-branch dispatch exists

    @Test
    fun `M call default detector W initRumAppStartupDetector() { collector NOT_INSTALLED }`() {
        // Given — state is already NOT_INSTALLED (default)
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.NOT_INSTALLED)

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then — default detector is created (field is non-null)
        val detector = testedFeature.getFieldValue<RumAppStartupDetector?, RumFeature>(
            "rumAppStartupDetector",
            RumFeature::class.java
        )
        assertThat(detector).isNotNull()
    }

    @Test
    fun `M call default detector W initRumAppStartupDetector() { collector IDLE }`() {
        // Given
        setCollectorState(AppLaunchPreInitCollector.State.IDLE)

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then — default detector created AND claim() was called (state -> CLAIMED)
        val detector = testedFeature.getFieldValue<RumAppStartupDetector?, RumFeature>(
            "rumAppStartupDetector",
            RumFeature::class.java
        )
        assertThat(detector).isNotNull()
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.CLAIMED)
    }

    // endregion

    // region INT-02: NOT_INSTALLED/IDLE route to default; IDLE calls claim

    @Test
    fun `M call claim and use default W initRumAppStartupDetector() { collector IDLE }`() {
        // Given
        setCollectorState(AppLaunchPreInitCollector.State.IDLE)

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then — claim() transitions IDLE -> CLAIMED
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.CLAIMED)
    }

    // endregion

    // region INT-03: CAPTURING branch

    @Test
    fun `M not send app start event during onInitialize W initRumAppStartupDetector() { collector CAPTURING }`() {
        // Given — GlobalRumMonitor is registered before onInitialize in tests, but in production
        // the real monitor is registered AFTER onInitialize returns. The CAPTURING branch now
        // defers events to the first-frame callback to avoid sending to a NoOp monitor.
        val mockActivity = mock<Activity>()
        setCollectorState(AppLaunchPreInitCollector.State.CAPTURING)
        configureCollectorData(
            processStartNs = 100_000L,
            activityOnCreateNs = 200_000L,
            isFirstActivityForProcess = true,
            activity = mockActivity
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then — sendAppStartEvent is NOT called immediately; deferred to first-frame callback
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
    }

    @Test
    fun `M send app start and TTID on first frame callback W initRumAppStartupDetector() { collector CAPTURING }`() {
        // Given
        val mockActivity = mock<Activity>()
        setCollectorState(AppLaunchPreInitCollector.State.CAPTURING)
        configureCollectorData(
            processStartNs = 100_000L,
            activityOnCreateNs = 200_000L,
            isFirstActivityForProcess = true,
            activity = mockActivity
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Simulate first frame callback firing (after Rum.enable() would have returned in prod)
        val fakeFirstFrameNs = 300_000L
        setCollectorState(AppLaunchPreInitCollector.State.COMPLETE)
        AppLaunchPreInitCollector.firstFrameNs = fakeFirstFrameNs
        val callbacks = getFirstFrameCallbacks()
        callbacks.forEach { it(fakeFirstFrameNs) }

        // Then — both sendAppStartEvent and sendTTIDEvent are called from the callback
        verify(mockRumMonitor, times(1)).sendAppStartEvent(any())
        val ttidCaptor = argumentCaptor<RumTTIDInfo>()
        verify(mockRumMonitor, times(1)).sendTTIDEvent(ttidCaptor.capture())
        assertThat(ttidCaptor.firstValue.durationNs).isEqualTo(fakeFirstFrameNs - 100_000L)
    }

    // endregion

    // region INT-04: COMPLETE branch

    @Test
    fun `M not send events during onInitialize W initRumAppStartupDetector() { collector COMPLETE }`() {
        // Given — events are deferred to pendingPreLaunchAction (invoked by Rum.kt after registerIfAbsent)
        val mockActivity = mock<Activity>()
        setCollectorState(AppLaunchPreInitCollector.State.COMPLETE)
        configureCollectorData(
            processStartNs = 100_000L,
            activityOnCreateNs = 200_000L,
            firstFrameNs = 300_000L,
            isFirstActivityForProcess = true,
            activity = mockActivity
        )

        // When — onInitialize runs but pendingPreLaunchAction has not been invoked yet
        testedFeature.onInitialize(appContext.mockInstance)

        // Then — events NOT sent yet (action pending Rum.kt dispatch)
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
        verify(mockRumMonitor, never()).sendTTIDEvent(any())
    }

    @Test
    fun `M send app start and TTID W initRumAppStartupDetector() { collector COMPLETE + action invoked }`() {
        // Given
        val mockActivity = mock<Activity>()
        setCollectorState(AppLaunchPreInitCollector.State.COMPLETE)
        configureCollectorData(
            processStartNs = 100_000L,
            activityOnCreateNs = 200_000L,
            firstFrameNs = 300_000L,
            isFirstActivityForProcess = true,
            activity = mockActivity
        )

        // When — simulate what Rum.kt does after registerIfAbsent
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.pendingPreLaunchAction?.invoke()

        // Then — both events sent
        verify(mockRumMonitor, times(1)).sendAppStartEvent(any())
        verify(mockRumMonitor, times(1)).sendTTIDEvent(any())
    }

    @Test
    fun `M call onActivityStarted on NavigationViewTrackingStrategy W initRumAppStartupDetector() { COMPLETE }`() {
        // Given
        val mockActivity = mock<Activity>()
        val mockNavStrategy = mock<NavigationViewTrackingStrategy>()
        setCollectorState(AppLaunchPreInitCollector.State.COMPLETE)
        configureCollectorData(
            processStartNs = 100_000L,
            activityOnCreateNs = 200_000L,
            firstFrameNs = 300_000L,
            isFirstActivityForProcess = true,
            activity = mockActivity
        )
        // Rebuild feature with NavigationViewTrackingStrategy so viewTrackingStrategy is set
        testedFeature = RumFeature(
            mockSdkCore,
            fakeApplicationId.toString(),
            fakeConfiguration.copy(
                appStartupActivityPredicate = mockAppStartupActivityPredicate,
                viewTrackingStrategy = mockNavStrategy
            ),
            lateCrashReporterFactory = { mockLateCrashReporter }
        )
        GlobalRumMonitor.clear()
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)

        // When — simulate Rum.kt: onInitialize then dispatch pendingPreLaunchAction
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.pendingPreLaunchAction?.invoke()

        // Then — view tracking strategy is primed before startup events so the RUM executor
        // has a StartView in the queue before AppStart/TTID
        verify(mockNavStrategy).onActivityStarted(mockActivity)
        verify(mockRumMonitor, times(1)).sendAppStartEvent(any())
        verify(mockRumMonitor, times(1)).sendTTIDEvent(any())
    }

    @Test
    fun `M call onActivityResumed on ActivityViewTrackingStrategy W initRumAppStartupDetector() { COMPLETE }`() {
        // Given
        val mockActivity = mock<Activity>()
        val mockActivityStrategy = mock<ActivityViewTrackingStrategy>()
        setCollectorState(AppLaunchPreInitCollector.State.COMPLETE)
        configureCollectorData(
            processStartNs = 100_000L,
            activityOnCreateNs = 200_000L,
            firstFrameNs = 300_000L,
            isFirstActivityForProcess = true,
            activity = mockActivity
        )
        testedFeature = RumFeature(
            mockSdkCore,
            fakeApplicationId.toString(),
            fakeConfiguration.copy(
                appStartupActivityPredicate = mockAppStartupActivityPredicate,
                viewTrackingStrategy = mockActivityStrategy
            ),
            lateCrashReporterFactory = { mockLateCrashReporter }
        )
        GlobalRumMonitor.clear()
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)

        // When
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.pendingPreLaunchAction?.invoke()

        // Then
        verify(mockActivityStrategy).onActivityResumed(mockActivity)
        verify(mockRumMonitor, times(1)).sendAppStartEvent(any())
        verify(mockRumMonitor, times(1)).sendTTIDEvent(any())
    }

    @Test
    fun `M skip view tracking strategy W initRumAppStartupDetector() { collector COMPLETE + activity GCd }`() {
        // Given — constructScenario returns null when activity is GC'd, falls back to default detector
        val mockNavStrategy = mock<NavigationViewTrackingStrategy>()
        setCollectorState(AppLaunchPreInitCollector.State.COMPLETE)
        configureCollectorData(
            processStartNs = 100_000L,
            activityOnCreateNs = 200_000L,
            firstFrameNs = 300_000L
        )
        AppLaunchPreInitCollector.activity = WeakReference(null) // GC'd
        testedFeature = RumFeature(
            mockSdkCore,
            fakeApplicationId.toString(),
            fakeConfiguration.copy(
                appStartupActivityPredicate = mockAppStartupActivityPredicate,
                viewTrackingStrategy = mockNavStrategy
            ),
            lateCrashReporterFactory = { mockLateCrashReporter }
        )
        GlobalRumMonitor.clear()
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)

        // When — pendingPreLaunchAction is null because constructScenario returned null
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.pendingPreLaunchAction?.invoke()

        // Then — no attempt to prime view tracking
        verify(mockNavStrategy, never()).onActivityStarted(any())
    }

    // endregion

    // region INT-05: constructScenario Cold vs Warm

    @Test
    fun `M construct Cold scenario W initRumAppStartupDetector() { COMPLETE + gap less than 10s }`() {
        // Given — processStartNs=100_000_000L, activityOnCreateNs=100_500_000L, gap=500_000L (< 10s in ns)
        val mockActivity = mock<Activity>()
        setCollectorState(AppLaunchPreInitCollector.State.COMPLETE)
        configureCollectorData(
            processStartNs = 100_000_000L,
            activityOnCreateNs = 100_500_000L,
            firstFrameNs = 101_000_000L,
            isFirstActivityForProcess = true,
            activity = mockActivity
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.pendingPreLaunchAction?.invoke()

        // Then — scenario is Cold with initialTime.nanoTime == processStartNs
        val scenarioCaptor = argumentCaptor<RumStartupScenario>()
        verify(mockRumMonitor).sendAppStartEvent(scenarioCaptor.capture())
        val scenario = scenarioCaptor.firstValue
        assertThat(scenario).isInstanceOf(RumStartupScenario.Cold::class.java)
        assertThat(scenario.initialTime.nanoTime).isEqualTo(100_000_000L)
    }

    @Test
    fun `M construct WarmFirstActivity scenario W initRumAppStartupDetector() { COMPLETE + gap greater than 10s }`() {
        // Given — gap > 10s: activityOnCreateNs - processStartNs > 10_000_000_000L
        val mockActivity = mock<Activity>()
        setCollectorState(AppLaunchPreInitCollector.State.COMPLETE)
        configureCollectorData(
            processStartNs = 100_000_000L,
            activityOnCreateNs = 111_000_000_000L, // gap = ~110.9s >> 10s
            firstFrameNs = 111_500_000_000L,
            isFirstActivityForProcess = true,
            activity = mockActivity
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.pendingPreLaunchAction?.invoke()

        // Then — scenario is WarmFirstActivity with initialTime.nanoTime == activityOnCreateNs
        val scenarioCaptor = argumentCaptor<RumStartupScenario>()
        verify(mockRumMonitor).sendAppStartEvent(scenarioCaptor.capture())
        val scenario = scenarioCaptor.firstValue
        assertThat(scenario).isInstanceOf(RumStartupScenario.WarmFirstActivity::class.java)
        assertThat(scenario.initialTime.nanoTime).isEqualTo(111_000_000_000L)
    }

    @Test
    fun `M construct WarmAfterActivityDestroyed W initRumAppStartupDetector() { COMPLETE + notFirstActivity }`() {
        // Given
        val mockActivity = mock<Activity>()
        setCollectorState(AppLaunchPreInitCollector.State.COMPLETE)
        configureCollectorData(
            processStartNs = 100_000_000L,
            activityOnCreateNs = 100_500_000L,
            firstFrameNs = 101_000_000L,
            isFirstActivityForProcess = false, // not first — warm
            activity = mockActivity
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.pendingPreLaunchAction?.invoke()

        // Then — scenario is WarmAfterActivityDestroyed
        val scenarioCaptor = argumentCaptor<RumStartupScenario>()
        verify(mockRumMonitor).sendAppStartEvent(scenarioCaptor.capture())
        val scenario = scenarioCaptor.firstValue
        assertThat(scenario).isInstanceOf(RumStartupScenario.WarmAfterActivityDestroyed::class.java)
    }

    // endregion

    // region INT-06: predicate mismatch

    @Test
    fun `M fall back to default W initRumAppStartupDetector() { COMPLETE + predicate rejects activity }`() {
        // Given — predicate returns false for all activities
        whenever(mockAppStartupActivityPredicate.shouldTrackStartup(any())) doReturn false
        val mockActivity = mock<Activity>()
        setCollectorState(AppLaunchPreInitCollector.State.COMPLETE)
        configureCollectorData(
            processStartNs = 100_000_000L,
            activityOnCreateNs = 100_500_000L,
            firstFrameNs = 101_000_000L,
            activity = mockActivity
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then — sendAppStartEvent never called AND default detector is created
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
        val detector = testedFeature.getFieldValue<RumAppStartupDetector?, RumFeature>(
            "rumAppStartupDetector",
            RumFeature::class.java
        )
        assertThat(detector).isNotNull()
    }

    @Test
    fun `M fall back to default W initRumAppStartupDetector() { CAPTURING + predicate rejects activity }`() {
        // Given — predicate returns false for all activities
        whenever(mockAppStartupActivityPredicate.shouldTrackStartup(any())) doReturn false
        val mockActivity = mock<Activity>()
        setCollectorState(AppLaunchPreInitCollector.State.CAPTURING)
        configureCollectorData(
            processStartNs = 100_000_000L,
            activityOnCreateNs = 100_500_000L,
            activity = mockActivity
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then — sendAppStartEvent never called AND default detector is created
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
        val detector = testedFeature.getFieldValue<RumAppStartupDetector?, RumFeature>(
            "rumAppStartupDetector",
            RumFeature::class.java
        )
        assertThat(detector).isNotNull()
    }

    // endregion

    // region INT-07: CLAIMED branch

    @Test
    fun `M log warn and use default W initRumAppStartupDetector() { collector CLAIMED }`() {
        // Given
        setCollectorState(AppLaunchPreInitCollector.State.CLAIMED)

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then — WARN log emitted and default detector created
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            any(),
            isNull(),
            eq(false),
            isNull()
        )
        val detector = testedFeature.getFieldValue<RumAppStartupDetector?, RumFeature>(
            "rumAppStartupDetector",
            RumFeature::class.java
        )
        assertThat(detector).isNotNull()
    }

    // endregion

    // region INT-08: null activity.get()

    @Test
    fun `M log warn and not send events W initRumAppStartupDetector() { COMPLETE + activity GCd }`() {
        // Given — activity has been GC'd (WeakReference.get() returns null)
        setCollectorState(AppLaunchPreInitCollector.State.COMPLETE)
        configureCollectorData(
            processStartNs = 100_000_000L,
            activityOnCreateNs = 100_500_000L,
            firstFrameNs = 101_000_000L,
            activity = null // simulates GC — WeakReference(null)
        )
        AppLaunchPreInitCollector.activity = WeakReference(null)

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then — sendAppStartEvent never called, WARN logged
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            any(),
            isNull(),
            eq(false),
            isNull()
        )
    }

    @Test
    fun `M log warn and fall back to default W initRumAppStartupDetector() { CAPTURING + activity GCd }`() {
        // Given — CAPTURING state, activity GC'd
        setCollectorState(AppLaunchPreInitCollector.State.CAPTURING)
        configureCollectorData(
            processStartNs = 100_000_000L,
            activityOnCreateNs = 100_500_000L,
            activity = null // simulates GC
        )
        AppLaunchPreInitCollector.activity = WeakReference(null)

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then — sendAppStartEvent never called, WARN logged, default detector falls back
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            any(),
            isNull(),
            eq(false),
            isNull()
        )
        val detector = testedFeature.getFieldValue<RumAppStartupDetector?, RumFeature>(
            "rumAppStartupDetector",
            RumFeature::class.java
        )
        assertThat(detector).isNotNull()
    }

    // endregion

    // region monitor not available at dispatch time

    @Test
    fun `M skip events W pendingPreLaunchAction() { GlobalRumMonitor not an AdvancedRumMonitor }`() {
        // Given — COMPLETE state with valid data
        val mockActivity = mock<Activity>()
        setCollectorState(AppLaunchPreInitCollector.State.COMPLETE)
        configureCollectorData(
            processStartNs = 100_000L,
            activityOnCreateNs = 200_000L,
            firstFrameNs = 300_000L,
            isFirstActivityForProcess = true,
            activity = mockActivity
        )
        testedFeature.onInitialize(appContext.mockInstance)

        // Clear the registered monitor so GlobalRumMonitor.get() returns a NoOp (not AdvancedRumMonitor)
        GlobalRumMonitor.clear()

        // When
        testedFeature.pendingPreLaunchAction?.invoke()

        // Then — early return: no events sent, no crash
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
        verify(mockRumMonitor, never()).sendTTIDEvent(any())
    }

    @Test
    fun `M skip first-frame events W addFirstFrameCallback() { CAPTURING + monitor not AdvancedRumMonitor }`() {
        // Given — CAPTURING state
        val mockActivity = mock<Activity>()
        setCollectorState(AppLaunchPreInitCollector.State.CAPTURING)
        configureCollectorData(
            processStartNs = 100_000L,
            activityOnCreateNs = 200_000L,
            isFirstActivityForProcess = true,
            activity = mockActivity
        )
        testedFeature.onInitialize(appContext.mockInstance)

        // Clear monitor so GlobalRumMonitor.get() inside the callback returns NoOp
        GlobalRumMonitor.clear()

        // When — fire the first-frame callback
        val fakeFirstFrameNs = 300_000L
        setCollectorState(AppLaunchPreInitCollector.State.COMPLETE)
        AppLaunchPreInitCollector.firstFrameNs = fakeFirstFrameNs
        val callbacks = getFirstFrameCallbacks()
        callbacks.forEach { it(fakeFirstFrameNs) }

        // Then — early return inside callback: no events sent, no crash
        verify(mockRumMonitor, never()).sendAppStartEvent(any())
        verify(mockRumMonitor, never()).sendTTIDEvent(any())
    }

    @Test
    fun `M not call view tracking strategy W pendingPreLaunchAction() { strategy is null }`() {
        // Given — null viewTrackingStrategy hits the else -> Unit branch
        val mockActivity = mock<Activity>()
        setCollectorState(AppLaunchPreInitCollector.State.COMPLETE)
        configureCollectorData(
            processStartNs = 100_000L,
            activityOnCreateNs = 200_000L,
            firstFrameNs = 300_000L,
            isFirstActivityForProcess = true,
            activity = mockActivity
        )
        testedFeature = RumFeature(
            mockSdkCore,
            fakeApplicationId.toString(),
            fakeConfiguration.copy(
                appStartupActivityPredicate = mockAppStartupActivityPredicate,
                viewTrackingStrategy = null
            ),
            lateCrashReporterFactory = { mockLateCrashReporter }
        )
        GlobalRumMonitor.clear()
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)

        // When
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.pendingPreLaunchAction?.invoke()

        // Then — no NPE, events still sent
        verify(mockRumMonitor, times(1)).sendAppStartEvent(any())
        verify(mockRumMonitor, times(1)).sendTTIDEvent(any())
    }

    // endregion

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, mainLooper)
        }
    }
}
