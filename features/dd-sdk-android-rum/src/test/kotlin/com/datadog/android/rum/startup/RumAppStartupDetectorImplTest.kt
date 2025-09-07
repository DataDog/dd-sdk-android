/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.startup

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.Application
import android.os.Build
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.internal.startup.RumAppStartupDetector
import com.datadog.android.rum.internal.startup.RumAppStartupDetectorImpl
import com.datadog.android.rum.internal.startup.RumStartupScenario
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
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
    @Test
    fun `M detect Cold scenario W RumAppStartupDetectorImpl {IMPORTANCE_FOREGROUND and small gap}`(
        @IntForgery(min = Build.VERSION_CODES.M, max = Build.VERSION_CODES.BAKLAVA) fakeBuildSdkVersion: Int
    ) {
        val application = mock<Application>()
        var currentTime: Duration = 0.nanoseconds

        val listener = mock<RumAppStartupDetector.Listener>()

        val buildSdkVersionProvider = mock<BuildSdkVersionProvider>()
        whenever(buildSdkVersionProvider.version) doReturn fakeBuildSdkVersion

        val rumAppStartupDetector = RumAppStartupDetectorImpl(
            application = application,
            buildSdkVersionProvider = buildSdkVersionProvider,
            appStartupTimeProvider = { 0 },
            processImportanceProvider = { IMPORTANCE_FOREGROUND },
            timeProviderNanos = { currentTime.inWholeNanoseconds }
        )

        rumAppStartupDetector.addListener(listener)

        verify(application).registerActivityLifecycleCallbacks(any())
        verifyNoMoreInteractions(application)

        val activity = mock<Activity>()
        currentTime += 3.seconds
        if (fakeBuildSdkVersion >= Build.VERSION_CODES.Q) {
            rumAppStartupDetector.onActivityPreCreated(activity, null)
        } else {
            rumAppStartupDetector.onActivityCreated(activity, null)
        }

        listener.verifyScenarioDetected(
            RumStartupScenario.Cold(
                startTimeNanos = 0,
                hasSavedInstanceStateBundle = false,
                activityName = activity.javaClass.canonicalName ?: activity.javaClass.name,
                activity = activity,
                gapNanos = 3.seconds.inWholeNanoseconds,
                nStart = 0,
            )
        )
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun `M detect WarmFirstActivity scenario W RumAppStartupDetectorImpl {IMPORTANCE_CACHED and small gap}`(
        @IntForgery(min = Build.VERSION_CODES.M, max = Build.VERSION_CODES.BAKLAVA) fakeBuildSdkVersion: Int
    ) {
        val application = mock<Application>()
        var currentTime: Duration = 0.nanoseconds

        val listener = mock<RumAppStartupDetector.Listener>()

        val buildSdkVersionProvider = mock<BuildSdkVersionProvider>()
        whenever(buildSdkVersionProvider.version) doReturn fakeBuildSdkVersion

        val rumAppStartupDetector = RumAppStartupDetectorImpl(
            application = application,
            buildSdkVersionProvider = buildSdkVersionProvider,
            appStartupTimeProvider = { 0 },
            processImportanceProvider = { IMPORTANCE_CACHED },
            timeProviderNanos = { currentTime.inWholeNanoseconds }
        )

        rumAppStartupDetector.addListener(listener)

        verify(application).registerActivityLifecycleCallbacks(any())
        verifyNoMoreInteractions(application)

        val activity = mock<Activity>()
        currentTime += 3.seconds
        if (fakeBuildSdkVersion >= Build.VERSION_CODES.Q) {
            rumAppStartupDetector.onActivityPreCreated(activity, null)
        } else {
            rumAppStartupDetector.onActivityCreated(activity, null)
        }

        listener.verifyScenarioDetected(
            RumStartupScenario.WarmFirstActivity(
                startTimeNanos = currentTime.inWholeNanoseconds,
                hasSavedInstanceStateBundle = false,
                activityName = activity.javaClass.canonicalName ?: activity.javaClass.name,
                activity = activity,
                gapNanos = currentTime.inWholeNanoseconds,
                nStart = 0,
                processStartedInForeground = false
            )
        )
        verifyNoMoreInteractions(listener)
    }

    private fun RumAppStartupDetector.Listener.verifyScenarioDetected(scenario: RumStartupScenario) {
        verify(this).onAppStartupDetected(eq(scenario))
    }
}
