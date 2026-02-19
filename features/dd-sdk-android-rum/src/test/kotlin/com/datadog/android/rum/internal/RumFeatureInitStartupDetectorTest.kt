/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.Activity
import android.app.Application
import android.content.ContentResolver
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.startup.RumAppStartupDetectorImpl
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
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumFeatureInitStartupDetectorTest {

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
    lateinit var mockScheduledExecutorService: ScheduledExecutorService

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mockTimeProvider
        whenever(mockSdkCore.createScheduledExecutorService(any())) doReturn mockScheduledExecutorService
        whenever(mockSdkCore.appStartTimeNs) doReturn 0L
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()) doReturn System.nanoTime()

        whenever(fakeConfiguration.appStartupActivityPredicate.shouldTrackStartup(any())) doReturn true

        val mockContentResolver = mock<ContentResolver>()
        whenever(appContext.mockInstance.contentResolver) doReturn mockContentResolver
        doNothing().whenever(appContext.mockInstance).registerComponentCallbacks(any())
        doNothing().whenever(appContext.mockInstance).unregisterComponentCallbacks(any())

        val mockResources = mock<Resources>()
        whenever(appContext.mockInstance.resources) doReturn mockResources
        whenever(mockResources.configuration) doReturn mock()

        testedFeature = RumFeature(
            mockSdkCore,
            fakeApplicationId.toString(),
            fakeConfiguration
        )
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor.clear()
    }

    @Test
    fun `M call sendAppStartEvent W onAppStartupDetected`() {
        // Given
        val detector = initializeAndCaptureDetector()
        val mockActivity = createMockActivityWithWindow()

        // When
        detector.simulateActivityCreated(mockActivity, null)

        // Then
        verify(mockRumMonitor).sendAppStartEvent(any())
        verify(mockRumMonitor, never()).sendTTIDEvent(any())
    }

    @Test
    fun `M subscribe to first frame W onAppStartupDetected { non-forwarded path }`() {
        // Given
        val detector = initializeAndCaptureDetector()
        val mockActivity = createMockActivityWithWindow()
        val decorView = mockActivity.window.decorView

        // When
        detector.simulateActivityCreated(mockActivity, null)

        // Then - onAppStartupDetected calls subscribeToFirstFrame with wasForwarded=false,
        // which always takes the else branch (subscribes via rumFirstDrawTimeReporter)
        verify(mockRumMonitor).sendAppStartEvent(any())
        verify(decorView).addOnAttachStateChangeListener(any())
    }

    @Test
    fun `M not detect startup W appStartupActivityPredicate returns false`() {
        // Given
        whenever(fakeConfiguration.appStartupActivityPredicate.shouldTrackStartup(any())) doReturn false
        val detector = initializeAndCaptureDetector()
        val mockActivity = createMockActivityWithWindow()

        // When
        detector.simulateActivityCreated(mockActivity, null)

        // Then
        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `M not call sendAppStartEvent W onAppStartupDetected { no AdvancedRumMonitor }`() {
        // Given
        GlobalRumMonitor.clear()
        val detector = initializeAndCaptureDetector()
        val mockActivity = createMockActivityWithWindow()

        // When
        detector.simulateActivityCreated(mockActivity, null)

        // Then
        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `M subscribe and invalidate W onAppStartupRetargeted`() {
        // Given
        val detector = initializeAndCaptureDetector()
        val splashActivity = createMockActivityWithWindow()
        val mainActivity = createMockActivityWithWindow()
        val mainDecorView = mainActivity.window.decorView

        // When
        detector.simulateActivityCreated(splashActivity, null)
        detector.simulateActivityCreated(mainActivity, null)
        detector.onActivityDestroyed(splashActivity)

        // Then - subscribes to draw listener and invalidates to ensure draw fires
        verify(mockRumMonitor, times(1)).sendAppStartEvent(any())
        verify(mockRumMonitor, never()).sendTTIDEvent(any())
        verify(mainDecorView).addOnAttachStateChangeListener(any())
        verify(mainDecorView).invalidate()
    }

    @Test
    fun `M not call sendTTIDEvent W startup activity destroyed { no other tracked activity }`() {
        // Given
        val detector = initializeAndCaptureDetector()
        val mockActivity = createMockActivityWithWindow()

        // When
        detector.simulateActivityCreated(mockActivity, null)
        detector.onActivityDestroyed(mockActivity)

        // Then
        verify(mockRumMonitor).sendAppStartEvent(any())
        verify(mockRumMonitor, never()).sendTTIDEvent(any())
    }

    @Test
    fun `M not call sendTTIDEvent W onAppStartupRetargeted { no AdvancedRumMonitor }`() {
        // Given
        val detector = initializeAndCaptureDetector()
        val splashActivity = createMockActivityWithWindow()
        val mainActivity = createMockActivityWithWindow()
        val mainDecorView = mainActivity.window.decorView

        detector.simulateActivityCreated(splashActivity, null)
        detector.simulateActivityCreated(mainActivity, null)

        // Clear monitor after detection but before retargeting
        GlobalRumMonitor.clear()

        // When
        detector.onActivityDestroyed(splashActivity)

        // Then - subscribes to draw and invalidates, but since monitor is gone
        // the callback won't reach sendTTIDEvent
        verify(mockRumMonitor, times(1)).sendAppStartEvent(any())
        verify(mockRumMonitor, never()).sendTTIDEvent(any())
        verify(mainDecorView).addOnAttachStateChangeListener(any())
        verify(mainDecorView).invalidate()
    }

    @Test
    fun `M not forward again W notifyStartupTTIDReported called after first forward`() {
        // Given - set up a forwarding scenario: splash -> main -> third
        // After the first forwarding (splash -> main) subscribes to draw,
        // the draw callback fires and calls notifyStartupTTIDReported.
        // Then when main is destroyed with third still alive, no second
        // forwarding should happen because the startupTTIDReported flag prevents it.
        val detector = initializeAndCaptureDetector()
        val splashActivity = createMockActivityWithWindow()
        val mainActivity = createMockActivityWithWindow()
        val thirdActivity = createMockActivityWithWindow()
        val mainDecorView = mainActivity.window.decorView

        // Splash created (startup detected), main created, third created
        detector.simulateActivityCreated(splashActivity, null)
        detector.simulateActivityCreated(mainActivity, null)
        detector.simulateActivityCreated(thirdActivity, null)

        // Splash destroyed -> forwards to main, subscribes to draw + invalidates
        detector.onActivityDestroyed(splashActivity)
        verify(mockRumMonitor, never()).sendTTIDEvent(any())
        verify(mainDecorView).addOnAttachStateChangeListener(any())
        verify(mainDecorView).invalidate()

        // Simulate the draw callback having fired (in real Android,
        // invalidate() triggers a draw which calls notifyStartupTTIDReported)
        detector.notifyStartupTTIDReported()

        // When - main destroyed while third is still alive.
        // The detector should not attempt another forwarding.
        detector.onActivityDestroyed(mainActivity)

        // Then - sendTTIDEvent was never called directly (it would come via draw callback)
        verify(mockRumMonitor, never()).sendTTIDEvent(any())
    }

    @Test
    fun `M destroy rumAppStartupDetector W onStop()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        val detector = testedFeature.getFieldValue<Any?, RumFeature>("rumAppStartupDetector")
        assertThat(detector).isNotNull

        // When
        testedFeature.onStop()

        // Then
        val detectorAfterStop = testedFeature.getFieldValue<Any?, RumFeature>("rumAppStartupDetector")
        assertThat(detectorAfterStop).isNull()
        verify(appContext.mockInstance)
            .unregisterActivityLifecycleCallbacks(detector as Application.ActivityLifecycleCallbacks)
    }

    private fun createMockActivityWithWindow(): Activity {
        val mockActivity = mock<Activity>()
        val mockWindow = mock<Window>()
        val mockDecorView = mock<View>()
        val mockCallback = mock<Window.Callback>()
        whenever(mockActivity.window) doReturn mockWindow
        whenever(mockWindow.callback) doReturn mockCallback
        whenever(mockWindow.peekDecorView()) doReturn mockDecorView
        whenever(mockWindow.decorView) doReturn mockDecorView
        return mockActivity
    }

    private fun initializeAndCaptureDetector(): RumAppStartupDetectorImpl {
        testedFeature.onInitialize(appContext.mockInstance)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(appContext.mockInstance, atLeastOnce())
            .registerActivityLifecycleCallbacks(captor.capture())
        return captor.allValues.filterIsInstance<RumAppStartupDetectorImpl>().first()
    }

    private fun RumAppStartupDetectorImpl.simulateActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            onActivityPreCreated(activity, savedInstanceState)
        } else {
            onActivityCreated(activity, savedInstanceState)
        }
    }

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
