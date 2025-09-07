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
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.internal.startup.RumAppStartupDetector
import com.datadog.android.rum.internal.startup.RumAppStartupDetectorImpl
import com.datadog.android.rum.internal.startup.RumStartupScenario
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
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
    }

    @Test
    fun `M detect Cold scenario W RumAppStartupDetectorImpl {IMPORTANCE_FOREGROUND and small gap}`() {
        // Given
        val detector = createDetector(
            processImportance = IMPORTANCE_FOREGROUND,
        )
        verify(application).registerActivityLifecycleCallbacks(any())
        verifyNoMoreInteractions(application)

        currentTime += 3.seconds

        // When
        triggerBeforeCreated(detector)

        // Then
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
    fun `M detect WarmFirstActivity scenario W RumAppStartupDetectorImpl {IMPORTANCE_CACHED and small gap}`() {
        val detector = createDetector(
            processImportance = IMPORTANCE_CACHED,
        )
        verify(application).registerActivityLifecycleCallbacks(any())
        verifyNoMoreInteractions(application)

        currentTime += 3.seconds
        triggerBeforeCreated(detector)

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
        return detector
    }

    private fun triggerBeforeCreated(detector: RumAppStartupDetectorImpl) {
        if (fakeBuildSdkVersion >= Build.VERSION_CODES.Q) {
            detector.onActivityPreCreated(activity, null)
        } else {
            detector.onActivityCreated(activity, null)
        }
    }

    private fun RumAppStartupDetector.Listener.verifyScenarioDetected(scenario: RumStartupScenario) {
        verify(this).onAppStartupDetected(eq(scenario))
    }
}
