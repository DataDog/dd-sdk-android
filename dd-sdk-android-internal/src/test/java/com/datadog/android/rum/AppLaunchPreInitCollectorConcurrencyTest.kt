/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.internal.forge.Configurator
import com.datadog.android.internal.system.BuildSdkVersionProvider
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class AppLaunchPreInitCollectorConcurrencyTest {

    @Mock
    lateinit var mockApplication: Application

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockWindow: Window

    @Mock
    lateinit var mockDecorView: View

    @Mock
    lateinit var mockViewTreeObserver: ViewTreeObserver

    @Mock
    lateinit var mockHandler: Handler

    @Mock
    lateinit var stubBuildSdkVersionProvider: BuildSdkVersionProvider

    @BeforeEach
    fun `set up`() {
        AppLaunchPreInitCollector.buildSdkVersionProvider = stubBuildSdkVersionProvider
        whenever(stubBuildSdkVersionProvider.isAtLeastQ).thenReturn(true)
        whenever(stubBuildSdkVersionProvider.isAtLeastN).thenReturn(true)

        DdRumContentProvider.createTimeNs = System.nanoTime()

        whenever(mockActivity.window).thenReturn(mockWindow)
        whenever(mockWindow.peekDecorView()).thenReturn(mockDecorView)
        whenever(mockWindow.decorView).thenReturn(mockDecorView)
        whenever(mockDecorView.viewTreeObserver).thenReturn(mockViewTreeObserver)
        whenever(mockViewTreeObserver.isAlive).thenReturn(true)
        whenever(mockDecorView.isAttachedToWindow).thenReturn(true)

        // CRITICAL: addOnDrawListener must NOT auto-fire onDraw — keeps state at CAPTURING
        // long enough for the CAS race to be observable
        whenever(mockViewTreeObserver.addOnDrawListener(any())).doAnswer { Unit }

        // Handler must NOT run runnables immediately — deferred so state stays stable during race
        whenever(mockHandler.post(any())).doAnswer { true }

        AppLaunchPreInitCollector.handlerFactory = { mockHandler }
    }

    @AfterEach
    fun `tear down`() {
        AppLaunchPreInitCollector.reset()
    }

    @RepeatedTest(50)
    fun `M exactly one winner W concurrent IDLE to CAPTURING vs IDLE to CLAIMED`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        val gate = CountDownLatch(1)
        val winnerCount = AtomicInteger(0)

        val t1 = thread(isDaemon = false) {
            gate.await()
            callbacks.onActivityPreCreated(mockActivity, null)
            if (AppLaunchPreInitCollector.state == AppLaunchPreInitCollector.State.CAPTURING) {
                winnerCount.incrementAndGet()
            }
        }
        val t2 = thread(isDaemon = false) {
            gate.await()
            if (AppLaunchPreInitCollector.claim()) winnerCount.incrementAndGet()
        }

        // When
        gate.countDown()
        t1.join(2_000)
        t2.join(2_000)

        // Then
        assertThat(t1.isAlive).isFalse()
        assertThat(t2.isAlive).isFalse()
        assertThat(winnerCount.get()).isEqualTo(1)
        assertThat(AppLaunchPreInitCollector.state).isIn(
            AppLaunchPreInitCollector.State.CAPTURING,
            AppLaunchPreInitCollector.State.CLAIMED
        )
    }
}
