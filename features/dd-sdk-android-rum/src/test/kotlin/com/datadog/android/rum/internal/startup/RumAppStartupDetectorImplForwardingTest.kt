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
internal class RumAppStartupDetectorImplForwardingTest {

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var listener: RumAppStartupDetector.Listener

    @BoolForgery
    private var fakeIsAtLeastQ: Boolean = false

    @Mock
    private lateinit var buildSdkVersionProvider: BuildSdkVersionProvider

    private var currentTime: Duration = 0.nanoseconds

    @BeforeEach
    fun `set up`() {
        // no-op
    }

    @Test
    fun `M forward to next activity W startup activity destroyed without TTID`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given
        val splashActivity = mock<Activity>()
        val mainActivity = mock<Activity>()
        val detector = createDetector()

        currentTime += 3.seconds

        // When - splash created (Cold startup detected)
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = splashActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // Main activity created while splash is still alive
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = mainActivity,
            hasSavedInstanceStateBundle = false
        )

        // Splash destroyed without TTID being reported
        detector.onActivityDestroyed(splashActivity)

        // Then
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = splashActivity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )

        listener.verifyScenarioRetargeted(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = mainActivity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )

        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M forward through chain of interstitials W A to B to C`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given
        val activityA = mock<Activity>()
        val activityB = mock<Activity>()
        val activityC = mock<Activity>()
        val detector = createDetector()

        currentTime += 3.seconds

        // When - A created (Cold startup)
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activityA,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // B created
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activityB,
            hasSavedInstanceStateBundle = false
        )

        // A destroyed -> forward to B
        detector.onActivityDestroyed(activityA)

        // C created
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activityC,
            hasSavedInstanceStateBundle = false
        )

        // B destroyed -> forward to C
        detector.onActivityDestroyed(activityB)

        // Then
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = activityA.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )

        // First forward: A -> B
        listener.verifyScenarioRetargeted(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = activityB.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )

        // Second forward: B -> C
        listener.verifyScenarioRetargeted(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = activityC.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )

        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M not forward W startup activity drew frame and TTID was reported`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given
        val splashActivity = mock<Activity>()
        val mainActivity = mock<Activity>()
        val detector = createDetector()

        currentTime += 3.seconds

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = splashActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = mainActivity,
            hasSavedInstanceStateBundle = false
        )

        // TTID reported before destruction
        detector.notifyStartupTTIDReported()

        // When - splash destroyed after TTID was reported
        detector.onActivityDestroyed(splashActivity)

        // Then - only the original detection, no forwarding
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = splashActivity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M not forward W startup activity destroyed during config change`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given
        val splashActivity = mock<Activity>()
        val mainActivity = mock<Activity>()
        val detector = createDetector()

        currentTime += 3.seconds

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = splashActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = mainActivity,
            hasSavedInstanceStateBundle = false
        )

        // When - splash destroyed due to config change
        whenever(splashActivity.isChangingConfigurations) doReturn true
        detector.onActivityDestroyed(splashActivity)

        // Then - no forwarding
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = splashActivity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M not forward and detect warm start W startup destroyed with no next tracked activity`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean,
        @BoolForgery hasSavedInstanceStateBundle2: Boolean
    ) {
        // Given
        val splashActivity = mock<Activity>()
        val detector = createDetector()

        currentTime += 3.seconds

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = splashActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // When - splash destroyed with no other tracked activities
        detector.onActivityDestroyed(splashActivity)

        // Then - no forwarding
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = splashActivity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )
        verifyNoMoreInteractions(listener)

        // When - next activity created after all are destroyed -> normal warm start
        currentTime += 30.seconds
        val nextActivity = mock<Activity>()

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = nextActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2
        )

        listener.verifyScenarioDetected(
            RumStartupScenario.WarmAfterActivityDestroyed(
                initialTime = Time(
                    nanoTime = currentTime.inWholeNanoseconds,
                    timestamp = currentTime.inWholeMilliseconds
                ),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2,
                activity = nextActivity.wrapWeak()
            )
        )
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M forward only to tracked activity W predicate excludes some activities`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given
        val startupActivity = mock<Activity>()
        val untrackedActivity = mock<Activity>()
        val trackedActivity = mock<Activity>()

        val predicate = AppStartupActivityPredicate { activity ->
            activity != untrackedActivity
        }

        val detector = createDetector(appStartupActivityPredicate = predicate)

        currentTime += 3.seconds

        // Startup activity created
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = startupActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // Untracked activity created (excluded by predicate)
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = untrackedActivity,
            hasSavedInstanceStateBundle = false
        )

        // Tracked activity created
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = trackedActivity,
            hasSavedInstanceStateBundle = false
        )

        // When - startup destroyed
        detector.onActivityDestroyed(startupActivity)

        // Then - forwarded to trackedActivity (not untrackedActivity)
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = startupActivity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )

        listener.verifyScenarioRetargeted(
            argMatcher = { actual ->
                // Should forward to one of the tracked activities (trackedActivity)
                // untrackedActivity is not in trackedActivities so can't be the target
                actual.activity.get() != untrackedActivity &&
                    actual.initialTime == Time(0, 0) &&
                    actual.javaClass == RumStartupScenario.Cold::class.java
            }
        )
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M forward to next activity W startup destroyed before next activity created`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given
        val splashActivity = mock<Activity>()
        val mainActivity = mock<Activity>()
        val detector = createDetector()

        currentTime += 3.seconds

        // When - splash created (Cold startup detected)
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = splashActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // Splash destroyed with no other tracked activities (awaitingRetarget = true)
        detector.onActivityDestroyed(splashActivity)

        // Short delay (100ms) then main created
        currentTime += 100_000_000.nanoseconds

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = mainActivity,
            hasSavedInstanceStateBundle = false
        )

        // Then
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = splashActivity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )

        listener.verifyScenarioRetargeted(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = mainActivity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )

        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M forward to next activity W deferred retarget and predicate excludes intermediate activity`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given
        val splashActivity = mock<Activity>()
        val untrackedActivity = mock<Activity>()
        val trackedActivity = mock<Activity>()

        val predicate = AppStartupActivityPredicate { activity ->
            activity != untrackedActivity
        }

        val detector = createDetector(appStartupActivityPredicate = predicate)

        currentTime += 3.seconds

        // Splash created (Cold startup detected)
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = splashActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // Splash destroyed with no other tracked activities (awaitingRetarget = true)
        detector.onActivityDestroyed(splashActivity)

        // Untracked activity created — should NOT trigger retarget
        currentTime += 50_000_000.nanoseconds
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = untrackedActivity,
            hasSavedInstanceStateBundle = false
        )

        // Tracked activity created — should trigger deferred retarget
        currentTime += 50_000_000.nanoseconds
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = trackedActivity,
            hasSavedInstanceStateBundle = false
        )

        // Then
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = splashActivity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )

        listener.verifyScenarioRetargeted(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = trackedActivity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )

        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M detect warm start W deferred retarget expires after threshold`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean,
        @BoolForgery hasSavedInstanceStateBundle2: Boolean
    ) {
        // Given
        val splashActivity = mock<Activity>()
        val detector = createDetector()

        currentTime += 3.seconds

        // Splash created (Cold startup detected)
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = splashActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // Splash destroyed with no other tracked activities (awaitingRetarget = true)
        detector.onActivityDestroyed(splashActivity)

        // Advance time past RETARGET_TIMEOUT_NS (1 second) — 2 seconds total
        currentTime += 2.seconds

        // New activity created — stale scenario, should detect WarmAfterActivityDestroyed
        val nextActivity = mock<Activity>()
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = nextActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2
        )

        // Then
        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                initialTime = Time(0, 0),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = splashActivity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )

        listener.verifyScenarioDetected(
            RumStartupScenario.WarmAfterActivityDestroyed(
                initialTime = Time(
                    nanoTime = currentTime.inWholeNanoseconds,
                    timestamp = currentTime.inWholeMilliseconds
                ),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2,
                activity = nextActivity.wrapWeak()
            )
        )

        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M detect normal warm start W forwarding cycle completes and new activity created`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean,
        @BoolForgery hasSavedInstanceStateBundle2: Boolean
    ) {
        // Given
        val splashActivity = mock<Activity>()
        val mainActivity = mock<Activity>()
        val detector = createDetector()

        currentTime += 3.seconds

        // Cold startup with splash
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = splashActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // Main created
        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = mainActivity,
            hasSavedInstanceStateBundle = false
        )

        // Splash destroyed -> forward to main
        detector.onActivityDestroyed(splashActivity)

        // TTID reported for main
        detector.notifyStartupTTIDReported()

        // Main destroyed
        detector.onActivityDestroyed(mainActivity)

        // When - new activity created
        currentTime += 30.seconds
        val newActivity = mock<Activity>()

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = newActivity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2
        )

        // Then - original Cold, one forward, then a new WarmAfterActivityDestroyed
        inOrder(listener) {
            listener.verifyScenarioDetected(
                RumStartupScenario.Cold(
                    initialTime = Time(0, 0),
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                    activity = splashActivity.wrapWeak(),
                    appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
                )
            )

            listener.verifyScenarioRetargeted(
                RumStartupScenario.Cold(
                    initialTime = Time(0, 0),
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                    activity = mainActivity.wrapWeak(),
                    appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
                )
            )

            listener.verifyScenarioDetected(
                RumStartupScenario.WarmAfterActivityDestroyed(
                    initialTime = Time(
                        nanoTime = currentTime.inWholeNanoseconds,
                        timestamp = currentTime.inWholeMilliseconds
                    ),
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2,
                    activity = newActivity.wrapWeak()
                )
            )
        }
        verifyNoMoreInteractions(listener)
    }

    private fun createDetector(
        appStartupActivityPredicate: AppStartupActivityPredicate = AppStartupActivityPredicate { true }
    ): RumAppStartupDetectorImpl {
        whenever(buildSdkVersionProvider.isAtLeastQ) doReturn fakeIsAtLeastQ

        return RumAppStartupDetectorImpl(
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

    private fun RumAppStartupDetector.Listener.verifyScenarioDetected(
        expected: RumStartupScenario
    ) {
        verify(this).onAppStartupDetected(
            argThat { actual ->
                (actual.activity.get() == expected.activity.get()) &&
                    (actual.hasSavedInstanceStateBundle == expected.hasSavedInstanceStateBundle) &&
                    (actual.initialTime == expected.initialTime) &&
                    (actual.javaClass == expected.javaClass)
            }
        )
    }

    private fun RumAppStartupDetector.Listener.verifyScenarioRetargeted(
        expected: RumStartupScenario
    ) {
        verify(this).onAppStartupRetargeted(
            argThat { actual ->
                (actual.activity.get() == expected.activity.get()) &&
                    (actual.hasSavedInstanceStateBundle == expected.hasSavedInstanceStateBundle) &&
                    (actual.initialTime == expected.initialTime) &&
                    (actual.javaClass == expected.javaClass)
            }
        )
    }

    private fun RumAppStartupDetector.Listener.verifyScenarioRetargeted(
        argMatcher: (RumStartupScenario) -> Boolean
    ) {
        verify(this).onAppStartupRetargeted(argThat(argMatcher))
    }
}

private fun Activity.wrapWeak() = WeakReference(this)
