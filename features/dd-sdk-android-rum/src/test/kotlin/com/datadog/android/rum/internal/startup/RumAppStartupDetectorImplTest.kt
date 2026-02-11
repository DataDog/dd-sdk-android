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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
    private lateinit var activity: Activity

    private var currentTime: Duration = 0.nanoseconds

    @BeforeEach
    fun `set up`() {
        whenever(activity.isChangingConfigurations) doReturn false
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

        currentTime += 3.seconds

        // When
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // Then
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = activity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M detect WarmFirstActivity scenario W RumAppStartupDetector {large gap}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        val detector = createDetector()

        currentTime += 11.seconds
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        listener.verifyScenarioDetected(
            RumStartupScenario.WarmFirstActivity(
                initialTime = Time(
                    nanoTime = currentTime.inWholeNanoseconds,
                    timestamp = currentTime.inWholeMilliseconds
                ),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = activity.wrapWeak(),
                appStartActivityOnCreateGapNs = 11.seconds.inWholeNanoseconds
            )
        )
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
        inOrder(listener) {
            listener.verifyScenarioDetected(
                RumStartupScenario.Cold(
                    initialTime = Time(0, 0),
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                    activity = activity.wrapWeak(),
                    appStartActivityOnCreateGapNs = 30.seconds.inWholeNanoseconds
                )
            )

            listener.verifyScenarioDetected(
                RumStartupScenario.WarmAfterActivityDestroyed(
                    initialTime = Time(
                        nanoTime = currentTime.inWholeNanoseconds,
                        timestamp = currentTime.inWholeMilliseconds
                    ),
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2,
                    activity = activity.wrapWeak()
                )
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
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = activity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
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
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = activity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
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

        currentTime += 3.seconds

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // When
        detector.onActivityStarted(activity)
        detector.onActivityResumed(activity)
        detector.onActivityPaused(activity)
        detector.onActivityStopped(activity)
        detector.onActivityDestroyed(activity)

        currentTime += 30.seconds

        val activity2 = mock<Activity>()

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity2,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2
        )

        // Then
        inOrder(listener) {
            listener.verifyScenarioDetected(
                RumStartupScenario.Cold(
                    initialTime = Time(0, 0),
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                    activity = activity.wrapWeak(),
                    appStartActivityOnCreateGapNs = 30.seconds.inWholeNanoseconds
                )
            )
            listener.verifyScenarioDetected(
                RumStartupScenario.WarmAfterActivityDestroyed(
                    initialTime = Time(
                        nanoTime = currentTime.inWholeNanoseconds,
                        timestamp = currentTime.inWholeMilliseconds
                    ),
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2,
                    activity = activity2.wrapWeak()
                )
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

        detector.onActivityStarted(activity2)
        detector.onActivityResumed(activity2)
        detector.onActivityPaused(activity2)
        detector.onActivityStopped(activity2)
        detector.onActivityDestroyed(activity2)

        detector.onActivityDestroyed(activity)

        currentTime += 30.seconds

        val activity3 = mock<Activity>()

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity3,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle3
        )

        // Then
        inOrder(listener) {
            listener.verifyScenarioDetected(
                RumStartupScenario.Cold(
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                    activity = activity.wrapWeak(),
                    appStartActivityOnCreateGapNs = 30.seconds.inWholeNanoseconds,
                    initialTime = Time(0, 0)
                )
            )

            listener.verifyScenarioDetected(
                RumStartupScenario.WarmAfterActivityDestroyed(
                    initialTime = Time(
                        nanoTime = currentTime.inWholeNanoseconds,
                        timestamp = currentTime.inWholeMilliseconds
                    ),
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle3,
                    activity = activity3.wrapWeak()
                )
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
        detector.onActivityStarted(interstitialActivity)
        detector.onActivityResumed(interstitialActivity)
        detector.onActivityPaused(interstitialActivity)
        detector.onActivityStopped(interstitialActivity)
        detector.onActivityDestroyed(interstitialActivity)

        currentTime += 1.seconds

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = mainActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2
        )

        // Then - scenario detected for main activity (first non-excluded)
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2,
                activity = mainActivity.wrapWeak(),
                appStartActivityOnCreateGapNs = 4.seconds.inWholeNanoseconds
            )
        )

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
        verifyNoMoreInteractions(listener)
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
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle3,
                activity = includedActivity.wrapWeak(),
                appStartActivityOnCreateGapNs = 5.seconds.inWholeNanoseconds
            )
        )

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
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = activity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )

        verifyNoMoreInteractions(listener)
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
            appStartupActivityPredicate = appStartupActivityPredicate
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

    private fun RumAppStartupDetector.Listener.verifyScenarioDetected(expected: RumStartupScenario) {
        verify(this).onAppStartupDetected(
            argThat { actual ->
                (actual.activity.get() == expected.activity.get()) &&
                    (actual.hasSavedInstanceStateBundle == expected.hasSavedInstanceStateBundle) &&
                    (actual.initialTime == expected.initialTime) &&
                    (actual.javaClass == expected.javaClass)
            }
        )
    }
}

private fun Activity.wrapWeak() = WeakReference(this)
