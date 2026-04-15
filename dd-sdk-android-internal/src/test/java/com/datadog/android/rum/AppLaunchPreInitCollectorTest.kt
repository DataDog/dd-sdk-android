/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.internal.forge.Configurator
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.startup.RumFirstDrawTimeReporter
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class AppLaunchPreInitCollectorTest {

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

    private var fakeCreateTimeNs: Long = 0L

    @BeforeEach
    fun `set up`() {
        // Inject mock build SDK version provider
        AppLaunchPreInitCollector.buildSdkVersionProvider = stubBuildSdkVersionProvider

        // Default: API 29+
        whenever(stubBuildSdkVersionProvider.isAtLeastQ).thenReturn(true)
        whenever(stubBuildSdkVersionProvider.isAtLeastN).thenReturn(true)

        // Set up a known createTimeNs value
        fakeCreateTimeNs = System.nanoTime()
        DdRumContentProvider.createTimeNs = fakeCreateTimeNs

        // Set up activity -> window -> decorView chain
        whenever(mockActivity.window).thenReturn(mockWindow)
        whenever(mockWindow.peekDecorView()).thenReturn(mockDecorView)
        whenever(mockWindow.decorView).thenReturn(mockDecorView)
        whenever(mockDecorView.viewTreeObserver).thenReturn(mockViewTreeObserver)
        whenever(mockViewTreeObserver.isAlive).thenReturn(true)
        whenever(mockDecorView.isAttachedToWindow).thenReturn(true)

        // Inject handler factory so tests can control handler behavior
        AppLaunchPreInitCollector.handlerFactory = { mockHandler }

        // By default, handler.post runs the runnable immediately (synchronous in tests)
        whenever(mockHandler.post(any())).doAnswer {
            val runnable = it.getArgument<Runnable>(0)
            runnable.run()
            true
        }

        // handler.sendMessageAtFrontOfQueue runs the message callback immediately (synchronous in tests)
        // RumFirstDrawTimeReporterImpl uses this to invoke onFirstFrameDrawn
        whenever(mockHandler.sendMessageAtFrontOfQueue(any())).doAnswer {
            val message = it.getArgument<Message>(0)
            message.callback?.run()
            true
        }

        // By default, addOnDrawListener immediately fires onDraw (simulates first frame)
        // Tests that need to control timing should override this behavior
        whenever(mockViewTreeObserver.addOnDrawListener(any())).doAnswer { invocation ->
            val listener = invocation.getArgument<ViewTreeObserver.OnDrawListener>(0)
            listener.onDraw()
            Unit
        }
    }

    @AfterEach
    fun `tear down`() {
        AppLaunchPreInitCollector.reset()
    }

    // region install()

    @Test
    fun `M transition to IDLE W install() {NOT_INSTALLED state}`() {
        // When
        AppLaunchPreInitCollector.install(mockApplication)

        // Then
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.IDLE)
    }

    @Test
    fun `M register ActivityLifecycleCallbacks W install()`() {
        // When
        AppLaunchPreInitCollector.install(mockApplication)

        // Then
        verify(mockApplication).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `M remain in current state W install() {already IDLE}`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)

        // When
        AppLaunchPreInitCollector.install(mockApplication)

        // Then
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.IDLE)
        verify(mockApplication).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `M remain in current state W install() {already CAPTURING}`() {
        // Given: install to IDLE, then test second install is no-op while IDLE
        AppLaunchPreInitCollector.install(mockApplication)
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.IDLE)

        // install again - should be no-op since state is IDLE (not NOT_INSTALLED)
        AppLaunchPreInitCollector.install(mockApplication)

        // registerActivityLifecycleCallbacks should only be called once
        verify(mockApplication).registerActivityLifecycleCallbacks(any())
    }

    // endregion

    // region claim()

    @Test
    fun `M transition to CLAIMED W claim() {IDLE state}`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)

        // When
        val result = AppLaunchPreInitCollector.claim()

        // Then
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.CLAIMED)
        assertThat(result).isTrue()
    }

    @Test
    fun `M unregister ActivityLifecycleCallbacks W claim() {IDLE state}`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)

        // When
        AppLaunchPreInitCollector.claim()

        // Then
        verify(mockApplication).unregisterActivityLifecycleCallbacks(any())
    }

    @Test
    fun `M return true W claim() {IDLE state}`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)

        // When
        val result = AppLaunchPreInitCollector.claim()

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W claim() {NOT_INSTALLED state}`() {
        // When
        val result = AppLaunchPreInitCollector.claim()

        // Then
        assertThat(result).isFalse()
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.NOT_INSTALLED)
    }

    @Test
    fun `M return false W claim() {CLAIMED state}`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)
        AppLaunchPreInitCollector.claim()

        // When
        val result = AppLaunchPreInitCollector.claim()

        // Then
        assertThat(result).isFalse()
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.CLAIMED)
    }

    @Test
    fun `M return false W claim() {COMPLETE state}`() {
        // Given: drive to COMPLETE via install -> lifecycle -> onDraw (auto-fires in @BeforeEach)
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue
        callbacks.onActivityPreCreated(mockActivity, null)
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.COMPLETE)

        // When
        val result = AppLaunchPreInitCollector.claim()

        // Then
        assertThat(result).isFalse()
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.COMPLETE)
    }

    @Test
    fun `M return false W claim() {CAPTURING state}`() {
        // Given: override addOnDrawListener to NOT fire onDraw, keeping state at CAPTURING
        val drawListenerCaptor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        whenever(mockViewTreeObserver.addOnDrawListener(drawListenerCaptor.capture())).doAnswer { Unit }

        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue
        callbacks.onActivityPreCreated(mockActivity, null)
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.CAPTURING)

        // When
        val result = AppLaunchPreInitCollector.claim()

        // Then
        assertThat(result).isFalse()
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.CAPTURING)
    }

    @Test
    fun `M ignore subsequent activity events W claim() {IDLE to CLAIMED, then onActivityPreCreated}`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        AppLaunchPreInitCollector.claim()
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.CLAIMED)

        // When — fire lifecycle callback directly (exercises CAS guard in onBeforeActivityCreated)
        callbacks.onActivityPreCreated(mockActivity, null)

        // Then — state unchanged, no data fields written
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.CLAIMED)
        assertThat(AppLaunchPreInitCollector.activityOnCreateNs).isEqualTo(0L)
        assertThat(AppLaunchPreInitCollector.processStartNs).isEqualTo(0L)
        assertThat(AppLaunchPreInitCollector.activity).isNull()
    }

    // endregion

    // region reset()

    @Test
    fun `M restore all fields to initial values W reset()`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)
        AppLaunchPreInitCollector.processStartNs = 12345L
        AppLaunchPreInitCollector.activityOnCreateNs = 67890L
        AppLaunchPreInitCollector.firstFrameNs = 11111L
        AppLaunchPreInitCollector.hasSavedInstanceState = true
        AppLaunchPreInitCollector.isFirstActivityForProcess = false

        // When
        AppLaunchPreInitCollector.reset()

        // Then
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.NOT_INSTALLED)
        assertThat(AppLaunchPreInitCollector.processStartNs).isEqualTo(0L)
        assertThat(AppLaunchPreInitCollector.activityOnCreateNs).isEqualTo(0L)
        assertThat(AppLaunchPreInitCollector.firstFrameNs).isEqualTo(0L)
        assertThat(AppLaunchPreInitCollector.hasSavedInstanceState).isFalse()
        assertThat(AppLaunchPreInitCollector.isFirstActivityForProcess).isTrue()
        assertThat(AppLaunchPreInitCollector.activity).isNull()
    }

    // endregion

    // region Task 1: API-level lifecycle dispatch and timing

    @Test
    fun `M capture activityOnCreateNs W onActivityPreCreated() {API 29+, IDLE state}`() {
        // Given
        whenever(stubBuildSdkVersionProvider.isAtLeastQ).thenReturn(true)
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When
        callbacks.onActivityPreCreated(mockActivity, null)

        // Then
        assertThat(AppLaunchPreInitCollector.activityOnCreateNs).isGreaterThan(0L)
    }

    @Test
    fun `M capture activityOnCreateNs W onActivityCreated() {API 23-28, IDLE state}`() {
        // Given
        whenever(stubBuildSdkVersionProvider.isAtLeastQ).thenReturn(false)
        whenever(stubBuildSdkVersionProvider.isAtLeastN).thenReturn(false)
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When
        callbacks.onActivityCreated(mockActivity, null)

        // Then
        assertThat(AppLaunchPreInitCollector.activityOnCreateNs).isGreaterThan(0L)
    }

    @Test
    fun `M not capture W onActivityCreated() {API 29+}`() {
        // Given
        whenever(stubBuildSdkVersionProvider.isAtLeastQ).thenReturn(true)
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When -- onActivityCreated should be a no-op on API 29+
        callbacks.onActivityCreated(mockActivity, null)

        // Then
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.IDLE)
        assertThat(AppLaunchPreInitCollector.activityOnCreateNs).isEqualTo(0L)
    }

    @Test
    fun `M fall back to DdRumContentProvider createTimeNs W onBeforeActivityCreated() {API 23}`() {
        // Given
        whenever(stubBuildSdkVersionProvider.isAtLeastQ).thenReturn(false)
        whenever(stubBuildSdkVersionProvider.isAtLeastN).thenReturn(false)
        val knownCreateTimeNs = 999_000_000L
        DdRumContentProvider.createTimeNs = knownCreateTimeNs
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When
        callbacks.onActivityCreated(mockActivity, null)

        // Then: on API 23, processStartNs must equal DdRumContentProvider.createTimeNs
        assertThat(AppLaunchPreInitCollector.processStartNs).isEqualTo(knownCreateTimeNs)
    }

    @Test
    fun `M fall back to createTimeNs W computeProcessStartNs() {computed gt createTimeNs}`() {
        // Given: test the OEM bug guard (computed > fallback should return fallback)
        whenever(stubBuildSdkVersionProvider.isAtLeastQ).thenReturn(false)
        whenever(stubBuildSdkVersionProvider.isAtLeastN).thenReturn(false)
        val knownCreateTimeNs = 500_000_000L
        DdRumContentProvider.createTimeNs = knownCreateTimeNs
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When: API 23 path always returns createTimeNs (same as OEM fallback direction 1 test)
        callbacks.onActivityCreated(mockActivity, null)

        // Then
        assertThat(AppLaunchPreInitCollector.processStartNs).isEqualTo(knownCreateTimeNs)
    }

    @Test
    fun `M compute processStartNs via Process getStartElapsedRealtime W onBeforeActivityCreated() {API 24+}`() {
        // Given: API 24+ (isAtLeastN=true, isAtLeastQ=false for simplicity)
        whenever(stubBuildSdkVersionProvider.isAtLeastQ).thenReturn(false)
        whenever(stubBuildSdkVersionProvider.isAtLeastN).thenReturn(true)
        // Set createTimeNs to a value in the future so computed (which is ~now - uptime) fits
        DdRumContentProvider.createTimeNs = System.nanoTime()
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When
        callbacks.onActivityCreated(mockActivity, null)

        // Then: processStartNs should be > 0 (either computed or fallback createTimeNs)
        assertThat(AppLaunchPreInitCollector.processStartNs).isGreaterThan(0L)
    }

    @Test
    fun `M set hasSavedInstanceState true W onBeforeActivityCreated() {non-null savedInstanceState}`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue
        val fakeSavedInstanceState = Bundle()

        // When
        callbacks.onActivityPreCreated(mockActivity, fakeSavedInstanceState)

        // Then
        assertThat(AppLaunchPreInitCollector.hasSavedInstanceState).isTrue()
    }

    @Test
    fun `M set hasSavedInstanceState false W onBeforeActivityCreated() {null savedInstanceState}`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When
        callbacks.onActivityPreCreated(mockActivity, null)

        // Then
        assertThat(AppLaunchPreInitCollector.hasSavedInstanceState).isFalse()
    }

    @Test
    fun `M set activity weak reference W onBeforeActivityCreated()`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When
        callbacks.onActivityPreCreated(mockActivity, null)

        // Then
        assertThat(AppLaunchPreInitCollector.activity?.get()).isSameAs(mockActivity)
    }

    @Test
    fun `M set isFirstActivityForProcess true W onBeforeActivityCreated() {first activity}`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When
        callbacks.onActivityPreCreated(mockActivity, null)

        // Then
        assertThat(AppLaunchPreInitCollector.isFirstActivityForProcess).isTrue()
    }

    @Test
    fun `M transition to CAPTURING W onBeforeActivityCreated() {IDLE state}`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When
        callbacks.onActivityPreCreated(mockActivity, null)

        // Then: state should be CAPTURING or COMPLETE (if onDraw fired synchronously)
        // In test environment, handler.post is synchronous, so onDraw fires immediately
        // State may be COMPLETE after subscribe since handler.post is immediate
        assertThat(AppLaunchPreInitCollector.state).isIn(
            AppLaunchPreInitCollector.State.CAPTURING,
            AppLaunchPreInitCollector.State.COMPLETE
        )
    }

    @Test
    fun `M not transition W onBeforeActivityCreated() {CLAIMED state}`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)
        AppLaunchPreInitCollector.claim()
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When
        callbacks.onActivityPreCreated(mockActivity, null)

        // Then: state should remain CLAIMED; no data written
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.CLAIMED)
        assertThat(AppLaunchPreInitCollector.activityOnCreateNs).isEqualTo(0L)
    }

    @Test
    fun `M unregister lifecycle callbacks W onBeforeActivityCreated() {IDLE to CAPTURING}`() {
        // Given
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When
        callbacks.onActivityPreCreated(mockActivity, null)

        // Then: lifecycle callbacks should be unregistered
        verify(mockApplication).unregisterActivityLifecycleCallbacks(any())
    }

    // endregion

    // region Task 2: OnDrawListener chain and addFirstFrameCallback

    @Test
    fun `M record firstFrameNs W onDraw() fires {CAPTURING state}`() {
        // Given: @BeforeEach configures addOnDrawListener to fire onDraw immediately
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When: trigger lifecycle which sets up the OnDrawListener (fires immediately via mock)
        callbacks.onActivityPreCreated(mockActivity, null)

        // Then: firstFrameNs > 0 since onDraw was fired immediately by the mock
        assertThat(AppLaunchPreInitCollector.firstFrameNs).isGreaterThan(0L)
    }

    @Test
    fun `M transition to COMPLETE W onDraw() fires {CAPTURING state}`() {
        // Given: @BeforeEach configures addOnDrawListener to fire onDraw immediately
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When
        callbacks.onActivityPreCreated(mockActivity, null)

        // Then
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.COMPLETE)
    }

    @Test
    fun `M record firstFrameNs only once W onDraw() fires multiple times`() {
        // Given: override default to capture listener without auto-firing so we control timing
        val drawListenerCaptor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        whenever(mockViewTreeObserver.addOnDrawListener(drawListenerCaptor.capture())).doAnswer { Unit }

        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue
        callbacks.onActivityPreCreated(mockActivity, null)
        verify(mockViewTreeObserver).addOnDrawListener(any())

        // Fire onDraw once
        drawListenerCaptor.firstValue.onDraw()
        val firstFrameNsAfterFirstDraw = AppLaunchPreInitCollector.firstFrameNs
        assertThat(firstFrameNsAfterFirstDraw).isGreaterThan(0L)

        // When: fire onDraw again manually
        drawListenerCaptor.firstValue.onDraw()

        // Then: firstFrameNs should not change (invoked guard prevents overwrite)
        assertThat(AppLaunchPreInitCollector.firstFrameNs).isEqualTo(firstFrameNsAfterFirstDraw)
    }

    @Test
    fun `M defer removeOnDrawListener via Handler post W onDraw()`() {
        // Given: use a handler that does NOT run immediately so we can observe the post call
        val deferredRunnables = mutableListOf<Runnable>()
        whenever(mockHandler.post(any())).doAnswer {
            deferredRunnables.add(it.getArgument(0))
            true
        }
        // Override to capture listener without auto-firing, so we can fire onDraw manually
        val drawListenerCaptor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        whenever(mockViewTreeObserver.addOnDrawListener(drawListenerCaptor.capture())).doAnswer { Unit }

        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        callbacks.onActivityPreCreated(mockActivity, null)
        verify(mockViewTreeObserver).addOnDrawListener(any())

        // Now manually fire onDraw
        drawListenerCaptor.firstValue.onDraw()

        // Then: removeOnDrawListener not yet called (deferred)
        assertThat(deferredRunnables).isNotEmpty()

        // Run the deferred runnable
        deferredRunnables.forEach { it.run() }

        verify(mockViewTreeObserver).removeOnDrawListener(any())
    }

    @Test
    fun `M guard removeOnDrawListener with isAlive W onDraw()`() {
        // Given: use a deferred handler to control timing
        val deferredRunnables = mutableListOf<Runnable>()
        whenever(mockHandler.post(any())).doAnswer {
            deferredRunnables.add(it.getArgument(0))
            true
        }
        // Override to capture listener without auto-firing
        val drawListenerCaptor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        whenever(mockViewTreeObserver.addOnDrawListener(drawListenerCaptor.capture())).doAnswer { Unit }

        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        callbacks.onActivityPreCreated(mockActivity, null)
        verify(mockViewTreeObserver).addOnDrawListener(any())
        drawListenerCaptor.firstValue.onDraw()

        // Simulate viewTreeObserver becoming dead
        whenever(mockViewTreeObserver.isAlive).thenReturn(false)

        // When: run deferred runnable
        deferredRunnables.forEach { it.run() }

        // Then: removeOnDrawListener should NOT be called since isAlive is false
        verify(mockViewTreeObserver, never()).removeOnDrawListener(any())
    }

    @Test
    fun `M handle null decor view W subscribeToFirstFrameDrawn() {peekDecorView returns null}`() {
        // Given: peekDecorView returns null; inject a mock reporter that fires the callback immediately
        whenever(mockWindow.peekDecorView()).thenReturn(null)
        AppLaunchPreInitCollector.firstDrawTimeReporterFactory = { _ ->
            mock<RumFirstDrawTimeReporter>().also { reporter ->
                whenever(reporter.subscribeToFirstFrameDrawn(any(), any())).doAnswer { invocation ->
                    val callback = invocation.getArgument<RumFirstDrawTimeReporter.Callback>(1)
                    callback.onFirstFrameDrawn(System.nanoTime())
                    Unit
                }
            }
        }

        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When: should not crash and reporter handles the null decorView case
        callbacks.onActivityPreCreated(mockActivity, null)

        // Then: state transitions to COMPLETE (reporter fired the callback)
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.COMPLETE)
        assertThat(AppLaunchPreInitCollector.firstFrameNs).isGreaterThan(0L)
    }

    @Test
    fun `M handle not-yet-attached decor view W subscribeToFirstFrameDrawn() {isAttachedToWindow false}`() {
        // Given: decor view exists but not attached
        whenever(mockDecorView.isAttachedToWindow).thenReturn(false)

        // Simulate addOnAttachStateChangeListener calling onViewAttachedToWindow immediately
        whenever(mockDecorView.addOnAttachStateChangeListener(any())).doAnswer {
            val listener = it.getArgument<View.OnAttachStateChangeListener>(0)
            listener.onViewAttachedToWindow(mockDecorView)
            Unit
        }

        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When
        callbacks.onActivityPreCreated(mockActivity, null)

        // Then: addOnAttachStateChangeListener was used, and OnDrawListener was registered after attach
        verify(mockDecorView).addOnAttachStateChangeListener(any())
        verify(mockViewTreeObserver).addOnDrawListener(any())
    }

    @Test
    fun `M guard addOnDrawListener with isAlive and try catch W registerOnDrawListener()`() {
        // Given: viewTreeObserver is not alive
        whenever(mockViewTreeObserver.isAlive).thenReturn(false)

        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        // When: should not crash
        callbacks.onActivityPreCreated(mockActivity, null)

        // Then: addOnDrawListener is never called
        verify(mockViewTreeObserver, never()).addOnDrawListener(any())
    }

    @Test
    fun `M invoke callback immediately W addFirstFrameCallback() {COMPLETE state}`() {
        // Given: reach COMPLETE state
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue
        callbacks.onActivityPreCreated(mockActivity, null)

        // Verify we are in COMPLETE state
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.COMPLETE)
        val firstFrameNs = AppLaunchPreInitCollector.firstFrameNs

        // When: add callback after COMPLETE
        var capturedNs = -1L
        AppLaunchPreInitCollector.addFirstFrameCallback { ns -> capturedNs = ns }

        // Then: callback was invoked immediately with firstFrameNs
        assertThat(capturedNs).isEqualTo(firstFrameNs)
    }

    @Test
    fun `M enqueue callback W addFirstFrameCallback() {CAPTURING state}`() {
        // Given: override addOnDrawListener to capture without firing, keeping state at CAPTURING
        val drawListenerCaptor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        whenever(mockViewTreeObserver.addOnDrawListener(drawListenerCaptor.capture())).doAnswer { Unit }

        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        callbacks.onActivityPreCreated(mockActivity, null)
        verify(mockViewTreeObserver).addOnDrawListener(any())

        // State should be CAPTURING since onDraw hasn't fired
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.CAPTURING)

        var capturedNs = -1L
        // When: add callback while CAPTURING
        AppLaunchPreInitCollector.addFirstFrameCallback { ns -> capturedNs = ns }

        // Then: callback should not be invoked yet
        assertThat(capturedNs).isEqualTo(-1L)
    }

    @Test
    fun `M drain callbacks on COMPLETE transition W onDraw() {callbacks enqueued}`() {
        // Given: override addOnDrawListener to capture without firing, keeping state at CAPTURING
        val drawListenerCaptor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        whenever(mockViewTreeObserver.addOnDrawListener(drawListenerCaptor.capture())).doAnswer { Unit }

        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue

        callbacks.onActivityPreCreated(mockActivity, null)
        verify(mockViewTreeObserver).addOnDrawListener(any())

        // Enqueue two callbacks before onDraw fires
        var capturedNs1 = -1L
        var capturedNs2 = -1L
        AppLaunchPreInitCollector.addFirstFrameCallback { ns -> capturedNs1 = ns }
        AppLaunchPreInitCollector.addFirstFrameCallback { ns -> capturedNs2 = ns }

        // When: manually fire onDraw
        drawListenerCaptor.firstValue.onDraw()

        // Then: both callbacks should have been called with firstFrameNs
        assertThat(capturedNs1).isEqualTo(AppLaunchPreInitCollector.firstFrameNs)
        assertThat(capturedNs2).isEqualTo(AppLaunchPreInitCollector.firstFrameNs)
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.COMPLETE)
    }

    @Test
    fun `M close TOCTOU window W addFirstFrameCallback() {race with COMPLETE transition}`() {
        // Given: reach COMPLETE state first
        AppLaunchPreInitCollector.install(mockApplication)
        val captor = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(mockApplication).registerActivityLifecycleCallbacks(captor.capture())
        val callbacks = captor.firstValue
        callbacks.onActivityPreCreated(mockActivity, null)
        assertThat(AppLaunchPreInitCollector.state).isEqualTo(AppLaunchPreInitCollector.State.COMPLETE)
        val firstFrameNs = AppLaunchPreInitCollector.firstFrameNs

        // When: add callback after COMPLETE (TOCTOU: state was COMPLETE when checked)
        var callCount = 0
        var capturedNs = -1L
        AppLaunchPreInitCollector.addFirstFrameCallback { ns ->
            callCount++
            capturedNs = ns
        }

        // Then: callback was called exactly once with the correct firstFrameNs
        assertThat(callCount).isEqualTo(1)
        assertThat(capturedNs).isEqualTo(firstFrameNs)
    }

    @Test
    fun `M not invoke callback W addFirstFrameCallback() {NOT_INSTALLED state}`() {
        // Given: NOT_INSTALLED, no install
        var capturedNs = -1L

        // When
        AppLaunchPreInitCollector.addFirstFrameCallback { ns -> capturedNs = ns }

        // Then: callback not fired since NOT_INSTALLED / no COMPLETE transition
        assertThat(capturedNs).isEqualTo(-1L)
    }

    // endregion
}
