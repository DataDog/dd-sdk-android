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
    fun `M fall back to its own detector W onInitialize() {captured activity excluded}`() {
        // Given — the pre-launch detector accepts every Activity, but the configured predicate
        // excludes the one it happened to capture.
        installPreLaunchDetector()
        emitPreLaunchAppStartup()
        whenever(mockAppStartupActivityPredicate.shouldTrackStartup(mockActivity)) doReturn false

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.usePreLaunchDetector).isFalse()
        assertThat(testedFeature.rumAppStartupDetector).isNotNull()
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

    // endregion

    // region Helpers

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
     * [PreLaunchRumAppStartupDetector] is a process-scoped singleton, so its state has to be
     * cleared between tests. Its backing fields are private, hence the reflection.
     */
    private fun resetPreLaunchDetector() {
        PreLaunchRumAppStartupDetector.detach()
        PreLaunchRumAppStartupDetector.setFieldValue("detectorImpl", null)
        PreLaunchRumAppStartupDetector.setFieldValue("capturedActivity", null)
        PreLaunchRumAppStartupDetector
            .getFieldValue<MutableList<*>, PreLaunchRumAppStartupDetector>("pendingEvents")
            .clear()
    }

    /**
     * Marks the singleton as installed without actually registering lifecycle callbacks — this
     * test drives the listener callbacks by hand instead.
     */
    private fun installPreLaunchDetector() {
        PreLaunchRumAppStartupDetector.setFieldValue(
            "detectorImpl",
            mock<RumAppStartupDetector>()
        )
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
