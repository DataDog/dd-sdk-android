/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.startup.RumSessionScopeStartupManagerImpl.Companion.MAX_TTID_DURATION_NS
import com.datadog.android.rum.startup.AppStartupActivityPredicate
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumAppStartupDetectorImplTest {
    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var listener: RumAppStartupDetector.Listener

    @BoolForgery
    private var fakeIsAtLeastQ: Boolean = false

    @Mock
    private lateinit var buildSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    private lateinit var rumFirstDrawTimeReporter: RumFirstDrawTimeReporter

    @Mock
    private lateinit var activity: Activity

    private var currentTime: Duration = 0.nanoseconds

    @BeforeEach
    fun `set up`() {
        whenever(activity.isChangingConfigurations) doReturn false
        whenever(rumFirstDrawTimeReporter.subscribeToFirstFrameDrawn(any(), any())).doAnswer {
            val handle = mock<RumFirstDrawTimeReporter.Handle>()
            handle
        }
    }

    @Test
    fun `M registerActivityLifecycleCallbacks W RumAppStartupDetector constructor`() {
        createDetector()
        verify(application).registerActivityLifecycleCallbacks(any())
        verifyNoMoreInteractions(application)
    }

    @Test
    fun `M detect Cold scenario W RumAppStartupDetector {small gap}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given
        val detector = createDetector()
        autoDrawFirstFrame(activity)

        currentTime += 3.seconds

        // When
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // Then
        val expectedScenario = RumStartupScenario.Cold(
            initialTime = Time(0, 0),
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
            activity = activity.wrapWeak(),
            appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
        )
        inOrder(listener) {
            verify(listener).onAppStartupDetected(matchingScenario(expectedScenario))
            verify(listener).onTTIDComputed(
                matchingScenario(expectedScenario),
                eq(3.seconds.inWholeNanoseconds),
                eq(false)
            )
        }
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M detect WarmFirstActivity scenario W RumAppStartupDetector {large gap}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        val detector = createDetector()
        autoDrawFirstFrame(activity)

        currentTime += 11.seconds
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        val expectedScenario = RumStartupScenario.WarmFirstActivity(
            initialTime = Time(
                nanoTime = currentTime.inWholeNanoseconds,
                timestamp = currentTime.inWholeMilliseconds
            ),
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
            activity = activity.wrapWeak(),
            appStartActivityOnCreateGapNs = 11.seconds.inWholeNanoseconds
        )
        inOrder(listener) {
            verify(listener).onAppStartupDetected(matchingScenario(expectedScenario))
            verify(listener).onTTIDComputed(
                matchingScenario(expectedScenario),
                eq(0.seconds.inWholeNanoseconds),
                eq(false)
            )
        }
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M detect WarmAfterActivityDestroyed scenario W RumAppStartupDetector {1st Cold scenario}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean,
        @BoolForgery hasSavedInstanceStateBundle2: Boolean
    ) {
        // Given
        val detector = createDetector()
        autoDrawFirstFrame(activity)

        currentTime += 3.seconds

        // When
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        detector.onActivityDestroyed(activity)

        currentTime += 30.seconds

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2
        )

        // Then
        val expectedColdScenario = RumStartupScenario.Cold(
            initialTime = Time(0, 0),
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
            activity = activity.wrapWeak(),
            appStartActivityOnCreateGapNs = 30.seconds.inWholeNanoseconds
        )
        val expectedWarmScenario = RumStartupScenario.WarmAfterActivityDestroyed(
            initialTime = Time(
                nanoTime = currentTime.inWholeNanoseconds,
                timestamp = currentTime.inWholeMilliseconds
            ),
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2,
            activity = activity.wrapWeak()
        )
        inOrder(listener) {
            verify(listener).onAppStartupDetected(matchingScenario(expectedColdScenario))
            verify(listener).onTTIDComputed(
                matchingScenario(expectedColdScenario),
                eq(3.seconds.inWholeNanoseconds),
                eq(false)
            )

            verify(listener).onAppStartupDetected(matchingScenario(expectedWarmScenario))
            verify(listener).onTTIDComputed(
                matchingScenario(expectedWarmScenario),
                eq(0L),
                eq(false)
            )
        }
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M not detect any scenario W RumAppStartupDetector {1st Cold scenario and configuration change}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given
        val detector = createDetector()

        currentTime += 3.seconds

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        whenever(activity.isChangingConfigurations) doReturn true

        // When
        detector.onActivityDestroyed(activity)

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // Then
        verify(listener).onAppStartupDetected(
            matchingScenario(
                RumStartupScenario.Cold(
                    initialTime = Time(0, 0),
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                    activity = activity.wrapWeak(),
                    appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
                )
            )
        )
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M detect Cold only W RumAppStartupDetector {1st Cold, 1st activity stopped and another activity created}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean,
        @BoolForgery hasSavedInstanceStateBundle2: Boolean
    ) {
        // Given
        val detector = createDetector()

        currentTime += 3.seconds

        // When
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        detector.onActivityStarted(activity)
        detector.onActivityResumed(activity)
        detector.onActivityPaused(activity)
        detector.onActivityStopped(activity)

        val activity2 = mock<Activity>()

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity2,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2
        )

        // Then
        verify(listener).onAppStartupDetected(
            matchingScenario(
                RumStartupScenario.Cold(
                    initialTime = Time(0, 0),
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                    activity = activity.wrapWeak(),
                    appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
                )
            )
        )
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M detect WarmAfterActivityDestroyed W RumAppStartupDetector {1st Cold, activity destroyed another created}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean,
        @BoolForgery hasSavedInstanceStateBundle2: Boolean
    ) {
        // Given
        val detector = createDetector()
        autoDrawFirstFrame(activity)

        currentTime += 3.seconds

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // When
        destroyActivity(detector, activity)

        currentTime += 30.seconds

        val activity2 = mock<Activity>()
        autoDrawFirstFrame(activity2)

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity2,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2
        )

        // Then
        val expectedColdScenario = RumStartupScenario.Cold(
            initialTime = Time(0, 0),
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
            activity = activity.wrapWeak(),
            appStartActivityOnCreateGapNs = 30.seconds.inWholeNanoseconds
        )
        val expectedWarmScenario = RumStartupScenario.WarmAfterActivityDestroyed(
            initialTime = Time(
                nanoTime = currentTime.inWholeNanoseconds,
                timestamp = currentTime.inWholeMilliseconds
            ),
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2,
            activity = activity2.wrapWeak()
        )
        inOrder(listener) {
            verify(listener).onAppStartupDetected(matchingScenario(expectedColdScenario))
            verify(listener).onTTIDComputed(
                matchingScenario(expectedColdScenario),
                eq(3.seconds.inWholeNanoseconds),
                eq(false)
            )

            verify(listener).onAppStartupDetected(matchingScenario(expectedWarmScenario))
            verify(listener).onTTIDComputed(
                matchingScenario(expectedWarmScenario),
                eq(0L),
                eq(false)
            )
        }
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M detect WarmAfterActivityDestroyed W RumAppStartupDetector {2 activities created, destroyed and 3rd created}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean,
        @BoolForgery hasSavedInstanceStateBundle2: Boolean,
        @BoolForgery hasSavedInstanceStateBundle3: Boolean
    ) {
        // Given
        val detector = createDetector()

        autoDrawFirstFrame(activity)

        currentTime += 3.seconds

        // When
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        detector.onActivityStarted(activity)
        detector.onActivityResumed(activity)
        detector.onActivityPaused(activity)
        detector.onActivityStopped(activity)

        currentTime += 30.seconds

        val activity2 = mock<Activity>()

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity2,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2
        )

        destroyActivity(detector, activity2)

        detector.onActivityDestroyed(activity)

        currentTime += 30.seconds

        val activity3 = mock<Activity>()
        autoDrawFirstFrame(activity3)

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity3,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle3
        )

        // Then
        val expectedColdScenario = RumStartupScenario.Cold(
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
            activity = activity.wrapWeak(),
            appStartActivityOnCreateGapNs = 30.seconds.inWholeNanoseconds,
            initialTime = Time(0, 0)
        )
        val expectedWarmScenario = RumStartupScenario.WarmAfterActivityDestroyed(
            initialTime = Time(
                nanoTime = currentTime.inWholeNanoseconds,
                timestamp = currentTime.inWholeMilliseconds
            ),
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle3,
            activity = activity3.wrapWeak()
        )
        inOrder(listener) {
            verify(listener).onAppStartupDetected(matchingScenario(expectedColdScenario))
            verify(listener).onTTIDComputed(
                matchingScenario(expectedColdScenario),
                eq(3.seconds.inWholeNanoseconds),
                eq(false)
            )

            verify(listener).onAppStartupDetected(matchingScenario(expectedWarmScenario))
            verify(listener).onTTIDComputed(
                matchingScenario(expectedWarmScenario),
                eq(0L),
                eq(false)
            )
        }
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M report TTID for second activity W first activity excluded by predicate {interstitial activity}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle1: Boolean,
        @BoolForgery hasSavedInstanceStateBundle2: Boolean
    ) {
        // Given - predicate that excludes the first activity
        val interstitialActivity = mock<Activity>()
        val mainActivity = mock<Activity>()

        autoDrawFirstFrame(mainActivity)

        val predicate = AppStartupActivityPredicate { activity ->
            activity != interstitialActivity
        }

        val detector = createDetector(appStartupActivityPredicate = predicate)

        currentTime += 3.seconds

        // When - interstitial activity is created first (excluded)
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = interstitialActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle1
        )

        // Then - no scenario detected yet
        verifyNoMoreInteractions(listener)

        // When - interstitial activity is destroyed and main activity is created
        destroyActivity(detector, interstitialActivity)

        currentTime += 1.seconds

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = mainActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2
        )

        // Then - scenario detected for main activity (first non-excluded)
        val expectedScenario = RumStartupScenario.Cold(
            initialTime = Time(0, 0),
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2,
            activity = mainActivity.wrapWeak(),
            appStartActivityOnCreateGapNs = 4.seconds.inWholeNanoseconds
        )
        inOrder(listener) {
            verify(listener).onAppStartupDetected(matchingScenario(expectedScenario))
            verify(listener).onTTIDComputed(
                matchingScenario(expectedScenario),
                eq(4.seconds.inWholeNanoseconds),
                eq(false)
            )
        }

        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M not report TTID W all activities excluded by predicate`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given - predicate that excludes all activities
        val predicate = AppStartupActivityPredicate { false }
        val detector = createDetector(appStartupActivityPredicate = predicate)

        currentTime += 3.seconds

        // When
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // Then - no scenario detected
        verifyNoInteractions(listener)
    }

    @Test
    fun `M report TTID for first included activity W multiple excluded activities`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle1: Boolean,
        @BoolForgery hasSavedInstanceStateBundle2: Boolean,
        @BoolForgery hasSavedInstanceStateBundle3: Boolean
    ) {
        // Given - predicate that excludes first two activities
        val excludedActivity1 = mock<Activity>()
        val excludedActivity2 = mock<Activity>()
        val includedActivity = mock<Activity>()

        autoDrawFirstFrame(includedActivity)

        val predicate = AppStartupActivityPredicate { activity ->
            activity != excludedActivity1 && activity != excludedActivity2
        }

        val detector = createDetector(appStartupActivityPredicate = predicate)

        currentTime += 3.seconds

        // When - first excluded activity
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = excludedActivity1,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle1
        )

        // Then - no scenario yet
        verifyNoMoreInteractions(listener)

        // When - second excluded activity (while first is still alive)
        currentTime += 1.seconds

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = excludedActivity2,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2
        )

        // Then - still no scenario
        verifyNoMoreInteractions(listener)

        // When - included activity is created
        currentTime += 1.seconds

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = includedActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle3
        )

        // Then - scenario detected for included activity
        val expectedScenario = RumStartupScenario.Cold(
            initialTime = Time(0, 0),
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle3,
            activity = includedActivity.wrapWeak(),
            appStartActivityOnCreateGapNs = 5.seconds.inWholeNanoseconds
        )
        inOrder(listener) {
            verify(listener).onAppStartupDetected(matchingScenario(expectedScenario))
            verify(listener).onTTIDComputed(
                matchingScenario(expectedScenario),
                eq(5.seconds.inWholeNanoseconds),
                eq(false)
            )
        }

        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M use default behavior W no predicate specified`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given - default predicate (allows all)
        val detector = createDetector()

        currentTime += 3.seconds

        // When
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // Then - scenario detected (backward compatibility)
        verify(listener).onAppStartupDetected(
            matchingScenario(
                RumStartupScenario.Cold(
                    initialTime = Time(0, 0),
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                    activity = activity.wrapWeak(),
                    appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
                )
            )
        )

        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M maintain counter consistency W mutable predicate changes between create and destroy`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given - a mutable predicate that changes its result
        val activity1 = mock<Activity>()
        val activity2 = mock<Activity>()
        var shouldTrackActivity1 = true

        autoDrawFirstFrame(activity1)
        autoDrawFirstFrame(activity2)

        val mutablePredicate = AppStartupActivityPredicate { activity ->
            if (activity == activity1) shouldTrackActivity1 else true
        }

        val detector = createDetector(appStartupActivityPredicate = mutablePredicate)

        currentTime += 3.seconds

        // When - activity created with predicate returning true
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity1,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // Then - scenario detected
        val expectedColdScenario = RumStartupScenario.Cold(
            initialTime = Time(0, 0),
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
            activity = activity1.wrapWeak(),
            appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
        )
        verify(listener).onAppStartupDetected(matchingScenario(expectedColdScenario))
        verify(listener).onTTIDComputed(
            matchingScenario(expectedColdScenario),
            eq(3.seconds.inWholeNanoseconds),
            eq(false)
        )

        // When - predicate changes to return false for activity1
        shouldTrackActivity1 = false

        // And - activity is destroyed (predicate now returns false, but stored value was true)
        destroyActivity(detector, activity1)

        // When - second activity is created
        currentTime += 1.seconds

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity2,
            hasSavedInstanceStateBundle = false
        )

        // Then - scenario detected because counter correctly went from 1 -> 0 -> 1
        // (not stuck at 1 due to predicate mismatch)
        val expectedWarmScenario = RumStartupScenario.WarmAfterActivityDestroyed(
            initialTime = Time(
                timestamp = 4.seconds.inWholeMilliseconds,
                nanoTime = 4.seconds.inWholeNanoseconds
            ),
            hasSavedInstanceStateBundle = false,
            activity = activity2.wrapWeak()
        )
        verify(listener).onAppStartupDetected(matchingScenario(expectedWarmScenario))
        verify(listener).onTTIDComputed(
            matchingScenario(expectedWarmScenario),
            eq(0.seconds.inWholeNanoseconds),
            eq(false)
        )

        verifyNoMoreInteractions(listener)
    }

    // region pendingScenario management tests

    @Test
    fun `M create fresh startup scenario W stale pendingScenario exists on re-launch`(
        forge: Forge
    ) {
        // Given - first launch creates a pending scenario (e.g. interstitial that never drew)
        val detector = createDetector()
        currentTime += 3.seconds
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = false
        )

        // Simulate the interstitial activity being fully destroyed (app goes background)
        destroyActivity(detector, activity)

        // Advance time beyond the TTID timeout (1 minute)
        currentTime += MAX_TTID_DURATION_NS.nanoseconds + 1.seconds

        // When - user re-launches the app (new activity in the same process)
        val secondActivity: Activity = mock()
        whenever(secondActivity.isChangingConfigurations) doReturn false
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = secondActivity,
            hasSavedInstanceStateBundle = false
        )

        // Then - a fresh scenario was detected for the new activity
        verify(listener, times(2)).onAppStartupDetected(any())
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M not subscribe second activity W second activity fails predicate`(
        forge: Forge
    ) {
        // Given
        val secondActivity: Activity = mock()
        val predicate = AppStartupActivityPredicate { it !== secondActivity }
        val detector = createDetector(appStartupActivityPredicate = predicate)
        currentTime += 3.seconds
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = false
        )

        // When
        currentTime += 1.seconds
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = secondActivity,
            hasSavedInstanceStateBundle = false
        )

        // Then
        inOrder(listener, rumFirstDrawTimeReporter) {
            verify(listener).onAppStartupDetected(any())
            verify(rumFirstDrawTimeReporter).subscribeToFirstFrameDrawn(eq(activity), any())
        }
        verifyNoMoreInteractions(listener, rumFirstDrawTimeReporter)
    }

    @Test
    fun `M not subscribe second activity W pendingScenario cleared by first frame draw`(
        forge: Forge
    ) {
        // Given
        val detector = createDetector()
        autoDrawFirstFrame(activity)
        currentTime += 3.seconds
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = false
        )

        val secondActivity: Activity = mock()

        // When
        currentTime += 1.seconds
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = secondActivity,
            hasSavedInstanceStateBundle = false
        )

        // Then
        inOrder(listener, rumFirstDrawTimeReporter) {
            verify(listener).onAppStartupDetected(any())
            verify(rumFirstDrawTimeReporter).subscribeToFirstFrameDrawn(eq(activity), any())
            verify(listener).onTTIDComputed(any(), any(), any())
        }
        verifyNoMoreInteractions(listener, rumFirstDrawTimeReporter)
    }

    @Test
    fun `M not emit second startup W first activity destroyed before next created (async interstitial)`(
        forge: Forge
    ) {
        // Given - first activity created, startup detected, then fully destroyed before
        // the next activity is created (async interstitial pattern: finish() + Handler.postDelayed)
        val detector = createDetector()
        currentTime += 3.seconds
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = false
        )

        destroyActivity(detector, activity)

        currentTime += 1.seconds
        val secondActivity: Activity = mock()

        // When - next activity created while pendingScenario still exists
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = secondActivity,
            hasSavedInstanceStateBundle = false
        )

        // Then - onAppStartupDetected must NOT be called a second time
        inOrder(listener, rumFirstDrawTimeReporter) {
            verify(listener, times(1)).onAppStartupDetected(any())
            verify(rumFirstDrawTimeReporter).subscribeToFirstFrameDrawn(eq(activity), any())
            verify(rumFirstDrawTimeReporter).subscribeToFirstFrameDrawn(eq(secondActivity), any())
        }
        verifyNoMoreInteractions(listener, rumFirstDrawTimeReporter)
    }

    // endregion

    // region unsubscribe and TTID callback tests

    @Test
    fun `M call onTTIDComputed with wasForwarded=true W forwarded activity first frame drawn`(
        forge: Forge
    ) {
        // Given
        val detector = createDetector()
        val secondActivity: Activity = mock()
        autoDrawFirstFrame(secondActivity, delay = 1.seconds)

        currentTime += 3.seconds
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = false
        )

        // When
        currentTime += 1.seconds
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = secondActivity,
            hasSavedInstanceStateBundle = false
        )

        // Then
        inOrder(listener) {
            verify(listener).onAppStartupDetected(any())
            verify(listener).onTTIDComputed(any(), eq(5.seconds.inWholeNanoseconds), eq(true))
        }
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M only call onTTIDComputed once W both first and forwarded activity draw`(
        forge: Forge
    ) {
        // Given
        val detector = createDetector()
        val secondActivity: Activity = mock()
        autoDrawFirstFrame(activity, delay = 1.seconds)
        autoDrawFirstFrame(secondActivity, delay = 2.seconds)

        // When
        currentTime += 3.seconds
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = false
        )

        currentTime += 1.seconds
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = secondActivity,
            hasSavedInstanceStateBundle = false
        )

        // Then - onTTIDComputed should only be called once (first activity drew, clearing scenario)
        val expectedScenario = RumStartupScenario.Cold(
            initialTime = Time(0, 0),
            hasSavedInstanceStateBundle = false,
            activity = activity.wrapWeak(),
            appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
        )
        inOrder(listener) {
            verify(listener).onAppStartupDetected(matchingScenario(expectedScenario))
            verify(listener).onTTIDComputed(
                matchingScenario(expectedScenario),
                eq(4.seconds.inWholeNanoseconds),
                eq(false)
            )
        }
        verifyNoMoreInteractions(listener)
    }

    // endregion

    private fun autoDrawFirstFrame(activity: Activity, delay: Duration = 0.seconds) {
        whenever(rumFirstDrawTimeReporter.subscribeToFirstFrameDrawn(eq(activity), any())).doAnswer {
            val callback = it.getArgument<RumFirstDrawTimeReporter.Callback>(1)
            val handle = mock<RumFirstDrawTimeReporter.Handle>()
            currentTime += delay
            callback.onFirstFrameDrawn(currentTime.inWholeNanoseconds)
            handle
        }
    }

    private fun createDetector(
        appStartupActivityPredicate: AppStartupActivityPredicate = AppStartupActivityPredicate { true }
    ): RumAppStartupDetectorImpl {
        whenever(buildSdkVersionProvider.isAtLeastQ) doReturn fakeIsAtLeastQ

        val detector = RumAppStartupDetectorImpl(
            application = application,
            buildSdkVersionProvider = buildSdkVersionProvider,
            appStartupTimeProvider = { Time(0, 0) },
            timeProvider = {
                Time(
                    timestamp = currentTime.inWholeMilliseconds,
                    nanoTime = currentTime.inWholeNanoseconds
                )
            },
            listener = listener,
            appStartupActivityPredicate = appStartupActivityPredicate,
            rumFirstDrawTimeReporter = rumFirstDrawTimeReporter
        )

        return detector
    }

    private fun triggerBeforeCreated(
        forge: Forge,
        detector: RumAppStartupDetectorImpl,
        activity: Activity,
        hasSavedInstanceStateBundle: Boolean
    ) {
        val bundle = if (hasSavedInstanceStateBundle) {
            Bundle().apply {
                putString(forge.anAlphabeticalString(), forge.anAlphabeticalString())
            }
        } else {
            null
        }
        if (fakeIsAtLeastQ) {
            detector.onActivityPreCreated(activity, bundle)
        } else {
            detector.onActivityCreated(activity, bundle)
        }
    }

    private fun destroyActivity(detector: RumAppStartupDetectorImpl, activity: Activity) {
        detector.onActivityStarted(activity)
        detector.onActivityResumed(activity)
        detector.onActivityPaused(activity)
        detector.onActivityStopped(activity)
        detector.onActivityDestroyed(activity)
    }

    private fun matchingScenario(expected: RumStartupScenario): RumStartupScenario {
        return argThat { actual ->
            (actual.activity.get() == expected.activity.get()) &&
                (actual.hasSavedInstanceStateBundle == expected.hasSavedInstanceStateBundle) &&
                (actual.initialTime == expected.initialTime) &&
                (actual.javaClass == expected.javaClass)
        }
    }
}

private fun Activity.wrapWeak() = WeakReference(this)
