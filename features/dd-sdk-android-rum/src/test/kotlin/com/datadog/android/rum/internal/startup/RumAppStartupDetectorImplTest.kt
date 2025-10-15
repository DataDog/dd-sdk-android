/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.Application
import android.os.Build
import android.os.Bundle
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.internal.domain.Time
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

    private var fakeBuildSdkVersion: Int = 0

    @Mock
    private lateinit var buildSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    private lateinit var activity: Activity

    private var currentTime: Duration = 0.nanoseconds

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeBuildSdkVersion = forge.anInt(
            min = System.getProperty("RUM_MIN_SDK")!!.toInt(),
            max = System.getProperty("RUM_TARGET_SDK")!!.toInt() + 1
        )
        whenever(activity.isChangingConfigurations) doReturn false
    }

    @Test
    fun `M registerActivityLifecycleCallbacks W RumAppStartupDetector constructor`() {
        createDetector(
            processImportance = IMPORTANCE_FOREGROUND
        )
        verify(application).registerActivityLifecycleCallbacks(any())
        verifyNoMoreInteractions(application)
    }

    @Test
    fun `M detect Cold scenario W RumAppStartupDetector {IMPORTANCE_FOREGROUND and small gap}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND
        )

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
    fun `M detect WarmFirstActivity scenario W RumAppStartupDetector {IMPORTANCE_CACHED and small gap}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given
        val detector = createDetector(
            processImportance = IMPORTANCE_CACHED
        )

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
            RumStartupScenario.WarmFirstActivity(
                initialTime = Time(
                    nanoTime = currentTime.inWholeNanoseconds,
                    timestamp = currentTime.inWholeMilliseconds
                ),
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activity = activity.wrapWeak(),
                appStartActivityOnCreateGapNs = 3.seconds.inWholeNanoseconds
            )
        )
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M detect WarmFirstActivity scenario W RumAppStartupDetector {IMPORTANCE_FOREGROUND and large gap}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND
        )

        currentTime += 6.seconds
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
                appStartActivityOnCreateGapNs = 6.seconds.inWholeNanoseconds
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
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND
        )

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
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND
        )

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
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND
        )

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
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND
        )

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
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND
        )

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

    private fun createDetector(processImportance: Int): RumAppStartupDetectorImpl {
        whenever(buildSdkVersionProvider.version) doReturn fakeBuildSdkVersion

        val detector = RumAppStartupDetectorImpl(
            application = application,
            buildSdkVersionProvider = buildSdkVersionProvider,
            appStartupTimeProvider = { Time(0, 0) },
            processImportanceProvider = { processImportance },
            timeProvider = {
                Time(
                    timestamp = currentTime.inWholeMilliseconds,
                    nanoTime = currentTime.inWholeNanoseconds
                )
            },
            listener
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
        if (fakeBuildSdkVersion >= Build.VERSION_CODES.Q) {
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
