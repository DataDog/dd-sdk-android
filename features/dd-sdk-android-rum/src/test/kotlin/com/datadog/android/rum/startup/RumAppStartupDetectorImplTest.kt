/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.startup

import android.app.Activity
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.Application
import android.os.Build
import android.os.Bundle
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.internal.startup.RumAppStartupDetector
import com.datadog.android.rum.internal.startup.RumAppStartupDetectorImpl
import com.datadog.android.rum.internal.startup.RumStartupScenario
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.IntForgery
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
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

    @IntForgery(min = Build.VERSION_CODES.M, max = Build.VERSION_CODES.BAKLAVA)
    private var fakeBuildSdkVersion: Int = 0

    @Mock
    private lateinit var buildSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    private lateinit var activity: Activity

    private var currentTime: Duration = 0.nanoseconds

    @BeforeEach
    fun `set up`() {
        currentTime = 0.nanoseconds
        whenever(activity.isChangingConfigurations) doReturn false
    }

    @Test
    fun `M detect Cold scenario W RumAppStartupDetectorImpl {IMPORTANCE_FOREGROUND and small gap}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND,
        )
        launchColdAndVerify(
            forge = forge,
            detector = detector,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )
    }

    @Test
    fun `M detect WarmFirstActivity scenario W RumAppStartupDetectorImpl {IMPORTANCE_CACHED and small gap}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        // Given
        val detector = createDetector(
            processImportance = IMPORTANCE_CACHED,
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
                startTimeNanos = currentTime.inWholeNanoseconds,
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activityName = activity.javaClass.canonicalName ?: activity.javaClass.name,
                activity = activity,
                gapNanos = currentTime.inWholeNanoseconds,
                nStart = 0,
                processStartedInForeground = false
            )
        )
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M detect WarmFirstActivity scenario W RumAppStartupDetectorImpl {IMPORTANCE_FOREGROUND and large gap}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean
    ) {
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND,
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
                startTimeNanos = currentTime.inWholeNanoseconds,
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activityName = activity.javaClass.canonicalName ?: activity.javaClass.name,
                activity = activity,
                gapNanos = currentTime.inWholeNanoseconds,
                nStart = 0,
                processStartedInForeground = true
            )
        )
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M detect WarmAfterActivityDestroyed scenario W RumAppStartupDetectorImpl {after 1st Cold scenario}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean,
        @BoolForgery hasSavedInstanceStateBundle2: Boolean,
    ) {
        // Given
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND,
        )

        launchColdAndVerify(
            forge = forge,
            detector = detector,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // When
        detector.onActivityDestroyed(activity)

        currentTime += 30.seconds

        triggerBeforeCreated(
            forge = forge,
            detector = detector,
            activity = activity,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2
        )

        // Then
        listener.verifyScenarioDetected(
            RumStartupScenario.WarmAfterActivityDestroyed(
                startTimeNanos = currentTime.inWholeNanoseconds,
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2,
                activityName = activity.javaClass.canonicalName ?: activity.javaClass.name,
                activity = activity,
                nStart = 1
            )
        )
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M not detect any scenario W RumAppStartupDetectorImpl {after 1st Cold scenario and configuration change}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean,
    ) {
        // Given
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND,
        )

        launchColdAndVerify(
            forge = forge,
            detector = detector,
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
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M not detect any scenario W RumAppStartupDetectorImpl {after 1st Colds scenario, 1st activity stopped and another activity is created}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean,
        @BoolForgery hasSavedInstanceStateBundle2: Boolean
    ) {
        // Given
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND,
        )

        launchColdAndVerify(
            forge = forge,
            detector = detector,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // When
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
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M detect WarmAfterActivityDestroyed scenario W RumAppStartupDetectorImpl {after 1st Colds scenario, 1st activity destroyed and another activity is created}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean,
        @BoolForgery hasSavedInstanceStateBundle2: Boolean
    ) {
        // Given
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND,
        )

        launchColdAndVerify(
            forge = forge,
            detector = detector,
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
        listener.verifyScenarioDetected(
            RumStartupScenario.WarmAfterActivityDestroyed(
                startTimeNanos = currentTime.inWholeNanoseconds,
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle2,
                activityName = activity2.javaClass.canonicalName ?: activity2.javaClass.name,
                activity = activity2,
                nStart = 1
            )
        )

        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M detect WarmAfterActivityDestroyed scenario W RumAppStartupDetectorImpl {2 activities created, destroyed and the 3rd created}`(
        forge: Forge,
        @BoolForgery hasSavedInstanceStateBundle: Boolean,
        @BoolForgery hasSavedInstanceStateBundle2: Boolean,
        @BoolForgery hasSavedInstanceStateBundle3: Boolean,
    ) {
        // Given
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND,
        )

        launchColdAndVerify(
            forge = forge,
            detector = detector,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle
        )

        // When
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

        verifyNoMoreInteractions(listener)

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
        listener.verifyScenarioDetected(
            RumStartupScenario.WarmAfterActivityDestroyed(
                startTimeNanos = currentTime.inWholeNanoseconds,
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle3,
                activityName = activity3.javaClass.canonicalName ?: activity3.javaClass.name,
                activity = activity3,
                nStart = 1
            )
        )

        verifyNoMoreInteractions(listener)
    }

    private fun launchColdAndVerify(forge: Forge, detector: RumAppStartupDetectorImpl, hasSavedInstanceStateBundle: Boolean) {
        // Given
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
                startTimeNanos = 0,
                hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                activityName = activity.javaClass.canonicalName ?: activity.javaClass.name,
                activity = activity,
                gapNanos = 3.seconds.inWholeNanoseconds,
                nStart = 0,
            )
        )
        verifyNoMoreInteractions(listener)
    }

    private fun createDetector(processImportance: Int): RumAppStartupDetectorImpl {
        whenever(buildSdkVersionProvider.version) doReturn fakeBuildSdkVersion
        val detector = RumAppStartupDetectorImpl(
            application = application,
            buildSdkVersionProvider = buildSdkVersionProvider,
            appStartupTimeProvider = { 0 },
            processImportanceProvider = { processImportance },
            timeProviderNanos = { currentTime.inWholeNanoseconds }
        )
        detector.addListener(listener)

        verify(application).registerActivityLifecycleCallbacks(any())
        verifyNoMoreInteractions(application)

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

    private fun RumAppStartupDetector.Listener.verifyScenarioDetected(scenario: RumStartupScenario) {
        verify(this).onAppStartupDetected(eq(scenario))
    }
}
